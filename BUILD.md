# 构建指南（GoLand/IDEA 插件）

本文档介绍如何在本地构建本插件（ZY Language Support）。

## 先决条件

- JDK 17（建议 Temurin/OpenJDK 发行版）
- 网络可访问 `mavenCentral` 与 IntelliJ 平台仓库
- 可选：Gradle（建议使用 Gradle Wrapper，后续会补充）

## 获取源码

```bash
git clone <your-repo-url> zy-idea-plugin
cd zy-idea-plugin
```

## 使用 Gradle 构建

```bash
# 首次建议直接构建，自动下载 IntelliJ 依赖
./gradlew build
```

产物位置：

- 编译产物：`build/`
- 插件打包（如启用 `buildPlugin` 任务）：`build/distributions/*.zip`

若需要打包为可安装的插件 Zip：

```bash
./gradlew buildPlugin
```

## 本地运行沙箱 IDE（调试）

```bash
./gradlew runIde
```

执行后会启动一个带插件的沙箱 IDE（通常为 IntelliJ IDEA Community）。在其中新建 `.cjp`/`.cj`/`.zy` 文件即可验证语法高亮与补全。

## 修改目标 IDE 版本

`build.gradle.kts` 中：

```kotlin
dependencies {
    intellijPlatform {
        create(
            ide = IntelliJPlatform.IdeaCommunity,
            version = "2024.1"
        )
    }
}
```

- 如需对接 GoLand，可改为 `IntelliJPlatform.GoLand` 并指定兼容的版本。
- 注意：不同 IDE/版本可能需要调整 `plugins` 或 API 兼容性。

## 常见问题

- 构建失败：检查 JDK 版本是否为 17；国内网络建议配置代理或使用镜像。
- 运行沙箱 IDE 异常：尝试清理：`./gradlew clean` 后再 `./gradlew runIde`。
- 依赖下载慢：可设置 Gradle 代理，或在 `~/.gradle/gradle.properties` 配置镜像。
