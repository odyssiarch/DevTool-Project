package com.jartool.app.core

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

data class DexInfo(val name: String, val version: String, val classes: Int, val size: Long)
data class JarInfo(val project: String, val size: Long, val entries: Int, val dex: List<DexInfo>) {
    val totalClasses: Int get() = dex.sumOf { maxOf(it.classes, 0) }
}

object JarAnalyzer {
    fun dexDir(ctx: Context, project: String) = File(Workspace.jarOf(ctx, project).parentFile, "dex")

    fun analyze(ctx: Context, project: String): JarInfo {
        val jar = Workspace.jarOf(ctx, project)
        val dir = dexDir(ctx, project)
        dir.deleteRecursively()
        val files = JarPackager.extractDex(jar, dir)
        val entries = ZipFile(jar).use { it.size() }
        val dex = files.map { f ->
            DexInfo(
                name = f.name,
                version = DexVersion.read(f) ?: "?",
                classes = runCatching { DexReader.classNames(f).size }.getOrDefault(-1),
                size = f.length(),
            )
        }
        return JarInfo(project, jar.length(), entries, dex)
    }

    fun classSet(ctx: Context, project: String): Set<String> =
        (dexDir(ctx, project).listFiles()?.toList().orEmpty())
            .flatMap { runCatching { DexReader.classNames(it) }.getOrDefault(emptyList()) }
            .toSet()
}
