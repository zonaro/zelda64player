/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.zelda64player.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Holds the list of captured items for [GalleryActivity].
 *
 * Loads from [GalleryRepository] on demand ([load]) and after a [delete],
 * exposing the current list via [items] (a [StateFlow] so the Activity can
 * collect it without manual adapter diffing). All repository IO runs on
 * [Dispatchers.IO] via the repository's own `suspend` API.
 */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)

    private val _items = MutableStateFlow<List<GalleryItem>>(emptyList())
    val items: StateFlow<List<GalleryItem>> = _items

    /** Reload the gallery from disk. */
    fun load() {
        viewModelScope.launch {
            _items.value = repository.list()
        }
    }

    /** Delete [item] from disk and refresh the list. */
    fun delete(item: GalleryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(item)
            _items.value = repository.list()
        }
    }
}
