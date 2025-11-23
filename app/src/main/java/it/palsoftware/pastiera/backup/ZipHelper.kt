package it.palsoftware.pastiera.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipHelper {
    fun zip(sourceDir: File, outputStream: OutputStream) {
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
            val basePath = sourceDir.toPath()
            sourceDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relative = basePath.relativize(file.toPath()).toString().replace("\\", "/")
                    val entry = ZipEntry(relative)
                    zipOut.putNextEntry(entry)
                    file.inputStream().use { input -> input.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
        }
    }

    fun unzip(inputStream: InputStream, targetDir: File) {
        ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
            var entry = zipIn.nextEntry
            val canonicalTargetRoot = targetDir.canonicalFile

            while (entry != null) {
                val entryName = entry.name.removePrefix("./")
                val outFile = File(targetDir, entryName)
                val canonicalOutFile = outFile.canonicalFile
                if (!canonicalOutFile.path.startsWith(canonicalTargetRoot.path)) {
                    throw IllegalStateException("Refusing to unzip entry outside target dir: $entryName")
                }

                if (entry.isDirectory) {
                    canonicalOutFile.mkdirs()
                } else {
                    canonicalOutFile.parentFile?.mkdirs()
                    FileOutputStream(canonicalOutFile).use { output ->
                        zipIn.copyTo(output)
                    }
                }

                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }
}
