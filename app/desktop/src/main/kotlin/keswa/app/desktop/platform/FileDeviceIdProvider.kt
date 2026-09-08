package keswa.app.desktop.platform

import keswa.core.common.AppPaths
import keswa.core.common.DeviceId
import keswa.core.common.DeviceIdProvider
import keswa.core.common.Ulid
import java.io.File
import java.util.Properties

/** Generates a ULID on first run and persists it in `config/device.properties`. */
class FileDeviceIdProvider(appPaths: AppPaths) : DeviceIdProvider {

    private val file = File(appPaths.configDir, "device.properties")
    private val id: DeviceId = load() ?: generateAndSave()

    override fun current(): DeviceId = id

    private fun load(): DeviceId? {
        if (!file.exists()) return null
        val props = Properties().apply { file.inputStream().use { load(it) } }
        return props.getProperty("deviceId")?.let { DeviceId(it) }
    }

    private fun generateAndSave(): DeviceId {
        val newId = DeviceId(Ulid.generate(System.currentTimeMillis()))
        file.parentFile?.mkdirs()
        val props = Properties().apply { setProperty("deviceId", newId.value) }
        file.outputStream().use { props.store(it, "Keswa device identity — do not edit") }
        return newId
    }
}
