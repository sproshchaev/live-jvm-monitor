package ru.sproshchaev.livejvmmonitor

import com.sun.tools.attach.VirtualMachine
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.lang.management.ThreadMXBean
import javax.management.remote.JMXConnector
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL
import com.sun.management.OperatingSystemMXBean as SunOperatingSystemMXBean

/** Один замер процесса. [cpu] — доля 0..1, отрицательное значение — JVM не отдала загрузку. */
data class Sample(
    val time: Long,
    val cpu: Double,
    val heapUsed: Long,
    val heapCommitted: Long,
    val heapMax: Long,
    val threads: Int,
)

/** Подключение к локальной JVM: Attach API включает локальный агент JMX, дальше читаются MXBean. */
class JvmConnection private constructor(
    val pid: Long,
    private val connector: JMXConnector,
    private val os: SunOperatingSystemMXBean,
    private val memory: MemoryMXBean,
    private val threads: ThreadMXBean,
) : AutoCloseable {

    fun sample(): Sample {
        val heap = memory.heapMemoryUsage
        return Sample(
            time = System.currentTimeMillis(),
            cpu = os.processCpuLoad,
            heapUsed = heap.used,
            heapCommitted = heap.committed,
            heapMax = heap.max,
            threads = threads.threadCount,
        )
    }

    override fun close() {
        runCatching { connector.close() }
    }

    companion object {
        /** PID всех JVM текущего пользователя, к которым можно подключиться. */
        fun attachablePids(): Set<Long> =
            VirtualMachine.list().mapNotNull { it.id().toLongOrNull() }.toSet()

        fun open(pid: Long): JvmConnection {
            val vm = VirtualMachine.attach(pid.toString())
            val address = try {
                vm.startLocalManagementAgent()
            } finally {
                vm.detach()
            }
            val connector = JMXConnectorFactory.connect(JMXServiceURL(address))
            try {
                val server = connector.mBeanServerConnection
                return JvmConnection(
                    pid = pid,
                    connector = connector,
                    os = ManagementFactory.newPlatformMXBeanProxy(
                        server, ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME, SunOperatingSystemMXBean::class.java
                    ),
                    memory = ManagementFactory.newPlatformMXBeanProxy(
                        server, ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean::class.java
                    ),
                    threads = ManagementFactory.newPlatformMXBeanProxy(
                        server, ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean::class.java
                    ),
                )
            } catch (e: Exception) {
                runCatching { connector.close() }
                throw e
            }
        }
    }
}
