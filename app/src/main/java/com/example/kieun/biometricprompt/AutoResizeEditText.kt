package com.example.kieun.biometricprompt

import android.annotation.TargetApi
import android.content.Context
import android.content.res.Resources
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.util.SparseIntArray
import android.util.TypedValue
import android.widget.EditText


class AutoResizeEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyle: Int = 0
) : EditText(context, attrs, defStyle) {
    private val _availableSpaceRect = RectF()
    private val _textCachedSizes = SparseIntArray()
    private val _sizeTester: SizeTester
    private var _maxTextSize: Float = 0.toFloat()
    private var _spacingMult = 1.0f
    private var _spacingAdd = 0.0f
    private var _minTextSize: Float = 0.toFloat()
    private var _widthLimit: Int = 0
    private var _maxLines: Int = 0
    private var _enableSizeCache = true
    private var _initiallized = false
    private var paint: TextPaint? = null

    private interface SizeTester {
        /**
         * AutoResizeEditText
         *
         * @param suggestedSize
         * Size of text to be tested
         * @param availableSpace
         * available space in which text must fit
         * @return an integer < 0 if after applying `suggestedSize` to
         * text, it takes less space than `availableSpace`, > 0
         * otherwise
         */
        fun onTestSize(suggestedSize: Int, availableSpace: RectF): Int
    }

    init {
        // using the minimal recommended font size
        _minTextSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f, resources.displayMetrics
        )
        _maxTextSize = textSize
        if (_maxLines == 0)
        // no value was assigned during construction
            _maxLines = NO_LINE_LIMIT
        // prepare size tester:
        _sizeTester = object : SizeTester {
            internal val textRect = RectF()

            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            override fun onTestSize(
                suggestedSize: Int,
                availableSPace: RectF
            ): Int {
                paint!!.textSize = suggestedSize.toFloat()
                val text = text.toString()
                val singleline = maxLines == 1
                if (singleline) {
                    textRect.bottom = paint!!.fontSpacing
                    textRect.right = paint!!.measureText(text)
                } else {
                    val layout = StaticLayout(
                        text, paint,
                        _widthLimit, Layout.Alignment.ALIGN_NORMAL, _spacingMult,
                        _spacingAdd, true
                    )
                    // return early if we have more lines
                    Log.d("NLN", "Current Lines = " + Integer.toString(layout.lineCount))
                    Log.d("NLN", "Max Lines = " + Integer.toString(maxLines))
                    if (maxLines != NO_LINE_LIMIT && layout.lineCount > maxLines)
                        return 1
                    textRect.bottom = layout.height.toFloat()
                    var maxWidth = -1
                    for (i in 0 until layout.lineCount)
                        if (maxWidth < layout.getLineWidth(i))
                            maxWidth = layout.getLineWidth(i).toInt()
                    textRect.right = maxWidth.toFloat()
                }
                textRect.offsetTo(0f, 0f)
                return if (availableSPace.contains(textRect)) -1 else 1
                // else, too big
            }
        }
        _initiallized = true
    }

    override fun setTypeface(tf: Typeface?) {
        if (paint == null)
            paint = TextPaint(getPaint())
        paint!!.typeface = tf
        super.setTypeface(tf)
    }

    override fun setTextSize(size: Float) {
        _maxTextSize = size
        _textCachedSizes.clear()
        adjustTextSize()
    }

    override fun setMaxLines(maxlines: Int) {
        super.setMaxLines(maxlines)
        _maxLines = maxlines
        reAdjust()
    }

    override fun getMaxLines(): Int {
        return _maxLines
    }

    override fun setSingleLine() {
        super.setSingleLine()
        _maxLines = 1
        reAdjust()
    }

    override fun setSingleLine(singleLine: Boolean) {
        super.setSingleLine(singleLine)
        if (singleLine)
            _maxLines = 1
        else
            _maxLines = NO_LINE_LIMIT
        reAdjust()
    }

    override fun setLines(lines: Int) {
        super.setLines(lines)
        _maxLines = lines
        reAdjust()
    }

    override fun setTextSize(unit: Int, size: Float) {
        val c = context
        val r: Resources
        r = if (c == null)
            Resources.getSystem()
        else
            c.resources
        _maxTextSize = TypedValue.applyDimension(
            unit, size,
            r.getDisplayMetrics()
        )
        _textCachedSizes.clear()
        adjustTextSize()
    }

    override fun setLineSpacing(add: Float, mult: Float) {
        super.setLineSpacing(add, mult)
        _spacingMult = mult
        _spacingAdd = add
    }

    /**
     * Set the lower text size limit and invalidate the view
     *
     * @param
     */
    fun setMinTextSize(minTextSize: Float) {
        _minTextSize = minTextSize
        reAdjust()
    }

    private fun reAdjust() {
        adjustTextSize()
    }

    private fun adjustTextSize() {
        if (!_initiallized)
            return
        val startSize = _minTextSize.toInt()
        val heightLimit = (measuredHeight
                - compoundPaddingBottom - compoundPaddingTop)
        _widthLimit = (measuredWidth - compoundPaddingLeft
                - compoundPaddingRight)
        if (_widthLimit <= 0)
            return
        _availableSpaceRect.right = _widthLimit.toFloat()
        _availableSpaceRect.bottom = heightLimit.toFloat()
        super.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            efficientTextSizeSearch(
                startSize, _maxTextSize.toInt(),
                _sizeTester, _availableSpaceRect
            ).toFloat()
        )
    }

    /**
     * Enables or disables size caching, enabling it will improve performance
     * where you are animating a value inside TextView. This stores the font
     * size against getText().length() Be careful though while enabling it as 0
     * takes more space than 1 on some fonts and so on.
     *
     * @param enable
     * enable font size caching
     */
    fun setEnableSizeCache(enable: Boolean) {
        _enableSizeCache = enable
        _textCachedSizes.clear()
        adjustTextSize()
    }

    private fun efficientTextSizeSearch(
        start: Int, end: Int,
        sizeTester: SizeTester, availableSpace: RectF
    ): Int {
        if (!_enableSizeCache)
            return binarySearch(start, end, sizeTester, availableSpace)
        val text = text.toString()
        val key = text?.length ?: 0
        var size = _textCachedSizes.get(key)
        if (size != 0)
            return size
        size = binarySearch(start, end, sizeTester, availableSpace)
        _textCachedSizes.put(key, size)
        return size
    }

    private fun binarySearch(
        start: Int, end: Int,
        sizeTester: SizeTester, availableSpace: RectF
    ): Int {
        var lastBest = start
        var lo = start
        var hi = end - 1
        var mid = 0
        while (lo <= hi) {
            mid = (lo + hi).ushr(1)
            val midValCmp = sizeTester.onTestSize(mid, availableSpace)
            if (midValCmp < 0) {
                lastBest = lo
                lo = mid + 1
            } else if (midValCmp > 0) {
                hi = mid - 1
                lastBest = hi
            } else
                return mid
        }
        // make sure to return last best
        // this is what should always be returned
        return lastBest
    }

    override fun onTextChanged(
        text: CharSequence, start: Int,
        before: Int, after: Int
    ) {
        super.onTextChanged(text, start, before, after)
        reAdjust()
    }

    override fun onSizeChanged(
        width: Int, height: Int,
        oldwidth: Int, oldheight: Int
    ) {
        _textCachedSizes.clear()
        super.onSizeChanged(width, height, oldwidth, oldheight)
        if (width != oldwidth || height != oldheight)
            reAdjust()
    }

    companion object {
        private val NO_LINE_LIMIT = -1
    }
}