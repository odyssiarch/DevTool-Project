package com.devtool.app.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devtool.app.core.SmaliEngine
import com.devtool.app.core.Workspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/**
 * Menjalankan "Bongkar ke Smali" dan "Susun ulang JAR" untuk satu proyek.
 * Pekerjaannya hidup di ViewModel, jadi tidak ikut mati dan tidak mengulang dari awal
 * waktu layar diputar atau komposisi dibuat ulang.
 */
class SmaliJobViewModel(
    private val app: Context,
    private val project: String,
) : ViewModel() {

    var busy by mutableStateOf(false)
        private set
    var lines by mutableStateOf(listOf<String>())
        private set
    var hasSmali by mutableStateOf(SmaliEngine.hasSmali(app, project))
        private set
    var outputAda by mutableStateOf(outputJar().exists())
        private set

    private fun outputJar() = File(Workspace.jarOf(app, project).parentFile, "output.jar")

    val outputPath: String get() = outputJar().absolutePath

    fun bongkar() = start { log -> SmaliEngine.disassembleProject(app, project, log) }

    fun susunUlang() = start { log -> SmaliEngine.assembleProject(app, project, log) }

    private fun start(task: suspend ((String) -> Unit) -> Unit) {
        if (busy) return
        busy = true
        lines = emptyList()
        viewModelScope.launch {
            val add: (String) -> Unit = { line -> lines = (lines + line).takeLast(10) }
            try {
                task(add)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                add("GAGAL: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                hasSmali = SmaliEngine.hasSmali(app, project)
                outputAda = outputJar().exists()
                busy = false
            }
        }
    }
}
