package com.outrageousstorm.aodsuite.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.outrageousstorm.aodsuite.aod.AodRepository

class MainViewModelFactory(private val ctx: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MainViewModel(AodRepository(ctx.cacheDir, ctx.contentResolver), ctx.packageName) as T
}
