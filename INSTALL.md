了，# 安装与调试指南

本文档介绍如何在 GoLand/IntelliJ IDEA 中安装并调试本插件（ZY Language Support）。

> 参考 VSCode 版本功能与定位：`php-any/zy-extension`（语法高亮、基础提示、未来 LSP 接入）

## 安装方式

### 方式一：源码运行（推荐开发者）

1. 安装 JDK 17。
2. 克隆仓库并进入目录：
   ```bash
   git clone <your-repo-url> zy-idea-plugin
   cd zy-idea-plugin
   ```
3. 启动沙箱 IDE：
   ```bash
   ./gradlew runIde
   ```
4. 在沙箱 IDE 中新建或打开 `.cjp`/`.cj`/`.zy` 文件，体验语法高亮与补全。

### 方式二：安装打包 Zip（给试用/分发）

1. 本地打包：
   ```bash
   ./gradlew buildPlugin
   ```
2. 找到 `build/distributions/xxx.zip`。
3. 打开 GoLand/IntelliJ IDEA：
   - Settings/Preferences → Plugins → ⚙ → Install Plugin from Disk → 选择 zip → 重启 IDE。

## 调试与开发流程

- 启动沙箱 IDE：`./gradlew runIde`
- 修改源码后，直接在 Gradle 工具窗口或命令行再次执行 `runIde` 即可验证。
- 建议在沙箱中创建样例文件验证：
  ```
  // test.cjp
  函数 获取用户信息(int $id): ?User {
      返回 this->userService->findById($id);
  }
  @Controller
  @Route(prefix: "/api")
  ```

## 常见问题排查

- 无法启动/构建失败：
  - 确认 JDK 为 17。
  - 清理缓存后重试：`./gradlew clean` 再执行 `build` 或 `runIde`。
- 无法下载依赖：
  - 配置代理或使用国内镜像；检查 `mavenCentral` 可达性。
- 插件未生效：
  - 确认文件扩展名是否为 `.cjp`、`.cj` 或 `.zy`。
  - 在沙箱 IDE 的 Plugins 页检查插件是否已启用。

## 与 VSCode zy-extension 的对应

- 语法高亮：关键字、字符串、数字、注释（含中文关键字）
- 基础补全：英文/中文关键字、常用注解（`@Controller`、`@Route`、`@GetMapping`、`@Inject`）
- 文件类型：`.cjp`、`.cj`、`.zy`
- LSP：当前保留扩展位，后续将提供与 `zy-lsp` 的 stdio/TCP 自动检测与回退策略（VSCode 版为 `zy-lsp` 优先，其次 TCP 连接 `localhost:8800`）。

## 目录结构（简要）

```
src/
  main/
    kotlin/com/phpany/zy/
      lang/         # 语言、文件类型注册
      highlight/    # 词法器与语法高亮
      completion/   # 基础补全
    resources/META-INF/
      plugin.xml    # 插件清单
      pluginIcon.svg
build.gradle.kts
settings.gradle.kts
README.md
BUILD.md
INSTALL.md
```
