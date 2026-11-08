package ru.sproshchaev.livejvmmonitor

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** Каждый запуск с локальным процессом передается в [MonitorService]; не-JVM процессы отсеются там. */
class MonitorRunListener(private val project: Project) : ExecutionListener {

    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        val process = (handler as? BaseProcessHandler<*>)?.process ?: return
        val pid = runCatching { process.pid() }.getOrNull() ?: return
        project.service<MonitorService>().startSession(env.runProfile.name, pid, handler)
    }
}
