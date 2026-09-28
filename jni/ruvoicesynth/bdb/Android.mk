LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := db

# This directive results in arm (vs thumb) code.  It's necessary to
# allow some BDB assembler code (for mutexes) to compile.
LOCAL_ARM_MODE := arm

# basic includes for BDB 11gR2
LOCAL_C_INCLUDES := $(LOCAL_PATH)/src $(LOCAL_PATH)
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)

# Source files
LOCAL_SRC_FILES := \
	src/btree/bt_compact.c \
	src/btree/bt_compare.c \
	src/btree/bt_compress.c \
	src/btree/bt_conv.c \
	src/btree/bt_curadj.c \
	src/btree/bt_cursor.c \
	src/btree/bt_delete.c \
	src/btree/bt_method.c \
	src/btree/bt_open.c \
	src/btree/bt_put.c \
	src/btree/bt_rec.c \
	src/btree/bt_reclaim.c \
	src/btree/bt_recno.c \
	src/btree/bt_rsearch.c \
	src/btree/bt_search.c \
	src/btree/bt_split.c \
	src/btree/bt_stat.c \
	src/btree/bt_upgrade.c \
	src/btree/bt_verify.c \
	src/btree/btree_auto.c \
	src/btree/btree_autop.c \
	src/clib/bsearch.c \
	src/clib/rand.c \
	src/clib/snprintf.c \
	src/common/clock.c \
	src/common/crypto_stub.c \
	src/common/db_byteorder.c \
	src/common/db_compint.c \
	src/common/db_err.c \
	src/common/db_getlong.c \
	src/common/db_idspace.c \
	src/common/db_log2.c \
	src/common/db_shash.c \
	src/common/dbt.c \
	src/common/mkpath.c \
	src/common/os_method.c \
	src/common/zerofill.c \
	src/db/crdel_auto.c \
	src/db/crdel_rec.c \
	src/db/db.c \
	src/db/db_am.c \
	src/db/db_auto.c \
	src/db/db_backup.c \
	src/db/db_cam.c \
	src/db/db_cds.c \
	src/db/db_compact.c \
	src/db/db_conv.c \
	src/db/db_copy.c \
	src/db/db_dispatch.c \
	src/db/db_dup.c \
	src/db/db_iface.c \
	src/db/db_join.c \
	src/db/db_meta.c \
	src/db/db_method.c \
	src/db/db_open.c \
	src/db/db_overflow.c \
	src/db/db_pr.c \
	src/db/db_rec.c \
	src/db/db_reclaim.c \
	src/db/db_remove.c \
	src/db/db_rename.c \
	src/db/db_ret.c \
	src/db/db_setid.c \
	src/db/db_setlsn.c \
	src/db/db_sort_multiple.c \
	src/db/db_stati.c \
	src/db/db_truncate.c \
	src/db/db_upg.c \
	src/db/db_upg_opd.c \
	src/db/db_vrfy_stub.c \
	src/db/partition.c \
	src/dbreg/dbreg.c \
	src/dbreg/dbreg_auto.c \
	src/dbreg/dbreg_rec.c \
	src/dbreg/dbreg_stat.c \
	src/dbreg/dbreg_util.c \
	src/env/env_alloc.c \
	src/env/env_backup.c \
	src/env/env_config.c \
	src/env/env_failchk.c \
	src/env/env_file.c \
	src/env/env_globals.c \
	src/env/env_method.c \
	src/env/env_name.c \
	src/env/env_open.c \
	src/env/env_recover.c \
	src/env/env_region.c \
	src/env/env_register.c \
	src/env/env_sig.c \
	src/env/env_stat.c \
	src/fileops/fileops_auto.c \
	src/fileops/fop_basic.c \
	src/fileops/fop_rec.c \
	src/fileops/fop_util.c \
	src/hash/hash_func.c \
	src/hash/hash_stub.c \
	src/heap/heap_stub.c \
	src/hmac/hmac.c \
	src/hmac/sha1.c \
	src/lock/lock.c \
	src/lock/lock_deadlock.c \
	src/lock/lock_failchk.c \
	src/lock/lock_id.c \
	src/lock/lock_list.c \
	src/lock/lock_method.c \
	src/lock/lock_region.c \
	src/lock/lock_stat.c \
	src/lock/lock_timer.c \
	src/lock/lock_util.c \
	src/log/log.c \
	src/log/log_archive.c \
	src/log/log_compare.c \
	src/log/log_debug.c \
	src/log/log_get.c \
	src/log/log_method.c \
	src/log/log_print.c \
	src/log/log_put.c \
	src/log/log_stat.c \
	src/log/log_verify_stub.c \
	src/mp/mp_alloc.c \
	src/mp/mp_backup.c \
	src/mp/mp_bh.c \
	src/mp/mp_fget.c \
	src/mp/mp_fmethod.c \
	src/mp/mp_fopen.c \
	src/mp/mp_fput.c \
	src/mp/mp_fset.c \
	src/mp/mp_method.c \
	src/mp/mp_mvcc.c \
	src/mp/mp_region.c \
	src/mp/mp_register.c \
	src/mp/mp_resize.c \
	src/mp/mp_stat.c \
	src/mp/mp_sync.c \
	src/mp/mp_trickle.c \
	src/mutex/mut_alloc.c \
	src/mutex/mut_failchk.c \
	src/mutex/mut_method.c \
	src/mutex/mut_region.c \
	src/mutex/mut_stat.c \
	src/mutex/mut_tas.c \
	src/os/os_abort.c \
	src/os/os_abs.c \
	src/os/os_alloc.c \
	src/os/os_clock.c \
	src/os/os_config.c \
	src/os/os_cpu.c \
	src/os/os_ctime.c \
	src/os/os_dir.c \
	src/os/os_errno.c \
	src/os/os_fid.c \
	src/os/os_flock.c \
	src/os/os_fsync.c \
	src/os/os_getenv.c \
	src/os/os_handle.c \
	src/os/os_map.c \
	src/os/os_mkdir.c \
	src/os/os_open.c \
	src/os/os_path.c \
	src/os/os_pid.c \
	src/os/os_rename.c \
	src/os/os_root.c \
	src/os/os_rpath.c \
	src/os/os_rw.c \
	src/os/os_seek.c \
	src/os/os_stack.c \
	src/os/os_stat.c \
	src/os/os_tmpdir.c \
	src/os/os_truncate.c \
	src/os/os_uid.c \
	src/os/os_unlink.c \
	src/os/os_yield.c \
	src/qam/qam_stub.c \
	src/rep/rep_stub.c \
	src/repmgr/repmgr_stub.c \
	src/sequence/seq_stat.c \
	src/sequence/sequence.c \
	src/txn/txn.c \
	src/txn/txn_auto.c \
	src/txn/txn_chkpt.c \
	src/txn/txn_failchk.c \
	src/txn/txn_method.c \
	src/txn/txn_rec.c \
	src/txn/txn_recover.c \
	src/txn/txn_region.c \
	src/txn/txn_stat.c \
	src/txn/txn_util.c

ifeq ($(TARGET_ARCH),arm64)
LOCAL_CFLAGS += -DARCH_64BIT
endif

LOCAL_CFLAGS += -std=c11 -Wno-deprecated-non-prototype -DHAVE_USLEEP=1 -Dfdatasync=fsync

include $(BUILD_STATIC_LIBRARY)
