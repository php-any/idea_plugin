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
    // 使用本地已安装的 IntelliJ IDEA，避免远程下载
    localPath.set("/Applications/IntelliJ IDEA.app/Contents")
    // type.set("IC")
    downloadSources.set(false)
    plugins.set(listOf("com.intellij.java"))
    // 不自动写入 since/until 到 plugin.xml，由我们手动控制
    updateSinceUntilBuild.set(false)
    // 添加配置以解决Gradle兼容性问题
    pluginName.set("zy-language-support")
    // version.set("2024.1.3")
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
        untilBuild.set("241.*")
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