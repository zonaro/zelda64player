package br.com.redclaw.zelda64player.tracker.autotracker

import android.content.ContextWrapper
import br.com.redclaw.zelda64player.tracker.data.TrackerRepository
import br.com.redclaw.zelda64player.tracker.model.TrackerGame
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TrackerRepositoryTest {
    @Test fun liveHoldersShareProgressAndPersistTogetherWithoutCrossHackContamination() {
        val dir = kotlin.io.path.createTempDirectory("tracker-repository").toFile()
        try {
            val context = object : ContextWrapper(null) {
                override fun getFilesDir(): File = dir
            }
            val gameplay = TrackerRepository(context)
            val dialog = TrackerRepository(context)
            val first = gameplay.load(TrackerGame.OOT, "one")
            val second = dialog.load(TrackerGame.OOT, "one")
            assertSame(first, second)
            first.obtainedItems["bow"] = 2
            gameplay.save(first, "one")
            second.foundSongs.add("song_of_time")
            dialog.save(second, "one")
            val disk = JSONObject(File(dir, "tracker_state_one.json").readText())
            assertEquals(2, disk.getJSONObject("obtainedItems").getInt("bow"))
            assertEquals("song_of_time", disk.getJSONArray("foundSongs").getString(0))
            assertTrue(gameplay.load(TrackerGame.OOT, "two").obtainedItems.isEmpty())
            assertNotSame(first, gameplay.load(TrackerGame.MM, "two"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
