package br.com.redclaw.zelda64player.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameSanitizerTest {

    @Test
    fun keepsSimpleName() {
        assertEquals("OoT_1.0.z64", FileNameSanitizer.sanitize("OoT_1.0.z64"))
    }

    @Test
    fun stripsPathSeparators() {
        assertEquals("foo_bar", FileNameSanitizer.sanitize("foo/bar"))
        assertEquals("a_b", FileNameSanitizer.sanitize("a\\b"))
    }

    @Test
    fun stripsIllegalCharacters() {
        assertEquals("rom_name_.txt", FileNameSanitizer.sanitize("rom:name*?.txt"))
        assertEquals("x_y_z", FileNameSanitizer.sanitize("x<y>z"))
    }

    @Test
    fun collapsesWhitespace() {
        assertEquals("my_rom", FileNameSanitizer.sanitize("  my   rom  "))
    }

    @Test
    fun fallsBackToRomWhenBlank() {
        assertEquals("rom", FileNameSanitizer.sanitize("///"))
        assertEquals("rom", FileNameSanitizer.sanitize("   "))
        assertEquals("rom", FileNameSanitizer.sanitize(""))
    }
}
