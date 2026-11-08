package ru.sproshchaev.livejvmmonitor

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

/**
 * Держит сессии наблюдения проекта. Вкладка в окне инструментов появляется только после первого
 * подключения к JVM: запуски npm, shell и прочего без JVM окно не засоряют.
 */
@Service(Service.Level.PROJECT)
class MonitorService(private val project: Project) : Disposable {

    fun startSession(title: String, pid: Long, handler: ProcessHandler) {
        val session = MonitorSession(title, pid) { connected ->
            ApplicationManager.getApplication().invokeLater(
                { MonitorToolWindow.addSession(project, connected) },
                project.disposed,
            )
        }
        Disposer.register(this, session)
        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                session.processTerminated()
                if (!session.shown) {
                    Disposer.dispose(session)
                }
            }
        })
        session.start()
    }

    override fun dispose() = Unit
}
