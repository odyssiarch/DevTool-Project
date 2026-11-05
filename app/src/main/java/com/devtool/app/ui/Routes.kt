package com.devtool.app.ui

import android.net.Uri

/**
 * Semua rute navigasi di satu tempat.
 * Layar tidak boleh menulis string rute sendiri: pakai konstanta (untuk composable)
 * dan fungsi pembuat rute (untuk navigate).
 */
object Routes {
    // Tab bawah
    const val TOOLS = "tools"
    const val PROJECTS = "projects"
    const val SETTINGS = "settings"

    // Layar tanpa argumen
    const val EDIT = "edit"
    const val INFO = "info"
    const val COMPARE = "compare"
    const val SOURCE = "source"
    const val EXPORT = "export"
    const val LOGCAT = "logcat"
    const val LIVE_LOGCAT = "live_logcat"

    // Nama argumen
    const val ARG_NAME = "name"
    const val ARG_EDIT = "edit"
    const val ARG_PROJECT = "project"
    const val ARG_CLS = "cls"

    // Pola rute, dipakai di composable(...)
    const val PROJECT = "project/{$ARG_NAME}?$ARG_EDIT={$ARG_EDIT}"
    const val EDITED = "edited/{$ARG_NAME}"
    const val BROWSE = "browse/{$ARG_NAME}"
    const val EXPORT_PROJECT = "exportproject/{$ARG_NAME}"
    const val SMALI = "smali/{$ARG_PROJECT}?$ARG_CLS={$ARG_CLS}"
    const val CODE = "code/{$ARG_PROJECT}?$ARG_CLS={$ARG_CLS}"

    // Pembuat rute, dipakai di navigate(...)
    fun project(name: String, edit: Boolean = false) = "project/${Uri.encode(name)}?$ARG_EDIT=$edit"
    fun edited(name: String) = "edited/${Uri.encode(name)}"
    fun browse(name: String) = "browse/${Uri.encode(name)}"
    fun exportProject(name: String) = "exportproject/${Uri.encode(name)}"
    fun smali(project: String, cls: String) = "smali/${Uri.encode(project)}?$ARG_CLS=${Uri.encode(cls)}"
    fun code(project: String, cls: String) = "code/${Uri.encode(project)}?$ARG_CLS=${Uri.encode(cls)}"
}