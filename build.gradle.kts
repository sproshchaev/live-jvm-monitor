plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "ru.sproshchaev"
version = "0.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local(providers.gradleProperty("localIdePath"))
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // IDE 2025.1 (GigaIDE 2025.1) несет stdlib Kotlin 2.1: новее вызовов быть не должно.
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        // Без мостов к методам интерфейсов платформы: verifyPlugin не видит лишних вызовов устаревшего API.
        jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
    }
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            local(file(providers.gradleProperty("localIdePath")))
        }
        // Без сети: проверка висла на загрузке списка изменений API с GitHub (504).
        freeArgs = listOf("-offline")
    }
}
