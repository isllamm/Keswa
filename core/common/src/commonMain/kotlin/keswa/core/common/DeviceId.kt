package keswa.core.common

/** Identifies the physical device/install a row originated from — see docs/data-model.md §1. */
@JvmInline
value class DeviceId(val value: String)

/**
 * Reads (and, on first run, creates and persists) this installation's [DeviceId]. The actual
 * persistence is platform storage (a config file today), so the implementation lives in :data or
 * :app:desktop — this module only defines the port.
 */
interface DeviceIdProvider {
    fun current(): DeviceId
}
