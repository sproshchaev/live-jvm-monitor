package ru.sproshchaev.livejvmmonitor

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import javax.swing.SwingConstants

class MonitorToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Окно могли открыть уже после первого запуска: тогда вкладки сессий в нем есть.
        if (toolWindow.contentManager.contentCount > 0) return
        val hint = JBLabel("Run a Java application to see live CPU and heap charts", SwingConstants.CENTER)
        val content = ContentFactory.getInstance().createContent(hint, "", false)
        content.isCloseable = false
        content.putUserData(MonitorToolWindow.PLACEHOLDER, true)
        toolWindow.contentManager.addContent(content)
    }
}

object MonitorToolWindow {
    const val ID = "JVM Monitor"

    val PLACEHOLDER = Key.create<Boolean>("LiveJvmMonitor.placeholder")
    private val SESSION = Key.create<MonitorSession>("LiveJvmMonitor.session")

    /** Добавляет вкладку сессии. Вызывается в EDT. Завершенная вкладка того же запуска заменяется новой. */
    fun addSession(project: Project, session: MonitorSession) {
        if (session.disposed) return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return
        val manager = toolWindow.contentManager
        manager.contents
            .filter { content ->
                content.getUserData(PLACEHOLDER) == true ||
                    content.getUserData(SESSION)?.let { it.title == session.title && it.finished } == true
            }
            .forEach { manager.removeContent(it, true) }

        val panel = MonitorPanel(session)
        val content = ContentFactory.getInstance().createContent(panel, session.title, false)
        content.putUserData(SESSION, session)
        content.setDisposer(Disposable { Disposer.dispose(session) })
        manager.addContent(content)
        manager.setSelectedContent(content)
        session.shown = true
        toolWindow.show()
    }
}
