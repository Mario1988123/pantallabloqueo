package com.mario.pantallabloqueo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

class PatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Colores
    private val dotColorNormal = Color.parseColor("#4DFFFFFF")
    private val dotColorSelected = Color.parseColor("#FFFFFFFF")
    private val dotColorWrong = Color.parseColor("#FFFF4444")
    private val lineColor = Color.parseColor("#FFFFFFFF")
    private val lineColorWrong = Color.parseColor("#FFFF4444")

    // Paints
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // Dimensiones
    private var dotRadius = 20f
    private var dotRadiusSelected = 30f
    private var cellWidth = 0f
    private var cellHeight = 0f
    private var offsetX = 0f
    private var offsetY = 0f

    // Estado
    private val selectedDots = mutableListOf<Int>()
    private var currentX = 0f
    private var currentY = 0f
    private var isDrawing = false
    private var showWrongPattern = false

    // Posiciones de los 9 puntos (calculadas en onSizeChanged)
    private val dotPositions = Array(9) { PointF(0f, 0f) }

    // Callback cuando se completa el patrón
    var onPatternCompleted: ((String) -> Unit)? = null

    init {
        dotPaint.style = Paint.Style.FILL
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Calcular el tamaño de la cuadrícula (cuadrada)
        val size = minOf(w, h).toFloat()
        cellWidth = size / 3
        cellHeight = size / 3

        // Centrar la cuadrícula
        offsetX = (w - size) / 2
        offsetY = (h - size) / 2

        // Calcular posiciones de los puntos
        for (i in 0..8) {
            val row = i / 3
            val col = i % 3
            dotPositions[i] = PointF(
                offsetX + col * cellWidth + cellWidth / 2,
                offsetY + row * cellHeight + cellHeight / 2
            )
        }

        // Ajustar tamaños según el tamaño de la vista
        dotRadius = cellWidth * 0.08f
        dotRadiusSelected = cellWidth * 0.12f
        linePaint.strokeWidth = cellWidth * 0.04f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Dibujar líneas entre puntos seleccionados
        if (selectedDots.size > 1) {
            linePaint.color = if (showWrongPattern) lineColorWrong else lineColor
            val path = Path()
            val firstDot = dotPositions[selectedDots[0]]
            path.moveTo(firstDot.x, firstDot.y)

            for (i in 1 until selectedDots.size) {
                val dot = dotPositions[selectedDots[i]]
                path.lineTo(dot.x, dot.y)
            }

            // Línea hacia el dedo actual si estamos dibujando
            if (isDrawing && selectedDots.isNotEmpty()) {
                path.lineTo(currentX, currentY)
            }

            canvas.drawPath(path, linePaint)
        } else if (isDrawing && selectedDots.size == 1) {
            // Dibujar línea desde el primer punto al dedo
            linePaint.color = lineColor
            val firstDot = dotPositions[selectedDots[0]]
            canvas.drawLine(firstDot.x, firstDot.y, currentX, currentY, linePaint)
        }

        // Dibujar los 9 puntos
        for (i in 0..8) {
            val pos = dotPositions[i]
            val isSelected = selectedDots.contains(i)

            dotPaint.color = when {
                showWrongPattern && isSelected -> dotColorWrong
                isSelected -> dotColorSelected
                else -> dotColorNormal
            }

            val radius = if (isSelected) dotRadiusSelected else dotRadius
            canvas.drawCircle(pos.x, pos.y, radius, dotPaint)

            // Dibujar un círculo interior más pequeño en puntos seleccionados
            if (isSelected) {
                dotPaint.color = when {
                    showWrongPattern -> dotColorWrong
                    else -> dotColorSelected
                }
                canvas.drawCircle(pos.x, pos.y, dotRadius * 0.4f, dotPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (showWrongPattern) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDrawing = true
                selectedDots.clear()
                checkDotTouched(event.x, event.y)
                currentX = event.x
                currentY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentX = event.x
                currentY = event.y
                checkDotTouched(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDrawing = false
                if (selectedDots.size >= 4) {
                    val pattern = selectedDots.joinToString("")
                    onPatternCompleted?.invoke(pattern)
                } else {
                    // Patrón muy corto, limpiar
                    selectedDots.clear()
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun checkDotTouched(x: Float, y: Float) {
        for (i in 0..8) {
            if (selectedDots.contains(i)) continue

            val pos = dotPositions[i]
            val distance = sqrt((x - pos.x) * (x - pos.x) + (y - pos.y) * (y - pos.y))

            if (distance < cellWidth * 0.35f) {
                // Verificar si hay puntos intermedios que debemos añadir
                if (selectedDots.isNotEmpty()) {
                    val lastDot = selectedDots.last()
                    val intermediateDot = getIntermediateDot(lastDot, i)
                    if (intermediateDot != -1 && !selectedDots.contains(intermediateDot)) {
                        selectedDots.add(intermediateDot)
                    }
                }
                selectedDots.add(i)
                break
            }
        }
    }

    // Obtener punto intermedio cuando se salta un punto en diagonal o línea recta
    private fun getIntermediateDot(from: Int, to: Int): Int {
        val pairs = mapOf(
            Pair(0, 2) to 1,
            Pair(2, 0) to 1,
            Pair(3, 5) to 4,
            Pair(5, 3) to 4,
            Pair(6, 8) to 7,
            Pair(8, 6) to 7,
            Pair(0, 6) to 3,
            Pair(6, 0) to 3,
            Pair(1, 7) to 4,
            Pair(7, 1) to 4,
            Pair(2, 8) to 5,
            Pair(8, 2) to 5,
            Pair(0, 8) to 4,
            Pair(8, 0) to 4,
            Pair(2, 6) to 4,
            Pair(6, 2) to 4
        )
        return pairs[Pair(from, to)] ?: -1
    }

    fun showWrongPattern() {
        showWrongPattern = true
        invalidate()

        postDelayed({
            showWrongPattern = false
            selectedDots.clear()
            invalidate()
        }, 1500)
    }

    fun clearPattern() {
        selectedDots.clear()
        showWrongPattern = false
        invalidate()
    }

    fun getSelectedPattern(): String {
        return selectedDots.joinToString("")
    }

    fun setPattern(pattern: String) {
        selectedDots.clear()
        pattern.forEach { char ->
            val dot = char.toString().toIntOrNull()
            if (dot != null && dot in 0..8) {
                selectedDots.add(dot)
            }
        }
        invalidate()
    }
}
