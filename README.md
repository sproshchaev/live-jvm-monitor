# Live JVM Monitor

**Сергей Прощаев**

[![Website](https://img.shields.io/badge/prosoft.pages.dev-blue?logo=googlechrome&logoColor=white)](https://prosoft.pages.dev)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-sproshchaev-0A66C2)](https://www.linkedin.com/in/sproshchaev/)
[![Habr](https://img.shields.io/badge/Habr-sproshchaev-65A3BE?logo=habr&logoColor=white)](https://habr.com/ru/users/sproshchaev/)

---

Плагин IntelliJ IDEA: живые графики CPU, кучи и числа потоков для Java-процесса, запущенного из конфигурации запуска. Замена виджета профилировщика Ultimate в окне запуска, работает без подписки.

## Как работает

1. `MonitorRunListener` ловит старт любого запуска и берет PID процесса.
2. `MonitorSession` раз в секунду ищет JVM запуска: самую позднюю дочернюю JVM (приложение, которое запустил Maven), иначе сам процесс через 2 секунды после старта. Подключение — Attach API, `startLocalManagementAgent()`, затем JMX.
3. Замер: `processCpuLoad` (доля от всех ядер машины), занятая и выделенная куча, число потоков. История — последние 120 секунд.
4. Вкладка в окне инструментов **JVM Monitor** появляется после первого подключения, запуски без JVM ее не создают.

Ограничение: задача Gradle `bootRun` запускает приложение из демона Gradle, а не из процесса запуска, поэтому такая JVM не находится.

## Сборка

```bash
./gradlew buildPlugin
```

Zip — в `build/distributions/`. Сборка идет против установленной IDE, путь — `localIdePath` в `gradle.properties`.

Песочница с плагином: `./gradlew runIde`.

## Версии

Kotlin 2.4.10 (в IDE 2026.2 встроен 2.4.0), IntelliJ Platform Gradle Plugin 2.19.0, Gradle 9.8.2, JDK 21 для компиляции, `sinceBuild` 252.
