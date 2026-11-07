package com.devtool.app.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import com.devtool.app.ui.screens.styleSmali

private val C_BG = 0xFF1F1E1D.toInt()
private val C_TEXT = 0xFFFAF9F5.toInt()
private val C_MUTED = 0xFF8A8880.toInt()
private val C_LINE = 0xFF403E3B.toInt()
private val C_MATCH = 0x55FFCB6B
private val C_MATCH_CUR = 0xAAFFB067.toInt()

private interface Syn
private interface Mt

private class SynColor(c: Int) : ForegroundColorSpan(c), Syn
private class SynItalic : StyleSpan(Typeface.ITALIC), Syn
private class MtSpan(c: Int) : BackgroundColorSpan(c), Mt
private class Snap(val text: String, val sel: Int)

/**
 * Editor kode native Android (EditText di dalam ScrollView) untuk Smali.
 * Ringan karena: pewarnaan sintaks hanya untuk baris yang terlihat (plus margin), tanpa
 * pengukuran ulang seluruh teks lewat Compose, dan nomor baris digambar langsung.
 */
class CodeEditorView(ctx: Context, private val firstLine: Int, initial: String) : FrameLayout(ctx) {
    var onDirty: ((Boolean) -> Unit)? = null
    var onHistory: ((Boolean, Boolean) -> Unit)? = null
    var onMatches: ((Int, Int) -> Unit)? = null

    private val dp = ctx.resources.displayMetrics.density
    private val vScroll = ScrollView(ctx)
    private val hScroll = HorizontalScrollView(ctx)

