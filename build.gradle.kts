plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.10"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.company"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")
}

intellij {
    // 使用本地已安装的 IntelliJ IDEA，避免远程下载
    localPath.set("/Applications/IntelliJ IDEA.app/Contents")
    type.set("IC")
    downloadSources.set(false)
    plugins.set(listOf("com.intellij.java"))
    // 不自动写入 since/until 到 plugin.xml，由我们手动控制
    updateSinceUntilBuild.set(false)
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("241")
    }
}
