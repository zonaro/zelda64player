package br.com.redclaw.zelda64player.settings

/**
 * Sanitizes an arbitrary filename (e.g. a document title coming from the Storage
 * Access Framework) into a safe local filename: strips path separators and
 * characters that are illegal or ambiguous on common filesystems, collapses
 * runs of whitespace into a single underscore, and never returns an empty string
 * (it falls back to "rom").
 *
 * Pure and side-effect free so it can be unit-tested on the JVM.
 */
object FileNameSanitizer {
    private val ILLEGAL = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')

    fun sanitize(name: String): String {
        val replaced = name.map { c ->
            if (c in ILLEGAL || c.isISOControl()) '_' else c
        }.joinToString("")
        // Collapse runs of separators/whitespace into a single underscore, then
        // trim leading/trailing separators.
        val cleaned = replaced.replace("[\\s_]+".toRegex(), "_")
            .trim('_')
            .trim()
        // Fall back to a safe default when nothing usable remains.
        return if (cleaned.isBlank() || cleaned.none { it.isLetterOrDigit() }) "rom" else cleaned
    }
}