    /** EditText yang menggambar nomor baris tetap di kiri (tidak ikut geser horizontal). */
    private inner class Gutter(c: Context) : EditText(c) {
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            color = C_MUTED
            textAlign = Paint.Align.RIGHT
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, c.resources.displayMetrics)
        }
        private val bgPaint = Paint().apply { color = C_BG }
        private val linePaint = Paint().apply { color = C_LINE }
        var gutterW = 0

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val lay = layout ?: return
            val sx = hScroll.scrollX.toFloat()
            canvas.drawRect(sx, 0f, sx + gutterW, height.toFloat(), bgPaint)
            canvas.drawRect(sx + gutterW - 1f, 0f, sx + gutterW, height.toFloat(), linePaint)
            val clip = canvas.clipBounds
            val a = lay.getLineForVertical((clip.top - totalPaddingTop).coerceAtLeast(0))
            val b = lay.getLineForVertical((clip.bottom - totalPaddingTop).coerceAtLeast(0))
            val x = sx + gutterW - 8f * dp
            for (i in a..b) {
                canvas.drawText(
                    (firstLine + i).toString(),
                    x,
                    (lay.getLineBaseline(i) + totalPaddingTop).toFloat(),
                    numPaint,
                )
            }
        }
    }

    private val edit = Gutter(ctx)

    private val undoStack = ArrayList<Snap>()
    private val redoStack = ArrayList<Snap>()
    private var original = initial
    private var prevText = initial
    private var prevSel = 0
    private var lastPush = 0L
    private var suppress = false
    private var lastDirty = false
    private var lastCanUndo = false
    private var lastCanRedo = false

    private var hlFrom = 0
    private var hlTo = -1
    private val hlRunnable = Runnable { rehighlight() }
    private val matchRunnable = Runnable { recomputeMatches(false) }

    private val matches = ArrayList<Int>()
    private var cur = 0
    private var findQ = ""
    private var findCase = false
    private var findOn = false

    init {
        setBackgroundColor(C_BG)

        edit.setText(initial)
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        edit.typeface = Typeface.MONOSPACE
        edit.setTextColor(C_TEXT)
        edit.background = null
        edit.gravity = Gravity.TOP or Gravity.START
        edit.setLineSpacing(0f, 1.15f)
        edit.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        edit.setHorizontallyScrolling(true)
        edit.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        edit.highlightColor = 0x66D97757
        edit.minWidth = ctx.resources.displayMetrics.widthPixels
        edit.minHeight = (ctx.resources.displayMetrics.heightPixels * 0.7f).toInt()

        hScroll.isFillViewport = true
        hScroll.overScrollMode = View.OVER_SCROLL_NEVER
        hScroll.isHorizontalScrollBarEnabled = false
        vScroll.isFillViewport = true
        vScroll.overScrollMode = View.OVER_SCROLL_NEVER

        hScroll.addView(edit, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        vScroll.addView(hScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(vScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        updateGutter()

        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!suppress && s != null) onUserEdited(s)
            }
        })

        vScroll.setOnScrollChangeListener { _, _, _, _, _ -> maybeRehighlight() }
        hScroll.setOnScrollChangeListener { _, _, _, _, _ -> edit.invalidate() }
        vScroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> scheduleHighlight(60) }
    }

    // ---------- API untuk Compose ----------
    fun text(): String = edit.text.toString()

    fun markSaved(content: String) {
        original = content
        notifyState(edit.text.toString())
    }

    /** Ganti seluruh isi (mis. "kembalikan ke asli"); riwayat undo dikosongkan. */
    fun replaceAll(s: String) {
        original = s
        undoStack.clear()
        redoStack.clear()
        applyText(s, 0)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val snap = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(Snap(prevText, edit.selectionStart.coerceAtLeast(0)))
        applyText(snap.text, snap.sel)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val snap = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(Snap(prevText, edit.selectionStart.coerceAtLeast(0)))
        applyText(snap.text, snap.sel)
    }

    fun insert(s: String) {
        val t = edit.text
        val a = minOf(edit.selectionStart, edit.selectionEnd).coerceAtLeast(0)
        val b = maxOf(edit.selectionStart, edit.selectionEnd).coerceAtLeast(a)
        t.replace(a, b, s)
        edit.setSelection((a + s.length).coerceAtMost(t.length))
    }

    fun setFind(q: String, caseSensitive: Boolean, on: Boolean) {
        val changed = q != findQ || caseSensitive != findCase || on != findOn
        findQ = q
        findCase = caseSensitive
        findOn = on
        if (changed) recomputeMatches(true)
    }

    fun next() = goTo(cur + 1)

    fun prev() = goTo(cur - 1)

    // ---------- perubahan teks ----------
    private fun onUserEdited(s: Editable) {
        val now = System.currentTimeMillis()
        val t = s.toString()
        if (t == prevText) return
        if (undoStack.isEmpty() || now - lastPush > 700) {
            undoStack.add(Snap(prevText, prevSel))
            if (undoStack.size > 60) undoStack.removeAt(0)
        }
        lastPush = now
        redoStack.clear()
        prevText = t
        prevSel = edit.selectionStart.coerceAtLeast(0)
        notifyState(t)
        updateGutter()
        scheduleHighlight(90)
        if (findOn && findQ.isNotEmpty()) {
            edit.removeCallbacks(matchRunnable)
            edit.postDelayed(matchRunnable, 250)
        }
    }

    private fun applyText(t: String, sel: Int) {
        suppress = true
        edit.setText(t)
        edit.setSelection(sel.coerceIn(0, t.length))
        suppress = false
        prevText = t
        prevSel = sel
        lastPush = 0L
        notifyState(t)
        updateGutter()
        hlTo = -1
        scheduleHighlight(10)
        if (findOn && findQ.isNotEmpty()) recomputeMatches(false)
    }

    private fun notifyState(t: String) {
        val d = t != original
        if (d != lastDirty) {
            lastDirty = d
            onDirty?.invoke(d)
        }
        val u = undoStack.isNotEmpty()
        val r = redoStack.isNotEmpty()
        if (u != lastCanUndo || r != lastCanRedo) {
            lastCanUndo = u
            lastCanRedo = r
            onHistory?.invoke(u, r)
        }
    }

    private fun updateGutter() {
        val lines = prevText.count { it == '\n' } + 1
        val digits = (firstLine + lines - 1).toString().length.coerceAtLeast(2)
        val w = (edit.numPaint.measureText("0") * digits + 14 * dp).toInt()
        if (w != edit.gutterW) {
            edit.gutterW = w
            edit.setPadding(w + (6 * dp).toInt(), (8 * dp).toInt(), (48 * dp).toInt(), (120 * dp).toInt())
        }
    }

    // ---------- pewarnaan sintaks (hanya area terlihat) ----------
    private fun scheduleHighlight(delayMs: Long) {
        edit.removeCallbacks(hlRunnable)
        edit.postDelayed(hlRunnable, delayMs)
    }

    private fun maybeRehighlight() {
        val lay = edit.layout ?: return
        val h = vScroll.height
        if (h <= 0 || lay.lineCount == 0) return
        val top = (vScroll.scrollY - edit.totalPaddingTop).coerceAtLeast(0)
        val first = lay.getLineForVertical(top)
        val last = lay.getLineForVertical(top + h)
        val needTop = hlFrom > 0 && first < hlFrom + 8
        val needBottom = hlTo < lay.lineCount - 1 && last > hlTo - 8
        if (hlTo < 0 || needTop || needBottom) scheduleHighlight(24)
    }

    private fun rehighlight() {
        val lay = edit.layout ?: return
        val h = vScroll.height
        if (h <= 0 || lay.lineCount == 0) return
        val top = (vScroll.scrollY - edit.totalPaddingTop).coerceAtLeast(0)
        val first = lay.getLineForVertical(top)
        val last = lay.getLineForVertical(top + h)
        highlightRange(first - 70, last + 70)
    }

    private fun highlightRange(fromLine: Int, toLine: Int) {
        try {
            val lay = edit.layout ?: return
            val lc = lay.lineCount
            if (lc == 0) return
            val a = fromLine.coerceIn(0, lc - 1)
            val b = toLine.coerceIn(a, lc - 1)
            val ed = edit.text
            for (sp in ed.getSpans(0, ed.length, Syn::class.java)) ed.removeSpan(sp)
            val start = lay.getLineStart(a)
            val end = lay.getLineEnd(b).coerceAtMost(ed.length)
            if (start >= end || start > ed.length) return
            val src = ed.subSequence(start, end).toString()
            val ann = buildAnnotatedString {
                append(src)
                styleSmali(src, this)
            }
            for (r in ann.spanStyles) {
                val st = r.item
                if (st.color.isSpecified) {
                    ed.setSpan(SynColor(st.color.toArgb()), start + r.start, start + r.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (st.fontStyle == FontStyle.Italic) {
                    ed.setSpan(SynItalic(), start + r.start, start + r.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            hlFrom = a
            hlTo = b
        } catch (_: Exception) {
        }
    }

    // ---------- pencarian ----------
    private fun recomputeMatches(jump: Boolean) {
        matches.clear()
        if (findOn && findQ.isNotEmpty()) {
            val src = edit.text.toString()
            var i = src.indexOf(findQ, 0, !findCase)
            while (i >= 0 && matches.size < 5000) {
                matches.add(i)
                i = src.indexOf(findQ, i + findQ.length, !findCase)
            }
        }
        cur = if (jump) 0 else cur.coerceIn(0, maxOf(0, matches.size - 1))
        applyMatchSpans()
        onMatches?.invoke(matches.size, cur)
        if (jump && matches.isNotEmpty()) goTo(0)
    }

    private fun applyMatchSpans() {
        val ed = edit.text
        for (sp in ed.getSpans(0, ed.length, Mt::class.java)) ed.removeSpan(sp)
        if (matches.isEmpty()) return
        val ql = findQ.length
        val lim = minOf(matches.size, 1500)
        for (k in 0 until lim) {
            val s = matches[k]
            val e = minOf(s + ql, ed.length)
            if (s < e) ed.setSpan(MtSpan(if (k == cur) C_MATCH_CUR else C_MATCH), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (cur >= lim) {
            val s = matches[cur]
            val e = minOf(s + ql, ed.length)
            if (s < e) ed.setSpan(MtSpan(C_MATCH_CUR), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun goTo(idx: Int) {
        if (matches.isEmpty()) return
        cur = ((idx % matches.size) + matches.size) % matches.size
        applyMatchSpans()
        onMatches?.invoke(matches.size, cur)
        val s = matches[cur].coerceAtMost(edit.text.length)
        edit.setSelection(s, minOf(s + findQ.length, edit.text.length))
        scrollToOffset(s)
    }

    private fun scrollToOffset(off: Int) {
        whenLaidOut {
            val lay = edit.layout ?: return@whenLaidOut
            val o = off.coerceIn(0, edit.text.length)
            val line = lay.getLineForOffset(o)
            val y = lay.getLineTop(line) + edit.totalPaddingTop
            vScroll.smoothScrollTo(0, (y - edit.lineHeight * 4).coerceAtLeast(0))
            val x = (lay.getPrimaryHorizontal(o) + edit.totalPaddingLeft).toInt()
            val sx = hScroll.scrollX
            if (x < sx + edit.gutterW + 24 || x > sx + hScroll.width - 80) {
                hScroll.smoothScrollTo((x - hScroll.width / 3).coerceAtLeast(0), 0)
            }
        }
    }

    private fun whenLaidOut(block: () -> Unit) {
        if (vScroll.height > 0 && edit.layout != null) {
            block()
            return
        }
        val l = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?, left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int,
            ) {
                if (vScroll.height > 0 && edit.layout != null) {
                    vScroll.removeOnLayoutChangeListener(this)
                    block()
                }
            }
        }
        vScroll.addOnLayoutChangeListener(l)
    }

    override fun onDetachedFromWindow() {
        edit.removeCallbacks(hlRunnable)
        edit.removeCallbacks(matchRunnable)
        super.onDetachedFromWindow()
    }
}
