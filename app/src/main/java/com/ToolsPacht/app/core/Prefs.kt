package com.jartool.app.core

import android.content.Context

val DECOMPILE_MODES = listOf("Kode dan resource", "Hanya kode", "Hanya resource")

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("jartool", Context.MODE_PRIVATE)

    fun decompileMode(ctx: Context): Int = sp(ctx).getInt("mode", 0).coerceIn(0, DECOMPILE_MODES.size - 1)
    fun setDecompileMode(ctx: Context, v: Int) = sp(ctx).edit().putInt("mode", v).apply()

    fun threads(ctx: Context): Int = sp(ctx).getInt("threads", 1).coerceIn(1, 8)
    fun setThreads(ctx: Context, v: Int) = sp(ctx).edit().putInt("threads", v.coerceIn(1, 8)).apply()
}
