package br.com.redclaw.zelda64player.store.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.databinding.ActivityStoreBinding

class StoreActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStoreBinding
    internal lateinit var viewModel: StoreViewModel
    private lateinit var adapter: StoreAdapter

    private var currentHacks: List<HackEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.storeToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.store_title)
        binding.storeToolbar.setNavigationOnClickListener { finish() }

        viewModel = ViewModelProvider(this)[StoreViewModel::class.java]

        val spanCount = resources.getInteger(R.integer.library_span_count)
        binding.storeGrid.layoutManager = GridLayoutManager(this, spanCount)
        adapter = StoreAdapter { hack -> openDetail(hack) }
        binding.storeGrid.adapter = adapter

        viewModel.catalog.observe(this) { state ->
            when (state) {
                is StoreViewModel.CatalogUiState.Loading -> {
                    binding.storeProgress.visibility = View.VISIBLE
                    binding.storeStatus.visibility = View.GONE
                }
                is StoreViewModel.CatalogUiState.Loaded -> {
                    binding.storeProgress.visibility = View.GONE
                    currentHacks = state.hacks
                    renderItems()
                    val isEmpty = state.hacks.isEmpty()
                    binding.storeEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    binding.storeGrid.visibility = if (isEmpty) View.GONE else View.VISIBLE
                }
                is StoreViewModel.CatalogUiState.Error -> {
                    binding.storeProgress.visibility = View.GONE
                    binding.storeStatus.text = state.message
                    binding.storeStatus.visibility = View.VISIBLE
                }
            }
        }

        // Refresh the grid badges after an install finishes.
        viewModel.install.observe(this) { state ->
            if (state is StoreViewModel.InstallUiState.Success) renderItems()
        }

        viewModel.refresh()
    }

    private fun renderItems() {
        val items = currentHacks.map { hack -> StoreItem(hack, viewModel.statusFor(hack)) }
        adapter.update(items)
    }

    private fun openDetail(hack: HackEntry) {
        HackDetailBottomSheet.newInstance(hack)
            .show(supportFragmentManager, "hack_detail")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.store_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                viewModel.refresh()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
