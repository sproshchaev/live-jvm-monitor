# live-jvm-monitor

**Сергей Прощаев**

[![Website](https://img.shields.io/badge/prosoft.pages.dev-blue?logo=googlechrome&logoColor=white)](https://prosoft.pages.dev)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-sproshchaev-0A66C2)](https://www.linkedin.com/in/sproshchaev/)
[![Habr](https://img.shields.io/badge/Habr-sproshchaev-65A3BE?logo=habr&logoColor=white)](https://habr.com/ru/users/sproshchaev/)

---

[English](README.md) | Русский

Плагин IntelliJ IDEA: живые графики CPU, кучи и числа потоков для Java-процесса, запущенного из конфигурации запуска. Я написал его, чтобы после окончания подписки остался живой график из виджета профилировщика Ultimate. Подписка плагину не нужна.

## 1. Требования

- IntelliJ IDEA 2025.2 и новее (сборка 252+): Community, бесплатный режим или Ultimate. Проверено на 2026.2.3.
- JDK 21 для сборки плагина. В среде выполнения IDE (JBR) модули `jdk.attach` и `java.management` уже есть.

## 2. Быстрый старт

Собрать zip плагина:

```bash
./gradlew buildPlugin
```

Zip появится в `build/distributions/`. Установка в IDE: Settings, Plugins, шестеренка, Install Plugin from Disk, затем перезапуск IDE.

Запустите любое приложение на Java или Spring Boot. Вкладка **JVM Monitor** появится в нижней области окон инструментов, как только плагин подключится к процессу.

Попробовать плагин в песочнице:

```bash
./gradlew runIde
```

Сборка идет против установленной IDE, путь к ней задается в `localIdePath` в `gradle.properties`.

## 3. Как работает

1. `MonitorRunListener` ловит старт любого запуска и берет PID процесса.
2. `MonitorSession` раз в секунду ищет JVM приложения: самую позднюю дочернюю JVM (так находится приложение, которое запустил Maven), иначе сам процесс через 2 секунды после старта. Подключение идет через Attach API, `startLocalManagementAgent()` и JMX.
3. Замер: `processCpuLoad` (доля от всех ядер машины), занятая и выделенная куча, число потоков. График хранит последние 120 секунд.
4. Вкладка создается после первого подключения, запуски без JVM ее не создают.

## 4. Ограничения

- Задача Gradle `bootRun` запускает приложение из демона Gradle, вне дерева процесса запуска, поэтому такая JVM не находится.
- CPU считается долей от всех ядер: один занятый поток на машине с 10 ядрами дает около 10%.

## 5. Версии

Kotlin 2.4.10 (в IDE 2026.2 встроен 2.4.0), IntelliJ Platform Gradle Plugin 2.19.0, Gradle 9.8.2, `sinceBuild` 252.

## 6. Лицензия

[Apache License 2.0](LICENSE).
