#ifndef HW_PFLASH_JEDEC_424_H
#define HW_PFLASH_JEDEC_424_H

/*
 * CFI parallel flash with Jedec (42.4) command set
 * Ported from QEMU 2.5.0-pebble8 to QEMU 10.x APIs.
 *
 * Key difference from pflash_cfi02: per-bank command states.
 * The Macronix MX29VS128FB has 8 banks of 2MB each. With per-bank states,
 * firmware can write to one bank while reading from another. The standard
 * pflash_cfi02 uses a single global command state, which breaks concurrent
 * read/write to different banks.
 */

#include "exec/hwaddr.h"
#include "qom/object.h"

#define TYPE_PFLASH_JEDEC_424 "cfi.pflash.jedec-42.4"
OBJECT_DECLARE_SIMPLE_TYPE(PFlashJEDEC424, PFLASH_JEDEC_424)

PFlashJEDEC424 *pflash_jedec_424_register(hwaddr base,
                                           const char *name,
                                           hwaddr size,
                                           BlockBackend *blk,
                                           uint32_t sector_len,
                                           uint32_t bank_size,
                                           int width,
                                           uint16_t id0, uint16_t id1,
                                           uint16_t id2, uint16_t id3,
                                           int be);

#endif /* HW_PFLASH_JEDEC_424_H */
