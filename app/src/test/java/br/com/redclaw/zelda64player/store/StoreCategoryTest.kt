package br.com.redclaw.zelda64player.store.ui

import br.com.redclaw.zelda64player.R
import org.junit.Assert.assertEquals
import org.junit.Test

class StoreCategoryTest {

    @Test
    fun allCategoriesAreAvailableInDisplayOrder() {
        assertEquals(
            listOf(
                R.string.store_cat_all,
                R.string.store_cat_installed,
                R.string.store_cat_updates,
                R.string.store_cat_oot,
                R.string.store_cat_mm
            ),
            StoreCategory.ALL.map(StoreCategory::labelRes)
        )
    }
}
