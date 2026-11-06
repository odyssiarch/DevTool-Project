package com.devtool.app.ui.viewmodel

import kotlinx.coroutines.Job
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devtool.app.core.CompareResult
import com.devtool.app.core.DeepResult
import com.devtool.app.core.JarComparer
import com.devtool.app.core.JarNames
import com.devtool.app.core.JarVerifier
import com.devtool.app.core.MethodChange
import com.devtool.app.core.SmaliDiff
import com.devtool.app.core.VerifyCheck
import com.devtool.app.core.Workspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Perbandingan dua JAR. Perbandingan kode bisa makan beberapa menit, jadi hasil dan
 * prosesnya disimpan di sini supaya tidak hilang waktu layar diputar.
 * Pilihan input (JAR lama/baru, opsi, kata kunci cari) tetap di layar lewat rememberSaveable.
 */
class CompareViewModel(private val app: Context) : ViewModel() {

    var running by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var nameOld by mutableStateOf("Versi lama")
        private set
    var nameNew by mutableStateOf("Versi baru")
        private set

    var result by mutableStateOf<CompareResult?>(null)
        private set
    var checks by mutableStateOf<List<VerifyCheck>>(emptyList())
        private set
    var deep by mutableStateOf<DeepResult?>(null)
        private set

    var selClass by mutableStateOf<String?>(null)
        private set
    var selMethod by mutableStateOf<MethodChange?>(null)
        private set

    /** Nomor proses. Dinaikkan tiap reset/start supaya proses lama yang dibatalkan tidak menimpa state. */
    private var gen = 0
    private var job: Job? = null // <--- Tambah ini

    /** Batalkan proses yang berjalan dan kosongkan hasil (dipanggil saat JAR diganti). */
fun reset() {
    job?.cancel()
    job = null
    gen++
    running = false
    status = ""
    error = null
    result = null
    checks = emptyList()
    deep = null
    selClass = null
    selMethod = null
}

fun start(oldP: String, newP: String, code: Boolean, res: Boolean) {
    reset()
    val mine = gen
    val da = JarNames.display(oldP)
    val db = JarNames.display(newP)
    val na = if (da == db) "$da (lama)" else da
    val nb = if (da == db) "$db (baru)" else db
    nameOld = na
    nameNew = nb
    running = true
            job = viewModelScope.launch {
            try {
                val c = withContext(Dispatchers.IO) {
                    try {
                        JarVerifier.structure(Workspace.jarOf(app, oldP), Workspace.jarOf(app, newP), na, nb)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                if (mine != gen) return@launch
                checks = c

                val r = withContext(Dispatchers.IO) { JarComparer.compare(app, oldP, newP, code, res) }
                if (mine != gen) return@launch
                result = r

                if (code) {
                    val d = SmaliDiff.compare(app, oldP, newP, na, nb) { msg ->
                        viewModelScope.launch { if (mine == gen) status = msg }
                    }
                    if (mine != gen) return@launch
                    deep = d
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mine == gen) error = e.message ?: "Perbandingan gagal"
            } finally {
                if (mine == gen) {
                    running = false
                    status = ""
                }
            }
        }
    }

    fun selectClass(key: String?) {
        selClass = key
        if (key == null) selMethod = null
    }

    fun selectMethod(m: MethodChange?) {
        selMethod = m
    }

    /** Kembali dari detail: tutup method dulu, baru class. */
    fun backFromDetail() {
        if (selMethod != null) selMethod = null else selClass = null
    }
}