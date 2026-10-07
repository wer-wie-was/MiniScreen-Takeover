/*
 *  CFI parallel flash with Jedec (42.4) command set emulation
 *
 *  Ported from QEMU 2.5.0-pebble8 to QEMU 10.x APIs.
 *
 *  Key feature: per-bank command states (cmd[bank], wcycle[bank]).
 *  The Macronix MX29VS128FB has 8 banks of 2MB each. With per-bank states,
 *  firmware can write to one bank while reading from another. This is
 *  critical for Pebble firmware which does flash writes (BlobDB, app install)
 *  while simultaneously reading from other banks.
 *
 *  Copyright (c) 2006 Thorsten Zitterell
 *  Copyright (c) 2005 Jocelyn Mayer
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, see <http://www.gnu.org/licenses/>.
 */

#include "qemu/osdep.h"
#include "hw/block/block.h"
#include "hw/block/pflash_jedec_424.h"
#include "hw/qdev-properties.h"
#include "hw/qdev-properties-system.h"
#include "qapi/error.h"
#include "qemu/error-report.h"
#include "qemu/bitops.h"
#include "system/block-backend.h"
#include "qemu/host-utils.h"
#include "qemu/module.h"
#include "hw/sysbus.h"
#include "migration/vmstate.h"
#include "hw/arm/pebble_compat.h"

