package com.jartool.app.core

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

data class CompareResult(
    val oldInfo: JarInfo,
    val newInfo: JarInfo,
    val addedClasses: List<String>?,
    val removedClasses: List<String>?,
    val addedFiles: List<String>?,
    val removedFiles: List<String>?,
    val changedFiles: List<String>?,
)

object JarComparer {
    private val dexName = Regex("classes\\d*\\.dex")

    private fun crcs(jar: File): Map<String, Long> =
        ZipFile(jar).use { z ->
            z.entries().asSequence()
                .filter { !it.isDirectory && !dexName.matches(it.name) }
                .associate { it.name to it.crc }
        }

    fun compare(ctx: Context, oldP: String, newP: String, code: Boolean, resources: Boolean): CompareResult {
        val oldInfo = JarAnalyzer.analyze(ctx, oldP)
        val newInfo = JarAnalyzer.analyze(ctx, newP)

        var addedC: List<String>? = null
        var removedC: List<String>? = null
        if (code) {
            val a = JarAnalyzer.classSet(ctx, oldP)
            val b = JarAnalyzer.classSet(ctx, newP)
            addedC = (b - a).sorted()
            removedC = (a - b).sorted()
        }

        var addedF: List<String>? = null
        var removedF: List<String>? = null
        var changedF: List<String>? = null
        if (resources) {
            val a = crcs(Workspace.jarOf(ctx, oldP))
            val b = crcs(Workspace.jarOf(ctx, newP))
            addedF = (b.keys - a.keys).sorted()
            removedF = (a.keys - b.keys).sorted()
            changedF = a.keys.intersect(b.keys).filter { a[it] != b[it] }.sorted()
        }
        return CompareResult(oldInfo, newInfo, addedC, removedC, addedF, removedF, changedF)
    }
}
