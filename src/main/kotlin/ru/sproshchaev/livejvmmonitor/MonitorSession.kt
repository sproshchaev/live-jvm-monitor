package ru.sproshchaev.livejvmmonitor

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.AppExecutorUtil
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Наблюдение за одним запуском. Раз в секунду выбирает JVM запуска, подключается к ней и снимает замер.
 *
 * Какая JVM считается JVM запуска: самая поздняя из дочерних JVM процесса, а если дочерних нет —
 * сам процесс, но не раньше [ROOT_GRACE_MS] после старта. Так приложение, которое Maven запускает
 * отдельным процессом (spring-boot:run), вытесняет JVM самого Maven, когда появляется.
 */
class MonitorSession(
    val title: String,
    private val rootPid: Long,
    private val onFirstConnect: (MonitorSession) -> Unit,
) : Disposable {

    private val startedAt = System.currentTimeMillis()
    private val samples = ArrayDeque<Sample>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    private var future: ScheduledFuture<*>? = null
    private var connection: JvmConnection? = null
    private var everConnected = false
    private var failedAttempts = 0

    @Volatile var status: String = "Waiting for JVM…"
        private set

    @Volatile var finished = false
        private set

    /** Вкладка сессии добавлена в окно инструментов. */
    @Volatile var shown = false

    @Volatile var disposed = false
        private set

    fun start() {
        future = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(::tick, 500, PERIOD_MS, TimeUnit.MILLISECONDS)
    }

    fun snapshot(): List<Sample> = synchronized(samples) { samples.toList() }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    /** Процесс запуска завершился: замеры прекращаются, история остается на графике. */
    @Synchronized
    fun processTerminated() {
        stopPolling()
        finished = true
        status = connection?.let { "PID ${it.pid} · process finished" } ?: "Process finished"
        closeConnection()
        fire()
    }

    @Synchronized
    override fun dispose() {
        disposed = true
        stopPolling()
        closeConnection()
        listeners.clear()
    }

    @Synchronized
    private fun tick() {
        if (finished) return
        try {
            val target = chooseTarget()
            if (target != null && target != connection?.pid) {
                connect(target)
            }
            val current = connection ?: return
            val sample = current.sample()
            synchronized(samples) {
                samples.addLast(sample)
                while (samples.size > HISTORY) samples.removeFirst()
            }
            status = "PID ${current.pid} · connected"
            fire()
        } catch (e: Exception) {
            closeConnection()
            failedAttempts++
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                stopPolling()
                status = "Cannot connect: ${e.message ?: e.javaClass.simpleName}"
            } else {
                status = "Connecting… (attempt $failedAttempts)"
            }
            fire()
        }
    }

    private fun chooseTarget(): Long? {
        val root = ProcessHandle.of(rootPid).orElse(null) ?: return null
        val jvms = JvmConnection.attachablePids()
        val newestChild = root.descendants()
            .filter { it.pid() in jvms }
            .toList()
            .maxByOrNull { it.info().startInstant().orElse(Instant.EPOCH) }
        if (newestChild != null) return newestChild.pid()
        val rootIsReady = System.currentTimeMillis() - startedAt >= ROOT_GRACE_MS
        return if (rootIsReady && rootPid in jvms) rootPid else null
    }

    private fun connect(pid: Long) {
        closeConnection()
        synchronized(samples) { samples.clear() }
        connection = JvmConnection.open(pid)
        failedAttempts = 0
        if (!everConnected) {
            everConnected = true
            onFirstConnect(this)
        }
    }

    private fun closeConnection() {
        connection?.close()
        connection = null
    }

    private fun stopPolling() {
        future?.cancel(false)
        future = null
    }

    private fun fire() {
        listeners.forEach { it() }
    }

    companion object {
        const val HISTORY = 120
        const val PERIOD_MS = 1000L
        private const val ROOT_GRACE_MS = 2000L
        private const val MAX_FAILED_ATTEMPTS = 30
    }
}
