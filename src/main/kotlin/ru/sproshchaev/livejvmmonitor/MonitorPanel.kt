package ru.sproshchaev.livejvmmonitor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Path2D
import javax.swing.JComponent
import javax.swing.JPanel

/** Вкладка сессии: строка состояния сверху, под ней два графика — CPU и куча. */
class MonitorPanel(private val session: MonitorSession) : JPanel(BorderLayout()) {

    private val status = JBLabel(session.status)
    private val chart = MonitorChart(session)

    init {
        status.border = JBUI.Borders.empty(4, 8)
        add(status, BorderLayout.NORTH)
        add(chart, BorderLayout.CENTER)
        session.addListener {
            ApplicationManager.getApplication().invokeLater {
                status.text = session.status
                chart.repaint()
            }
        }
    }
}

private class Series(val name: String, val color: Color, val value: (Sample) -> Double)

private class MonitorChart(private val session: MonitorSession) : JComponent() {

    init {
        preferredSize = JBUI.size(480, 240)
    }

    override fun paintComponent(g0: Graphics) {
        val g = g0.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = UIUtil.getPanelBackground()
            g.fillRect(0, 0, width, height)

            val data = session.snapshot()
            val last = data.lastOrNull()
            val gap = JBUI.scale(8)
            val boxHeight = (height - gap * 3) / 2
            val boxWidth = width - gap * 2
            if (boxHeight <= 0 || boxWidth <= 0) return

            val cpuTitle = last?.takeIf { it.cpu >= 0 }?.let { "CPU %.1f%%".format(it.cpu * 100) } ?: "CPU"
            // Шкала CPU подстраивается под нагрузку: при доле процента линия не лежит на дне.
            val cpuPeak = data.maxOfOrNull { if (it.cpu < 0) 0.0 else it.cpu * 100 } ?: 0.0
            val cpuTop = CPU_SCALES.firstOrNull { it >= cpuPeak * HEADROOM } ?: 100.0
            paintBox(
                g, Rectangle(gap, gap, boxWidth, boxHeight), cpuTitle, data, cpuTop, "%",
                listOf(Series("process", CPU_COLOR) { if (it.cpu < 0) Double.NaN else it.cpu * 100 }),
            )

            val heapPeak = data.maxOfOrNull { it.heapCommitted }?.toDouble()?.div(MB) ?: 0.0
            val heapTop = (heapPeak * HEADROOM).coerceAtLeast(1.0)
            val heapTitle = last?.let {
                "Heap %d / %d MB · threads %d".format(it.heapUsed / MB_LONG, it.heapCommitted / MB_LONG, it.threads)
            } ?: "Heap"
            paintBox(
                g, Rectangle(gap, gap * 2 + boxHeight, boxWidth, boxHeight), heapTitle, data, heapTop, "MB",
                listOf(
                    Series("committed", COMMITTED_COLOR) { it.heapCommitted / MB },
                    Series("used", USED_COLOR) { it.heapUsed / MB },
                ),
            )
        } finally {
            g.dispose()
        }
    }

    private fun paintBox(
        g: Graphics2D,
        box: Rectangle,
        title: String,
        data: List<Sample>,
        top: Double,
        unit: String,
        series: List<Series>,
    ) {
        g.font = UIUtil.getLabelFont()
        val metrics = g.fontMetrics
        val pad = JBUI.scale(6)
        val titleBaseline = box.y + metrics.ascent + JBUI.scale(4)
        // Под заголовком своя полоса: линии графика на текст не заходят.
        val plot = Rectangle(
            box.x, box.y + metrics.height + JBUI.scale(8),
            box.width, box.height - metrics.height - JBUI.scale(12),
        )

        g.color = JBColor.border()
        g.stroke = BasicStroke(JBUI.scale(1).toFloat())
        g.drawRect(box.x, box.y, box.width, box.height)
        if (plot.height <= 0) return
        for (i in 0..3) {
            val y = plot.y + plot.height * i / 4
            g.drawLine(box.x + 1, y, box.x + box.width - 1, y)
        }

        val step = plot.width.toDouble() / (MonitorSession.HISTORY - 1)
        val offset = MonitorSession.HISTORY - data.size
        g.stroke = BasicStroke(JBUI.scale(2).toFloat())
        for (s in series) {
            val path = Path2D.Double()
            var started = false
            data.forEachIndexed { index, sample ->
                val value = s.value(sample)
                if (value.isNaN()) {
                    started = false
                    return@forEachIndexed
                }
                val x = plot.x + (offset + index) * step
                val y = plot.y + plot.height - (value / top).coerceIn(0.0, 1.0) * plot.height
                if (started) path.lineTo(x, y) else path.moveTo(x, y)
                started = true
            }
            g.color = s.color
            g.draw(path)
        }

        g.color = UIUtil.getContextHelpForeground()
        val scale = if (top >= 10) "%.0f %s".format(top, unit) else "%.1f %s".format(top, unit)
        g.drawString(scale, plot.x + pad, plot.y + metrics.ascent + JBUI.scale(2))

        g.color = UIUtil.getLabelForeground()
        g.drawString(title, box.x + pad, titleBaseline)
        var legendX = box.x + box.width - pad
        for (s in series.asReversed()) {
            legendX -= metrics.stringWidth(s.name)
            g.color = s.color
            g.drawString(s.name, legendX, titleBaseline)
            legendX -= JBUI.scale(10)
        }
    }

    private companion object {
        const val MB = 1024.0 * 1024.0
        const val MB_LONG = 1024L * 1024L
        val CPU_COLOR = JBColor(Color(0x3574F0), Color(0x548AF7))
        val USED_COLOR = JBColor(Color(0xE55765), Color(0xF07C87))
        val COMMITTED_COLOR = JBColor(Color(0x5FB865), Color(0x73BD79))

        /** Запас над пиком, чтобы линия не лежала на верхней границе. */
        const val HEADROOM = 1.15

        /** Ступени шкалы CPU в процентах. */
        val CPU_SCALES = listOf(1.0, 2.0, 5.0, 10.0, 25.0, 50.0, 100.0)
    }
}
