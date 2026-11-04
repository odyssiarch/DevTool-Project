package com.devtool.app.core

import java.io.File

/** Pintu masuk ke mesin decompile dan recompile. Diisi di tahap berikutnya. */
interface DexEngine {
    suspend fun decompileToJava(dexFiles: List<File>, outDir: File)
    suspend fun disassemble(dexFile: File, outDir: File, apiLevel: Int, classpath: List<File>)
    suspend fun assemble(smaliDir: File, outDex: File, apiLevel: Int)
}
