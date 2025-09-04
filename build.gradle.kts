plugins {
	kotlin("jvm") version "1.9.24"
	id("org.jetbrains.intellij") version "1.17.3"
}

repositories {
	mavenCentral()
}

intellij {
	localPath.set("/Applications/IntelliJ IDEA.app/Contents")
	plugins.set(listOf())
}


group = "com.phpany.zy"
version = "0.1.0"

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(23))
	}
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

tasks {
	patchPluginXml {
		pluginDescription.set("为 Origami/折言 语言提供语法高亮与基础提示")
		sinceBuild.set("241")
		untilBuild.set("251.*")
	}

	runIde {
	}

	buildSearchableOptions {
		enabled = false
	}
}

kotlin {
	jvmToolchain(23)
}

// Kotlin 编译目标为 17
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
	kotlinOptions.jvmTarget = "17"
}
