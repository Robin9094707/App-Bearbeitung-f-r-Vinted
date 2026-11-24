package de.glanzstueck.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.glanzstueck.app.imaging.EditParams
import de.glanzstueck.app.imaging.MaskOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class ProjectInfo(val id: String, val dir: File, val thumb: File, val modified: Long)

class LoadedProject(val id: String, val source: Bitmap, val mask: ByteArray?, val params: EditParams, val bgImage: File?)

/** Projects kept inside the app so they can be edited again later. */
class ProjectStore(private val context: Context) {
    private val root get() = File(context.filesDir, "projects").apply { mkdirs() }
    private val _projects = MutableStateFlow<List<ProjectInfo>>(emptyList())
    val projects: StateFlow<List<ProjectInfo>> = _projects.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _projects.value = root.listFiles()?.filter { File(it, "params.json").exists() && File(it, "thumb.jpg").exists() }
            ?.map { ProjectInfo(it.name, it, File(it, "thumb.jpg"), File(it, "params.json").lastModified()) }
            ?.sortedByDescending { it.modified } ?: emptyList()
    }

    fun newId() = "p" + System.currentTimeMillis()

    suspend fun save(id: String, source: Bitmap, sourceChanged: Boolean, mask: ByteArray?, params: EditParams, thumb: Bitmap, bgImage: File?) =
        withContext(Dispatchers.IO) {
            val dir = File(root, id).apply { mkdirs() }
            val srcFile = File(dir, "source.jpg")
            if (sourceChanged || !srcFile.exists()) srcFile.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 97, it) }
            val maskFile = File(dir, "mask.png")
            if (mask != null) {
                val g = MaskOps.maskToGrayBitmap(mask, source.width, source.height)
                maskFile.outputStream().use { g.compress(Bitmap.CompressFormat.PNG, 100, it) }
                g.recycle()
            } else maskFile.delete()
            var p = params
            if (bgImage != null && bgImage.exists() && bgImage.parentFile != dir) {
                val dst = File(dir, "background.jpg")
                bgImage.copyTo(dst, overwrite = true)
                p = p.copy(background = p.background.copy(imagePath = dst.absolutePath))
            }
            File(dir, "params.json").writeText(AppJson.encodeToString(EditParams.serializer(), p))
            File(dir, "thumb.jpg").outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            refresh()
        }

    suspend fun load(id: String): LoadedProject = withContext(Dispatchers.IO) {
        val dir = File(root, id)
        val src = ImageIO.loadFile(File(dir, "source.jpg"))
        val maskFile = File(dir, "mask.png")
        val mask = if (maskFile.exists()) {
            val g = BitmapFactory.decodeFile(maskFile.absolutePath)
            val scaled = if (g.width != src.width || g.height != src.height) Bitmap.createScaledBitmap(g, src.width, src.height, true) else g
            MaskOps.grayBitmapToMask(scaled)
        } else null
        val params = AppJson.decodeFromString(EditParams.serializer(), File(dir, "params.json").readText())
        val bg = params.background.imagePath?.let { File(it) }?.takeIf { it.exists() }
        LoadedProject(id, src, mask, params, bg)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(root, id).deleteRecursively()
        refresh()
    }

    fun backgroundsDir() = File(context.filesDir, "backgrounds").apply { mkdirs() }
}
