package de.glanzstueck.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

object ImageIO {
    const val WORKING_LONG_SIDE = 3072
    private const val ALBUM = "Glanzstück"

    suspend fun load(context: Context, uri: Uri, maxSide: Int = WORKING_LONG_SIDE): Bitmap = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        decode(source, maxSide)
    }

    suspend fun loadFile(file: File, maxSide: Int = WORKING_LONG_SIDE): Bitmap = withContext(Dispatchers.IO) {
        decode(ImageDecoder.createSource(file), maxSide)
    }

    private fun decode(source: ImageDecoder.Source, maxSide: Int): Bitmap {
        val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
            val w = info.size.width; val h = info.size.height
            val l = max(w, h)
            if (l > maxSide) {
                val s = maxSide.toFloat() / l
                decoder.setTargetSize(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()))
            }
        }
        return if (bmp.config != Bitmap.Config.ARGB_8888) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
    }

    fun newCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val f = File(dir, "foto_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, context.packageName + ".files", f)
    }

    private fun fileName(ext: String) =
        "Glanzstueck_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.GERMANY).format(Date()) + "." + ext

    /** Saves into Pictures/Glanzstück so the photo shows up in every gallery and in Vinted's picker. */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap, png: Boolean, quality: Int = 95): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName(if (png) "png" else "jpg"))
            put(MediaStore.Images.Media.MIME_TYPE, if (png) "image/png" else "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Galerie nicht verfügbar")
        try {
            resolver.openOutputStream(uri)!!.use { out ->
                bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        uri
    }

    /** Writes a temporary file for the Android share sheet (e.g. straight into Vinted). */
    suspend fun shareFile(context: Context, bitmap: Bitmap, png: Boolean): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, fileName(if (png) "png" else "jpg"))
        f.outputStream().use { bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
        FileProvider.getUriForFile(context, context.packageName + ".files", f)
    }
}
