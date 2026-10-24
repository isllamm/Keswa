package com.alsoug.keswa.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import java.io.File
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders a composable to a PNG, with no window and no display.
 *
 * The same idea as Phase 3's print proofs: a change to something visual should leave an artefact
 * somebody can look at, in the build directory, without running the app. It is not a screenshot
 * *test* — nothing is asserted and nothing fails — it is a way to see what the code produces.
 */
object UiProof {

    private val directory = File("build/ui-proofs").also { it.mkdirs() }

    fun render(
        name: String,
        width: Int = 1440,
        height: Int = 900,
        density: Float = 2f,
        content: @Composable () -> Unit,
    ): File {
        val scene = ImageComposeScene(
            width = (width * density).toInt(),
            height = (height * density).toInt(),
            density = Density(density),
            content = content,
        )
        try {
            val bytes = scene.render()
                .encodeToData(EncodedImageFormat.PNG)
                ?.bytes
                ?: error("Skia declined to encode $name")
            return directory.resolve("$name.png").also { it.writeBytes(bytes) }
        } finally {
            scene.close()
        }
    }
}
