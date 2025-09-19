plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.company"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // 移除 LSP 依赖，使用本地 Java 实现
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

intellij {
    // CI 环境使用远程下载指定平台（避免依赖本机 IDEA 路径）
    type.set("IC")
    version.set("2024.1")
    downloadSources.set(false)
    plugins.set(listOf("com.intellij.java"))
    updateSinceUntilBuild.set(false)
    pluginName.set("zy-language-support")
    // 支持 2024 和 2025 年版本
    sameSinceUntilBuild.set(false)
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    test {
        useJUnitPlatform()
    }

    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("252.*")
    }
    
    runIde {
        // 添加JVM参数以解决兼容性问题，包含调试支持
        jvmArgs = listOf(
            "-Djava.awt.headless=false",
            "-XX:MaxJavaStackTraceDepth=10000",
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
        )
    }
}