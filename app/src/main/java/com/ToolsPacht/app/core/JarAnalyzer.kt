package com.jartool.app.core

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

data class DexInfo(val name: String, val version: String, val classes: Int, val size: Long, val logical: Int = 1) {
    fun describe(): String = buildString {
        append("dex ").append(version)
        if (logical > 1) append(", $logical dex dalam 1 kontainer")
        append(", ").append(if (classes >= 0) classes.toString() else "?").append(" class, ")
        append(formatSize(size))
    }
}

data class JarInfo(val project: String, val size: Long, val entries: Int, val dex: List<DexInfo>) {
    val totalClasses: Int get() = dex.sumOf { maxOf(it.classes, 0) }
}

data class ClassEntry(val name: String, val dex: String)

object JarAnalyzer {
    fun dexDir(ctx: Context, project: String) = File(Workspace.jarOf(ctx, project).parentFile, "dex")

    fun analyze(ctx: Context, project: String): JarInfo {
        val jar = Workspace.jarOf(ctx, project)
        val dir = dexDir(ctx, project)
        dir.deleteRecursively()
        val files = JarPackager.extractDex(jar, dir)
        val entries = ZipFile(jar).use { it.size() }
        val dex = files.map { f ->
            val r = runCatching { DexReader.read(f) }.getOrNull()
            DexInfo(
                name = f.name,
                version = r?.version ?: (DexVersion.read(f) ?: "?"),
                classes = r?.classes?.size ?: -1,
                size = f.length(),
                logical = r?.logical ?: 1,
            )
        }
        return JarInfo(project, jar.length(), entries, dex)
    }

    private fun dexFiles(ctx: Context, project: String): List<File> {
        val dir = dexDir(ctx, project)
        if (dir.listFiles().isNullOrEmpty()) JarPackager.extractDex(Workspace.jarOf(ctx, project), dir)
        return dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
    }

    fun classSet(ctx: Context, project: String): Set<String> =
        dexFiles(ctx, project)
            .flatMap { runCatching { DexReader.classNames(it) }.getOrDefault(emptyList()) }
            .toSet()

    fun classEntries(ctx: Context, project: String): List<ClassEntry> =
        dexFiles(ctx, project)
            .flatMap { f -> runCatching { DexReader.classNames(f) }.getOrDefault(emptyList()).map { ClassEntry(it, f.name) } }
            .sortedBy { it.name }
}
