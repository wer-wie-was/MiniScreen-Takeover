/*
 * Pebble QEMU compatibility macros
 *
 * Handles API differences between QEMU versions (10.0 vs 10.1+).
 */

#ifndef PEBBLE_COMPAT_H
#define PEBBLE_COMPAT_H

/* QEMU 10.1+ changed class_init data param from (void *) to (const void *).
 * Use CLASS_DATA_VOID_PTR to match whichever QEMU we're building against. */
#if defined(QEMU_VERSION_MINOR) && QEMU_VERSION_MINOR >= 1
#define CLASS_DATA_VOID_PTR const void
#else
#define CLASS_DATA_VOID_PTR void
#endif

#endif /* PEBBLE_COMPAT_H */
