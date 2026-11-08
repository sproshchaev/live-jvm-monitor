# live-jvm-monitor

**Сергей Прощаев**

[![Website](https://img.shields.io/badge/prosoft.pages.dev-blue?logo=googlechrome&logoColor=white)](https://prosoft.pages.dev)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-sproshchaev-0A66C2)](https://www.linkedin.com/in/sproshchaev/)
[![Habr](https://img.shields.io/badge/Habr-sproshchaev-65A3BE?logo=habr&logoColor=white)](https://habr.com/ru/users/sproshchaev/)

---

English | [Русский](README.ru.md)

An IntelliJ IDEA plugin that draws live CPU, heap and thread charts for the Java process you start from a run configuration. I wrote it to keep the live chart from the Ultimate profiler widget after my subscription ends. It needs no subscription.

## 1. Requirements

- IntelliJ IDEA 2025.2 or newer (build 252+), Community, free mode or Ultimate. Tested on 2026.2.3.
- JDK 21 to build the plugin. The IDE runtime (JBR) already ships `jdk.attach` and `java.management`.

## 2. Quick start

Build the plugin zip:

```bash
./gradlew buildPlugin
```

The zip lands in `build/distributions/`. Install it in the IDE: Settings, Plugins, the gear icon, Install Plugin from Disk. Restart the IDE.

Run any Java or Spring Boot application. A **JVM Monitor** tab appears in the bottom tool window area once the plugin connects to the process.

To try the plugin in a sandbox IDE instead:

```bash
./gradlew runIde
```

The build compiles against a locally installed IDE. Set its path in `localIdePath` in `gradle.properties`.

## 3. How it works

1. `MonitorRunListener` catches every run start and takes the process PID.
2. Once a second `MonitorSession` looks for the application JVM. It picks the newest child JVM (the case where Maven starts the app), otherwise the process itself two seconds after start. The connection goes through the Attach API, `startLocalManagementAgent()` and JMX.
3. Each sample reads `processCpuLoad` (share of all machine cores), used and committed heap, and the live thread count. The chart keeps the last 120 seconds.
4. The tab is created after the first successful connection. Runs without a JVM leave no tab.

## 4. Limitations

- The Gradle `bootRun` task runs the application from the Gradle daemon, outside the run process tree, so the plugin does not find that JVM.
- CPU is the process share of all cores: one busy thread on a 10-core machine shows about 10%.

## 5. Versions

Kotlin 2.4.10 (IDE 2026.2 bundles 2.4.0), IntelliJ Platform Gradle Plugin 2.19.0, Gradle 9.8.2, `sinceBuild` 252.

## 6. License

[Apache License 2.0](LICENSE).