#define PFLASH_BUG(fmt, ...) \
do { \
    fprintf(stderr, "PFLASH: Possible BUG - " fmt, ## __VA_ARGS__); \
    exit(1); \
} while(0)

/* #define PFLASH_DEBUG */
#ifdef PFLASH_DEBUG
#define DPRINTF(fmt, ...)                                   \
do {                                                        \
    printf("PFLASH: " fmt , ## __VA_ARGS__);                \
} while (0)
#else
#define DPRINTF(fmt, ...) do { } while (0)
#endif

#define PFLASH_MAX_BANKS 8

struct PFlashJEDEC424 {
    /*< private >*/
    SysBusDevice parent_obj;
    /*< public >*/

    BlockBackend *blk;
    uint32_t nb_blocs;
    uint64_t sector_len;
    uint8_t bank_width;
    uint8_t device_width;
    uint8_t max_device_width;
    uint32_t bank_size;
    uint8_t be;
    int ro;
    uint8_t wcycle[PFLASH_MAX_BANKS];  /* per-bank write cycle state */
    uint8_t cmd[PFLASH_MAX_BANKS];     /* per-bank command state */
    uint8_t global_cmd;  /* some operations don't have separate bank states */
    uint8_t status;
    uint16_t ident0;
    uint16_t ident1;
    uint16_t ident2;
    uint16_t ident3;
    uint8_t cfi_len;
    uint8_t cfi_table[0x60];
    uint64_t counter;
    unsigned int writeblock_size;
    MemoryRegion mem;
    char *name;
    void *storage;
    uint16_t configuration_register;
    bool rom_mode;          /* true = ROMD enabled (direct memory reads) */
    uint32_t read_counter;  /* reads since last ROMD disable, for lazy re-enable */
};

static const VMStateDescription vmstate_pflash_jedec424 = {
    .name = "pflash_jedec_424",
    .version_id = 2,
    .minimum_version_id = 2,
    .fields = (VMStateField[]) {
        VMSTATE_UINT8_ARRAY(wcycle, PFlashJEDEC424, PFLASH_MAX_BANKS),
        VMSTATE_UINT8_ARRAY(cmd, PFlashJEDEC424, PFLASH_MAX_BANKS),
        VMSTATE_UINT8(global_cmd, PFlashJEDEC424),
        VMSTATE_UINT8(status, PFlashJEDEC424),
        VMSTATE_UINT64(counter, PFlashJEDEC424),
        VMSTATE_END_OF_LIST()
    }
};

/*
 * Threshold for lazy ROMD re-enable: after this many reads through the
 * slow MMIO path with all banks idle, re-enable ROMD for direct memory
 * reads.  Matches pflash_cfi02's PFLASH_LAZY_ROMD_THRESHOLD.
 */
#define PFLASH_LAZY_ROMD_THRESHOLD 42

/* Enable ROMD (direct memory reads bypass MMIO handler) */
static void pflash_enter_romd(PFlashJEDEC424 *pfl)
{
    if (!pfl->rom_mode) {
        pfl->rom_mode = true;
        memory_region_rom_device_set_romd(&pfl->mem, true);
    }
    pfl->read_counter = 0;
}

/* Disable ROMD (reads go through pflash_read MMIO handler) */
static void pflash_exit_romd(PFlashJEDEC424 *pfl)
{
    if (pfl->rom_mode) {
        pfl->rom_mode = false;
        memory_region_rom_device_set_romd(&pfl->mem, false);
    }
    pfl->read_counter = 0;
}

/* Check if all banks are idle (no write sequences in progress) */
static bool pflash_all_banks_idle(PFlashJEDEC424 *pfl)
{
    for (int i = 0; i < PFLASH_MAX_BANKS; i++) {
        if (pfl->wcycle[i] != 0) return false;
    }
    return true;
}

static void pflash_reset_state(PFlashJEDEC424 *pfl)
{
    memset(pfl->wcycle, 0, sizeof(pfl->wcycle));
    memset(pfl->cmd, 0, sizeof(pfl->cmd));
    pfl->global_cmd = 0;
}

/* Perform a CFI query based on the bank width of the flash. */
static uint32_t pflash_cfi_query(PFlashJEDEC424 *pfl, hwaddr offset)
{
    int i;
    uint32_t resp = 0;
    hwaddr boff;

    boff = offset >> (ctz32(pfl->bank_width) +
                      ctz32(pfl->max_device_width) - ctz32(pfl->device_width));

    if (boff > pfl->cfi_len) {
        return 0;
    }
    resp = pfl->cfi_table[boff];
    if (pfl->device_width != pfl->max_device_width) {
        if (pfl->device_width != 1 || pfl->bank_width > 4) {
            DPRINTF("%s: Unsupported device configuration: "
                    "device_width=%d, max_device_width=%d\n",
                    __func__, pfl->device_width,
                    pfl->max_device_width);
            return 0;
        }
        for (i = 1; i < pfl->max_device_width; i++) {
            resp = deposit32(resp, 8 * i, 8, pfl->cfi_table[boff]);
        }
    }
    if (pfl->device_width < pfl->bank_width) {
        for (i = pfl->device_width;
             i < pfl->bank_width; i += pfl->device_width) {
            resp = deposit32(resp, 8 * i, 8 * pfl->device_width, resp);
        }
    }

    return resp;
}

/* Perform a device id query based on the bank width of the flash. */
static uint32_t pflash_devid_query(PFlashJEDEC424 *pfl, hwaddr offset)
{
    int i;
    uint32_t resp;
    hwaddr boff;

    boff = offset >> (ctz32(pfl->bank_width) +
                      ctz32(pfl->max_device_width) - ctz32(pfl->device_width));

    switch (boff & 0xFF) {
    case 0:
        resp = pfl->ident0;
        break;
    case 1:
        resp = pfl->ident1;
        break;
    default:
        return 0;
    }
    if (pfl->device_width < pfl->bank_width) {
        for (i = pfl->device_width;
              i < pfl->bank_width; i += pfl->device_width) {
            resp = deposit32(resp, 8 * i, 8 * pfl->device_width, resp);
        }
    }

    return resp;
}

static uint32_t pflash_read(PFlashJEDEC424 *pfl, hwaddr offset,
                             int width, int be)
{
    hwaddr boff;
    uint32_t ret;
    uint8_t *p;

    ret = -1;

    uint8_t bank = offset / pfl->bank_size;

    /* Lazy ROMD re-enable: if we're reading through the MMIO handler but all
     * banks are idle, re-enable ROMD after enough reads so future reads go
     * directly to memory (massive perf win for the common case). */
    if (!pfl->rom_mode && pfl->wcycle[bank] == 0 &&
        ++pfl->read_counter > PFLASH_LAZY_ROMD_THRESHOLD) {
        if (pflash_all_banks_idle(pfl)) {
            pflash_enter_romd(pfl);
        }
    }

    /* Per-bank command dispatch - this is the key difference from pflash_cfi02 */
    switch (pfl->cmd[bank]) {
    default:
        /* Unknown state: reset and treat as read */
        fprintf(stderr, "%s: unknown command state: %x\n",
                __func__, pfl->cmd[bank]);
        pfl->wcycle[bank] = 0;
        pfl->cmd[bank] = 0;
        pfl->global_cmd = 0;
        /* fall through to read code */
    case 0x00:
        /* Flash area read */
        p = pfl->storage;
        switch (width) {
        case 1:
            ret = p[offset];
            break;
        case 2:
            if (be) {
                ret = p[offset] << 8;
                ret |= p[offset + 1];
            } else {
                ret = p[offset];
                ret |= p[offset + 1] << 8;
            }
            break;
        case 4:
            if (be) {
                ret = p[offset] << 24;
                ret |= p[offset + 1] << 16;
                ret |= p[offset + 2] << 8;
                ret |= p[offset + 3];
            } else {
                ret = p[offset];
                ret |= p[offset + 1] << 8;
                ret |= p[offset + 2] << 16;
                ret |= p[offset + 3] << 24;
            }
            break;
        default:
            DPRINTF("BUG in %s\n", __func__);
        }
        break;
    case 0x70: /* Status Register */
        ret = pfl->status;
        if (pfl->device_width && width > pfl->device_width) {
            int shift = pfl->device_width * 8;
            while (shift + pfl->device_width * 8 <= width * 8) {
                ret |= pfl->status << shift;
                shift += pfl->device_width * 8;
            }
        } else if (!pfl->device_width && width > 2) {
            ret |= pfl->status << 16;
        }
        pfl->cmd[bank] = 0;
        break;
    case 0x90:
        if (!pfl->device_width) {
            boff = offset & 0xFF;
            if (pfl->bank_width == 2) {
                boff = boff >> 1;
            } else if (pfl->bank_width == 4) {
                boff = boff >> 2;
            }

            switch (boff) {
            case 0:
                ret = pfl->ident0 << 8 | pfl->ident1;
                break;
            case 1:
                ret = pfl->ident2 << 8 | pfl->ident3;
                break;
            default:
                ret = 0;
                break;
            }
        } else {
            int i;
            for (i = 0; i < width; i += pfl->bank_width) {
                ret = deposit32(ret, i * 8, pfl->bank_width * 8,
                                pflash_devid_query(pfl,
                                                 offset + i * pfl->bank_width));
            }
        }
        break;
    case 0x98: /* Query mode */
        if (!pfl->device_width) {
            boff = offset & 0xFF;
            if (pfl->bank_width == 2) {
                boff = boff >> 1;
            } else if (pfl->bank_width == 4) {
                boff = boff >> 2;
            }

            if (boff > pfl->cfi_len) {
                ret = 0;
            } else {
                ret = pfl->cfi_table[boff];
            }
        } else {
            int i;
            for (i = 0; i < width; i += pfl->bank_width) {
                ret = deposit32(ret, i * 8, pfl->bank_width * 8,
                                pflash_cfi_query(pfl,
                                                 offset + i * pfl->bank_width));
            }
        }
        break;
    case 0xd0:  /* Configuration register */
        ret = pfl->configuration_register;
        break;
    }

    return ret;
}

/* update flash content on disk */
static void pflash_update(PFlashJEDEC424 *pfl, int offset, int size)
{
    int offset_end;
    if (pfl->blk) {
        offset_end = offset + size;
        /* widen to sector boundaries */
        offset = QEMU_ALIGN_DOWN(offset, BDRV_SECTOR_SIZE);
        offset_end = QEMU_ALIGN_UP(offset_end, BDRV_SECTOR_SIZE);
        int ret = blk_pwrite(pfl->blk, offset, offset_end - offset,
                             pfl->storage + offset, 0);
        if (ret < 0) {
            error_report("Could not update PFLASH: %s", strerror(-ret));
        }
    }
}

static inline void pflash_data_write(PFlashJEDEC424 *pfl, hwaddr offset,
                                     uint32_t value, int width, int be)
{
    uint8_t *p = pfl->storage;

    DPRINTF("%s: block write offset " TARGET_FMT_plx
            " value %x width %d counter %016" PRIx64 "\n",
            __func__, offset, value, width, pfl->counter);

    /* Flash can only flip 1's to 0's, so use &= to update contents.
     * This is critical because some drivers write 0xFF to preserve
     * the original value. */
    switch (width) {
    case 1:
        p[offset] &= value;
        break;
    case 2:
        if (be) {
            p[offset] &= value >> 8;
            p[offset + 1] &= value;
        } else {
            p[offset] &= value;
            p[offset + 1] &= value >> 8;
        }
        break;
    case 4:
        if (be) {
            p[offset] &= value >> 24;
            p[offset + 1] &= value >> 16;
            p[offset + 2] &= value >> 8;
            p[offset + 3] &= value;
        } else {
            p[offset] &= value;
            p[offset + 1] &= value >> 8;
            p[offset + 2] &= value >> 16;
            p[offset + 3] &= value >> 24;
        }
        break;
    }
}

static void pflash_write(PFlashJEDEC424 *pfl, hwaddr offset,
                         uint32_t value, int width, int be)
{
    uint8_t cmd;
    int i;
    uint8_t *p;

    cmd = value;

    uint8_t bank = pfl->global_cmd ? 0 : offset / pfl->bank_size;
    uint32_t sector_offset = offset & (pfl->sector_len - 1);
    sector_offset = sector_offset >> (pfl->bank_width - 1);

    DPRINTF("%s: writing offset 0x%llx sector offset 0x%x value 0x%x width %d "
            "pfl->wcycle %d pfl->cmd 0x%x\n", __func__, (unsigned long long)offset,
            sector_offset, value, width,
            pfl->wcycle[bank], pfl->cmd[bank]);

    if (!pfl->wcycle[bank]) {
        /* Set the device in I/O access mode (disable ROMD so reads go
         * through the MMIO handler for command/status responses) */
        pflash_exit_romd(pfl);
    }

    switch (pfl->wcycle[bank]) {
    case 0:
        /* read mode */
        switch (cmd) {
        case 0x00:
            goto reset_bank;
        case 0x25:
            DPRINTF("%s: Program to buffer\n", __func__);
            pfl->status |= 0x80; /* Ready! */
            break;
        case 0x33:
            DPRINTF("%s: Blank check\n", __func__);
            pfl->status |= 0x80; /* Ready! */
            pfl->status &= ~0x20;  /* clear non-erased bit */
            p = pfl->storage;
            {
                hwaddr erase_offset = offset & ~(pfl->sector_len - 1);
                p += erase_offset;
                for (i = 0; i < (int)pfl->sector_len; i++) {
                    if (p[i] != 0xFF) {
                        pfl->status |= 0x20;    /* Not erased */
                        break;
                    }
                }
            }
            goto reset_bank;
        case 0x60: /* Sector Lock/Unlock */
            pfl->global_cmd = 0x60;
            bank = 0;
            DPRINTF("%s: Sector Lock/Unlock\n", __func__);
            break;
        case 0x70: /* Status Register */
            DPRINTF("%s: Read status register\n", __func__);
            pfl->cmd[bank] = cmd;
            return;
        case 0x71: /* Clear status bits */
            DPRINTF("%s: Clear status bits\n", __func__);
            pfl->status = 0x80;   /* set device ready bit */
            goto reset_bank;
        case 0x80: /* Erase setup */
            DPRINTF("%s: Erase setup\n", __func__);
            break;
        case 0x90: /* Read Device ID */
            DPRINTF("%s: Read Device information\n", __func__);
            pfl->cmd[bank] = cmd;
            return;
        case 0x98: /* CFI query */
            DPRINTF("%s: CFI query\n", __func__);
            break;
        case 0xd0: /* Enter configuration register */
            DPRINTF("%s: Configuration register enter\n", __func__);
            break;
        case 0xf0: /* Reset */
            DPRINTF("%s: Reset\n", __func__);
            pflash_reset_state(pfl);
            goto reset_bank;
        case 0xff: /* Read array mode */
            DPRINTF("%s: Read array mode\n", __func__);
            goto reset_bank;
        default:
            goto error_flash;
        }
        pfl->wcycle[bank]++;
        pfl->cmd[bank] = cmd;
        break;

    case 1:
        switch (pfl->cmd[bank]) {
        case 0x25:
            pfl->counter = value + 1;
            DPRINTF("%s: Program to buffer of %lld words\n", __func__,
                    (long long)pfl->counter);
            pfl->wcycle[bank]++;
            break;
        case 0x60: /* Sector Lock... Expecting 0x288 = 60 */
            if (cmd == 0x60) {
                pfl->cmd[bank] = 0x60;
                pfl->wcycle[bank]++;
            } else {
                goto reset_bank;
            }
            break;
        case 0x80:
            if (sector_offset == 0x2aa && cmd == 0x30) {
                /* sector erase */
                offset &= ~(pfl->sector_len - 1);
                DPRINTF("%s: sector erase at " TARGET_FMT_plx " bytes %x\n",
                    __func__, offset, (unsigned)pfl->sector_len);

                if (!pfl->ro) {
                    p = pfl->storage;
                    memset(p + offset, 0xff, pfl->sector_len);
                    pflash_update(pfl, offset, pfl->sector_len);
                    pfl->status &= ~0x20;  /* clear non-erased bit */
                } else {
                    pfl->status |= 0x20; /* Block erase error */
                }
                pfl->status |= 0x80; /* Ready! */
            } else if (sector_offset == 0x2aa && cmd == 0x10) {
                DPRINTF("%s: chip erase\n", __func__);
                if (!pfl->ro) {
                    memset(pfl->storage, 0xff,
                           pfl->sector_len * pfl->nb_blocs);
                    pflash_update(pfl, 0, pfl->sector_len * pfl->nb_blocs);
                    pfl->status &= ~0x20;  /* clear non-erased bit */
                } else {
                    pfl->status |= 0x20; /* Block erase error */
                }
                pfl->status |= 0x80; /* Ready! */
            } else {
                DPRINTF("%s: Unexpected command byte: 0x%x\n", __func__, cmd);
            }
            goto reset_bank;
            break;
        case 0x98:
            if (cmd == 0xf0) {
                DPRINTF("%s: leaving query mode\n", __func__);
                goto reset_bank;
            } else {
                DPRINTF("%s: Unexpected command byte: 0x%x\n", __func__, cmd);
                goto reset_bank;
            }
        case 0xd0:
            if (cmd == 0xf0) {
                DPRINTF("%s: leaving configuration register mode\n", __func__);
                pfl->status |= 0x80;
                goto reset_bank;
            } else if (cmd == 0x25) {
                DPRINTF("%s: 2nd byte of program to config register buffer\n",
                        __func__);
                pfl->wcycle[bank]++;
            } else if (cmd == 0x29) {
                DPRINTF("%s: program config register buffer to flash\n",
                        __func__);
                pfl->wcycle[bank] = 1;
            } else {
                DPRINTF("%s: Unexpected command byte: 0x%x\n", __func__, cmd);
                goto reset_bank;
            }
            break;
        default:
            goto error_flash;
        }
        break;

    case 2:
        switch (pfl->cmd[bank]) {
        case 0x25: /* Program to buffer */
            if (!pfl->ro) {
                DPRINTF("%s: Programming %d bytes at " TARGET_FMT_plx
                        " to 0x%x\n", __func__, width, offset, value);
                pflash_data_write(pfl, offset, value, width, be);
            } else {
                pfl->status |= 0x10; /* Programming error */
            }
            pfl->status |= 0x80;

            pfl->counter--;
            if (!pfl->counter) {
                hwaddr mask = pfl->writeblock_size - 1;
                mask = ~mask;

                DPRINTF("%s: Programming finished\n", __func__);
                pfl->wcycle[bank]++;
                if (!pfl->ro) {
                    /* Flush the entire write buffer onto backing storage. */
                    pflash_update(pfl, offset & mask, pfl->writeblock_size);
                } else {
                    pfl->status |= 0x10; /* Programming error */
                }
            }
            break;
        case 0xd0:
            if (sector_offset == 0x2aa && cmd == 0) {
                DPRINTF("%s: 3rd byte of program to config register buffer\n",
                        __func__);
                pfl->wcycle[bank]++;
            } else {
                DPRINTF("%s: Unexpected command byte: 0x%x\n", __func__, cmd);
                goto reset_bank;
            }
            break;
        case 0x60: /* Sector Lock */
            if (cmd == 0x60) {
                DPRINTF("%s: 3rd byte of Sector Lock\n", __func__);
                goto reset_bank;
            } else if (cmd == 0x61) {
                pfl->wcycle[bank]++;
                pfl->cmd[bank] = 0x61;
            } else {
              goto error_flash;
            }
            break;
        default:
            goto error_flash;
        }
        break;

    case 3: /* Confirm mode */
        switch (pfl->cmd[bank]) {
        case 0x25: /* Block write */
            if (cmd == 0x29 && sector_offset == 0x555) {
                pfl->status |= 0x80;
                goto reset_bank;
            } else {
                PFLASH_BUG("%s: unknown command for Programming\n", __func__);
                goto reset_bank;
            }
            break;
        case 0x61: /* Sector Lock */
            if (cmd == 0x61) {
                DPRINTF("%s: 4th byte of Sector Lock\n", __func__);
                goto reset_bank;
            }
            goto error_flash;
            break;
        case 0xd0:
            if (sector_offset == 0) {
                pfl->configuration_register = value;
                DPRINTF("%s: setting new config register: 0x%x\n",
                        __func__, value);
                pfl->wcycle[bank] = 1;
            } else {
                DPRINTF("%s: Unexpected command byte: 0x%x\n", __func__, cmd);
                goto reset_bank;
            }
            break;
        default:
            goto error_flash;
        }
        break;
    default:
        /* Should never happen */
        DPRINTF("%s: invalid write state\n", __func__);
        goto reset_bank;
    }
    return;

 error_flash:
    fprintf(stderr, "PFLASH %s: Unimplemented flash cmd sequence "
                  "(offset 0x%llx sector offset 0x%x, bank %u "
                  "pfl->wcycle %d pfl->cmd 0x%x value 0x%x)\n",
            __func__, (unsigned long long)offset, sector_offset, bank,
            pfl->wcycle[bank], pfl->cmd[bank], value);

 reset_bank:
    pfl->wcycle[bank] = 0;
    pfl->cmd[bank] = 0;
    pfl->global_cmd = 0;

    /* Re-enable ROMD if all banks are now idle.  This switches reads back
     * to fast direct-memory access instead of the slow MMIO handler. */
    if (pflash_all_banks_idle(pfl)) {
        pflash_enter_romd(pfl);
    }
}

/* MemoryRegionOps callbacks (QEMU 10.x style) */
static uint64_t pflash_jedec_read_op(void *opaque, hwaddr addr,
                                      unsigned int width)
{
    PFlashJEDEC424 *pfl = opaque;
    return pflash_read(pfl, addr, width, pfl->be);
}

static void pflash_jedec_write_op(void *opaque, hwaddr addr,
                                   uint64_t value, unsigned int width)
{
    PFlashJEDEC424 *pfl = opaque;
    pflash_write(pfl, addr, (uint32_t)value, width, pfl->be);
}

static const MemoryRegionOps pflash_jedec_ops = {
    .read = pflash_jedec_read_op,
    .write = pflash_jedec_write_op,
    .valid.min_access_size = 1,
    .valid.max_access_size = 4,
    .endianness = DEVICE_NATIVE_ENDIAN,
};

static void pflash_jedec_realize(DeviceState *dev, Error **errp)
{
    ERRP_GUARD();
    PFlashJEDEC424 *pfl = PFLASH_JEDEC_424(dev);
    uint64_t total_len;
    int ret;
    uint64_t blocks_per_device, device_len;
    int num_devices;

    total_len = pfl->sector_len * pfl->nb_blocs;

    num_devices = pfl->device_width ? (pfl->bank_width / pfl->device_width) : 1;
    blocks_per_device = pfl->nb_blocs / num_devices;
    device_len = pfl->sector_len * blocks_per_device;

    memory_region_init_rom_device(
        &pfl->mem, OBJECT(dev),
        &pflash_jedec_ops, pfl,
        pfl->name, total_len, errp);
    if (*errp) {
        return;
    }
    pfl->storage = memory_region_get_ram_ptr(&pfl->mem);
    sysbus_init_mmio(SYS_BUS_DEVICE(dev), &pfl->mem);

    if (pfl->blk) {
        uint64_t perm;
        pfl->ro = !blk_supports_write_perm(pfl->blk);
        perm = BLK_PERM_CONSISTENT_READ | (pfl->ro ? 0 : BLK_PERM_WRITE);
        ret = blk_set_perm(pfl->blk, perm, BLK_PERM_ALL, errp);
        if (ret < 0) {
            return;
        }
    } else {
        pfl->ro = 0;
    }

    if (pfl->blk) {
        if (!blk_check_size_and_read_all(pfl->blk, dev, pfl->storage,
                                         total_len, errp)) {
            return;
        }
    }

    /* Default to devices being used at their maximum device width. */
    if (!pfl->max_device_width) {
        pfl->max_device_width = pfl->device_width;
    }

    pfl->configuration_register = 0xdf48;

    pflash_reset_state(pfl);
    pfl->status = 0;
    /* Hardcoded CFI table */
    pfl->cfi_len = 0x52;
    /* Standard "QRY" string */
    pfl->cfi_table[0x10] = 'Q';
    pfl->cfi_table[0x11] = 'R';
    pfl->cfi_table[0x12] = 'Y';
    /* Command set (JEDEC 42.4) */
    pfl->cfi_table[0x13] = 0x02;
    pfl->cfi_table[0x14] = 0x00;
    /* Primary extended table address (none) */
    pfl->cfi_table[0x15] = 0x40;
    pfl->cfi_table[0x16] = 0x00;
    /* Alternate command set (none) */
    pfl->cfi_table[0x17] = 0x00;
    pfl->cfi_table[0x18] = 0x00;
    /* Alternate extended table (none) */
    pfl->cfi_table[0x19] = 0x00;
    pfl->cfi_table[0x1A] = 0x00;
    /* Vcc min */
    pfl->cfi_table[0x1B] = 0x17;
    /* Vcc max */
    pfl->cfi_table[0x1C] = 0x19;
    /* Vpp min (no Vpp pin) */
    pfl->cfi_table[0x1D] = 0x00;
    /* Vpp max (no Vpp pin) */
    pfl->cfi_table[0x1E] = 0x00;
    /* Reserved */
    pfl->cfi_table[0x1F] = 0x04;
    /* Timeout for min size buffer write */
    pfl->cfi_table[0x20] = 0x09;
    /* Typical timeout for block erase */
    pfl->cfi_table[0x21] = 0x0a;
    /* Typical timeout for full chip erase (4096 ms) */
    pfl->cfi_table[0x22] = 0x11;
    /* Reserved */
    pfl->cfi_table[0x23] = 0x04;
    /* Max timeout for buffer write */
    pfl->cfi_table[0x24] = 0x02;
    /* Max timeout for block erase */
    pfl->cfi_table[0x25] = 0x03;
    /* Max timeout for chip erase */
    pfl->cfi_table[0x26] = 0x00;
    /* Device size */
    pfl->cfi_table[0x27] = ctz32(device_len);
    /* Flash device interface (8 & 16 bits) */
    pfl->cfi_table[0x28] = 0x01;
    pfl->cfi_table[0x29] = 0x00;
    /* Max number of bytes in multi-bytes write */
    pfl->cfi_table[0x2A] = 0x06;
    pfl->writeblock_size = 1 << pfl->cfi_table[0x2A];

    pfl->cfi_table[0x2B] = 0x00;
    /* Number of erase block regions (uniform) */
    pfl->cfi_table[0x2C] = 0x02;
    /* Erase block region 1 */
    pfl->cfi_table[0x2D] = 3;
    pfl->cfi_table[0x2E] = 0;
    pfl->cfi_table[0x2F] = pfl->sector_len >> 8;
    pfl->cfi_table[0x30] = pfl->sector_len >> 16;
    /* Erase block region 2 */
    pfl->cfi_table[0x31] = 0x7e;
    pfl->cfi_table[0x32] = 0;
    pfl->cfi_table[0x33] = 0;
    pfl->cfi_table[0x34] = 0x02;

    /* Extended */
    pfl->cfi_table[0x40] = 'P';
    pfl->cfi_table[0x41] = 'R';
    pfl->cfi_table[0x42] = 'I';

    pfl->cfi_table[0x43] = '1';
    pfl->cfi_table[0x44] = '3';

    pfl->cfi_table[0x45] = 0x00;
    pfl->cfi_table[0x46] = 0x02;
    pfl->cfi_table[0x47] = 0x01;
    pfl->cfi_table[0x48] = 0x00;

    pfl->cfi_table[0x49] = 0x08;
    pfl->cfi_table[0x4a] = 0x00;
    pfl->cfi_table[0x4b] = 0x01;
    pfl->cfi_table[0x4c] = 0x00;
    pfl->cfi_table[0x4d] = 0x85;
    pfl->cfi_table[0x4e] = 0x95;
    pfl->cfi_table[0x4f] = 0x02;
    pfl->cfi_table[0x50] = 0x01;

    /* Start in ROMD mode — reads go directly to RAM, bypassing MMIO handler */
    pfl->rom_mode = true;
}

static void pflash_jedec_reset(DeviceState *dev)
{
    PFlashJEDEC424 *pfl = PFLASH_JEDEC_424(dev);
    pflash_reset_state(pfl);
    pflash_enter_romd(pfl);
}

static const Property pflash_jedec_properties[] = {
    DEFINE_PROP_DRIVE("drive", PFlashJEDEC424, blk),
    DEFINE_PROP_UINT32("num-blocks", PFlashJEDEC424, nb_blocs, 0),
    DEFINE_PROP_UINT64("sector-length", PFlashJEDEC424, sector_len, 0),
    DEFINE_PROP_UINT8("width", PFlashJEDEC424, bank_width, 0),
    DEFINE_PROP_UINT8("device-width", PFlashJEDEC424, device_width, 0),
    DEFINE_PROP_UINT8("max-device-width", PFlashJEDEC424, max_device_width, 0),
    DEFINE_PROP_UINT32("bank-size", PFlashJEDEC424, bank_size, 0),
    DEFINE_PROP_UINT8("big-endian", PFlashJEDEC424, be, 0),
    DEFINE_PROP_UINT16("id0", PFlashJEDEC424, ident0, 0),
    DEFINE_PROP_UINT16("id1", PFlashJEDEC424, ident1, 0),
    DEFINE_PROP_UINT16("id2", PFlashJEDEC424, ident2, 0),
    DEFINE_PROP_UINT16("id3", PFlashJEDEC424, ident3, 0),
    DEFINE_PROP_STRING("name", PFlashJEDEC424, name),
};

static void pflash_jedec_class_init(ObjectClass *klass, CLASS_DATA_VOID_PTR *data)
{
    DeviceClass *dc = DEVICE_CLASS(klass);

    dc->realize = pflash_jedec_realize;
    device_class_set_legacy_reset(dc, pflash_jedec_reset);
    device_class_set_props(dc, pflash_jedec_properties);
    dc->vmsd = &vmstate_pflash_jedec424;
    set_bit(DEVICE_CATEGORY_STORAGE, dc->categories);
}

static const TypeInfo pflash_jedec_info = {
    .name           = TYPE_PFLASH_JEDEC_424,
    .parent         = TYPE_SYS_BUS_DEVICE,
    .instance_size  = sizeof(PFlashJEDEC424),
    .class_init     = pflash_jedec_class_init,
};

static void pflash_jedec_register_types(void)
{
    type_register_static(&pflash_jedec_info);
}

type_init(pflash_jedec_register_types)

PFlashJEDEC424 *pflash_jedec_424_register(hwaddr base,
                                           const char *name,
                                           hwaddr size,
                                           BlockBackend *blk,
                                           uint32_t sector_len,
                                           uint32_t bank_size,
                                           int width,
                                           uint16_t id0, uint16_t id1,
                                           uint16_t id2, uint16_t id3,
                                           int be)
{
    DeviceState *dev = qdev_new(TYPE_PFLASH_JEDEC_424);

    if (blk) {
        qdev_prop_set_drive(dev, "drive", blk);
    }
    qdev_prop_set_uint32(dev, "num-blocks", size / sector_len);
    qdev_prop_set_uint64(dev, "sector-length", sector_len);
    qdev_prop_set_uint8(dev, "width", width);
    qdev_prop_set_uint32(dev, "bank-size", bank_size);
    qdev_prop_set_uint8(dev, "big-endian", !!be);
    qdev_prop_set_uint16(dev, "id0", id0);
    qdev_prop_set_uint16(dev, "id1", id1);
    qdev_prop_set_uint16(dev, "id2", id2);
    qdev_prop_set_uint16(dev, "id3", id3);
    qdev_prop_set_string(dev, "name", name);
    sysbus_realize_and_unref(SYS_BUS_DEVICE(dev), &error_fatal);

    sysbus_mmio_map(SYS_BUS_DEVICE(dev), 0, base);
    return PFLASH_JEDEC_424(dev);
}
