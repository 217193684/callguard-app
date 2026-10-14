package za.jobradar.cv

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import za.jobradar.data.Cv
import java.util.UUID
import java.util.zip.ZipInputStream

object CvParser {
    @Volatile private var pdfReady = false

    fun displayName(ctx: Context, uri: Uri): String {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) c.getString(i)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "CV"
    }

    /** Reads a PDF / DOCX / TXT CV into a Cv. Throws with a readable message on failure. */
    fun parse(ctx: Context, uri: Uri): Cv {
        val name = displayName(ctx, uri)
        val lower = name.lowercase()
        val mime = ctx.contentResolver.getType(uri) ?: ""
        val text = when {
            lower.endsWith(".pdf") || mime == "application/pdf" -> pdfText(ctx, uri)
            lower.endsWith(".docx") || mime.contains("wordprocessingml") -> docxText(ctx, uri)
            lower.endsWith(".doc") -> throw IllegalArgumentException("Old .doc files aren't supported — save it as PDF or DOCX")
            else -> ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        }.replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()

        if (text.length < 80) throw IllegalArgumentException("No readable text found (is it a scanned image?)")
        return build(name, text)
    }

    fun build(name: String, text: String, id: String = UUID.randomUUID().toString(), alertOn: Boolean = true): Cv {
        val skills = Skills.skillsIn(text)
        val titles = Skills.titlesIn(text).ifEmpty { Skills.titlesFromSkills(skills) }
        return Cv(id, name, text, skills, titles.take(8), System.currentTimeMillis(), alertOn)
    }

    private fun pdfText(ctx: Context, uri: Uri): String {
        if (!pdfReady) {
            PDFBoxResourceLoader.init(ctx.applicationContext)
            pdfReady = true
        }
        return ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Couldn't open file" }
            PDDocument.load(input).use { doc -> PDFTextStripper().getText(doc) }
        }
    }

    private fun docxText(ctx: Context, uri: Uri): String {
        ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Couldn't open file" }
            ZipInputStream(input).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (e.name == "word/document.xml") {
                        val xml = zip.readBytes().toString(Charsets.UTF_8)
                        return xml
                            .replace(Regex("</w:p>"), "\n")
                            .replace(Regex("<w:tab/>"), " ")
                            .replace(Regex("<w:br[^>]*/>"), "\n")
                            .replace(Regex("<[^>]+>"), "")
                            .let(::unescape)
                    }
                    e = zip.nextEntry
                }
            }
        }
        throw IllegalArgumentException("Not a valid DOCX file")
    }

    fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&")
}
