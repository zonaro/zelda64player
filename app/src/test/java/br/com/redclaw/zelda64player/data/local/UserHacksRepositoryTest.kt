package br.com.redclaw.zelda64player.data.local

import br.com.redclaw.zelda64player.data.model.BaseRomRef
import br.com.redclaw.zelda64player.data.model.Checksums
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.data.model.PatchRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class UserHacksRepositoryTest {

    private fun tempFile() = File.createTempFile("user_hacks", ".json").apply { deleteOnExit() }

    private fun entry(id: String, name: String = id) = HackEntry(
        id = id,
        name = name,
        description = "",
        author = "",
        version = "1.0",
        baseRom = BaseRomRef("", "", 0, Checksums("")),
        patch = PatchRef("", "", 0, Checksums(""))
    )

    @Test
    fun addGetRemoveRoundTrip() {
        val repo = UserHacksRepository(tempFile())
        repo.add(entry("a"))
        repo.add(entry("b"))
        assertEquals(2, repo.getAll().size)
        assertEquals("a", repo.getById("a")?.id)
        assertEquals("b", repo.getById("b")?.id)
        assertEquals(setOf("a", "b"), repo.asMap().keys)

        repo.remove("a")
        assertNull(repo.getById("a"))
        assertEquals("b", repo.getById("b")?.id)
    }

    @Test
    fun dedupeByIdOnReAdd() {
        val repo = UserHacksRepository(tempFile())
        repo.add(entry("a", "A1"))
        repo.add(entry("a", "A2"))
        assertEquals(1, repo.getAll().size)
        assertEquals("A2", repo.getById("a")?.name)
    }

    @Test
    fun removeAbsentIsNoOp() {
        val repo = UserHacksRepository(tempFile())
        repo.add(entry("a"))
        repo.remove("missing")
        assertEquals(1, repo.getAll().size)
    }
}
