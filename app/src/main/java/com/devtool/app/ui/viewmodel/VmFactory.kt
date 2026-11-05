package com.devtool.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Pembuat ViewModel yang butuh argumen (misalnya nama proyek), tanpa library tambahan. */
fun <VM : ViewModel> vmFactory(build: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = build() as T
    }
