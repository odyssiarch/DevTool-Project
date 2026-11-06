package com.devtool.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devtool.app.core.SmaliFiles
import com.devtool.app.core.formatSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * State dan aksi Editor Smali: jendela terbuka atau tidak, folder yang sedang dilihat,
 * pencarian, dan isi berkas yang sedang diedit. Semua akses berkas jalan di Dispatchers.IO.
 * Karena ada di ViewModel, teks yang belum disimpan tidak hilang waktu layar diputar.
 */
class SmaliEditorViewModel(val root: File) : ViewModel() {

    // ---- jendela ----
    var visible by mutableStateOf(false)
        private set

    // ---- penjelajah ----
    var dir by mutableStateOf(root)
        private set
    var entries by mutableStateOf<List<File>>(emptyList())
        private set
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<File>?>(null)
        private set
    var searching by mutableStateOf(false)
        private set

    // ---- editor satu berkas ----
    var openFile by mutableStateOf<File?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    /** Alasan berkas tidak bisa diedit (terlalu besar atau gagal dibaca). Null = bisa diedit. */
    var blocked by mutableStateOf<String?>(null)
        private set
    var original by mutableStateOf("")
        private set
    var text by mutableStateOf(TextFieldValue(""))
        private set
    var msg by mutableStateOf<String?>(null)
        private set
    var hasBackup by mutableStateOf(false)
        private set
    var confirmDiscard by mutableStateOf(false)
        private set

    val dirty: Boolean get() = loaded && blocked == null && text.text != original

    private class Loaded(val size: Long, val hasBackup: Boolean, val content: String?)

    private var searchJob: Job? = null
    private var saving = false

    // ---------- jendela ----------

    fun open() {
        visible = true
        loadEntries()
    }

    fun close() {
        searchJob?.cancel()
        visible = false
        dir = root
        query = ""
        results = null
        searching = false
        resetFile()
    }

    /** Tombol kembali: tutup berkas, naik satu folder, atau tutup jendela. */
    fun back() {
        when {
            openFile != null -> if (dirty) confirmDiscard = true else closeFile()
            dir != root -> goDir(dir.parentFile ?: root)
            else -> close()
        }
    }

    // ---------- penjelajah ----------

    fun goDir(d: File) {
        dir = d
        loadEntries()
    }

    private fun loadEntries() {
        val d = dir
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { SmaliFiles.list(d) }
            if (dir == d) entries = list
        }
    }

    fun onQueryChange(q: String) {
        query = q
        searchJob?.cancel()
        val s = q.trim()
        if (s.length < 2) {
            results = null
            searching = false
            return
        }
        searching = true
        searchJob = viewModelScope.launch {
            delay(300)
            val found = withContext(Dispatchers.IO) { SmaliFiles.search(root, s) }
            results = found
            searching = false
        }
    }

    // ---------- editor ----------

    fun openEditor(file: File) {
        goDir(file.parentFile ?: root)
        resetFile()
        openFile = file
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val len = file.length()
                    val backup = SmaliFiles.backupOf(root, file).exists()
                    Loaded(len, backup, if (len > SmaliFiles.MAX_BYTES) null else SmaliFiles.read(file))
                }
                if (openFile != file) return@launch
                hasBackup = info.hasBackup
                val content = info.content
                if (content == null) {
                    blocked = "Berkas ini ${formatSize(info.size)}, terlalu besar untuk editor di aplikasi (batas ${formatSize(SmaliFiles.MAX_BYTES)})."
                } else {
                    original = content
                    text = TextFieldValue(content)
                }
                loaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (openFile == file) {
                    blocked = "Gagal membuka berkas: ${e.message ?: e.javaClass.simpleName}"
                    loaded = true
                }
            }
        }
    }

    fun onTextChange(v: TextFieldValue) {
        text = v
        if (dirty) msg = null
    }

    fun save() {
        val file = openFile ?: return
        if (saving || !dirty) return
        val content = text.text
        saving = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { SmaliFiles.save(root, file, content) }
                if (openFile == file) {
                    original = content
                    hasBackup = true
                    msg = "Tersimpan"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (openFile == file) msg = "Gagal menyimpan: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                saving = false
            }
        }
    }

    fun restore() {
        val file = openFile ?: return
        viewModelScope.launch {
            try {
                val s = withContext(Dispatchers.IO) { SmaliFiles.restore(root, file) }
                if (openFile == file) {
                    original = s
                    text = TextFieldValue(s)
                    msg = "Dikembalikan ke versi asli"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (openFile == file) msg = "Gagal mengembalikan: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun discardChanges() {
        confirmDiscard = false
        closeFile()
    }

    fun cancelDiscard() {
        confirmDiscard = false
    }

    private fun closeFile() {
        resetFile()
        loadEntries() // ukuran berkas bisa berubah setelah disimpan
    }

    private fun resetFile() {
        openFile = null
        loaded = false
        blocked = null
        original = ""
        text = TextFieldValue("")
        msg = null
        hasBackup = false
        confirmDiscard = false
    }
}
