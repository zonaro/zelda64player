package br.com.redclaw.zelda64player.views

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.local.MergedCatalogRepository
import br.com.redclaw.zelda64player.data.local.PatchRepository
import br.com.redclaw.zelda64player.databinding.ActivityLibraryBinding
import br.com.redclaw.zelda64player.settings.ui.SettingsActivity
import br.com.redclaw.zelda64player.store.ui.StoreActivity
import br.com.redclaw.zelda64player.views.CatalogBackedLibrarySource
import br.com.redclaw.zelda64player.views.HackLibraryEntry
import java.io.File

class LibraryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLibraryBinding

    /* Stateless: rebuilt from the source on every (re)create, so process
       death / configuration changes need no saved instance state. */
    private lateinit var items: List<HackLibraryEntry>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.decorView.setOnApplyWindowInsetsListener { view, windowInsets ->
            view.post { immersive(window) }
            windowInsets
        }

        val external = getExternalFilesDir(null) ?: filesDir
        val patchRepository = PatchRepository(File(external, "patches"))
        val installedRepository =
            InstalledHacksRepository(File(filesDir, "installed_hacks.json"))
        val mergedCatalog = MergedCatalogRepository(File(filesDir, "merged_catalog.json"))
        val source = CatalogBackedLibrarySource(
            patchRepository, installedRepository, mergedCatalog.asMap()
        )
        items = source.available()

        setupGrid()
        binding.librarySettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.libraryStore.setOnClickListener {
            startActivity(Intent(this, StoreActivity::class.java))
        }
    }

    private fun setupGrid() {
        val spanCount = resources.getInteger(R.integer.library_span_count)
        binding.libraryGrid.layoutManager = GridLayoutManager(this, spanCount)
        binding.libraryGrid.adapter = LibraryAdapter(items) { item ->
            val intent = Intent(this, GameActivity::class.java).apply {
                putExtra("hack_id", item.id)
            }
            startActivity(intent)
        }
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val isEmpty = items.isEmpty()
        binding.libraryEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.libraryGrid.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /** Hide the system bars when the config permits it (mirrors GameActivity). */
    @Suppress("DEPRECATION")
    private fun immersive(window: Window) {
        if (!resources.getBoolean(R.bool.config_fullscreen))
            return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN
        }
    }

    private class LibraryAdapter(
        private val items: List<HackLibraryEntry>,
        private val onItemClick: (HackLibraryEntry) -> Unit
    ) : RecyclerView.Adapter<LibraryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tile_title)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.library_tile, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
