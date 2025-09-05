# ZY Language Support Plugin

IntelliJ IDEA 插件，为 `.zy` 文件提供语法高亮、代码提示和 LSP 集成功能。

## 功能特性

- ✅ **语法高亮**: 基于 TextMate 语法文件 `origami.tmLanguage.json`
- ✅ **代码提示**: 基于 LSP 的智能代码补全
- ✅ **LSP 集成**: 连接到外部语言服务器
- ✅ **文件类型支持**: 支持 `.zy` 文件扩展名
- ✅ **错误高亮**: 语法错误和诊断信息

## 技术栈

- **开发语言**: Kotlin
- **构建工具**: Gradle
- **IntelliJ Platform SDK**: 2023.1+
- **LSP 集成**: IntelliJ LSP API
- **语言服务器**: `/Users/lvluo/Desktop/github.com/php-any/origami/bin/zy-lsp`

## 项目结构

```
src/
├── main/
│   ├── kotlin/
│   │   └── com/company/plugin/
│   │       ├── language/          # 语言相关功能
│   │       ├── lsp/              # LSP 客户端集成
│   │       ├── completion/       # 代码提示
│   │       ├── navigation/       # 代码跳转
│   │       └── highlighting/     # 语法高亮
│   └── resources/
│       ├── META-INF/
│       │   └── plugin.xml        # 插件配置
│       ├── icons/               # 图标资源
│       └── fileTypes/           # 文件类型定义
└── test/
    └── kotlin/
```

## 开发指南

### 构建插件

```bash
./gradlew build
```

### 运行插件

```bash
./gradlew runIde
```

### 打包插件

```bash
./gradlew buildPlugin
```

## 配置

### LSP 服务器路径

默认 LSP 服务器路径: `/Users/lvluo/Desktop/github.com/php-any/origami/bin/zy-lsp`

### 支持的文件类型

- 扩展名: `.zy`
- MIME 类型: `text/x-zy`
- 语言: DIY 语言 (Origami)

## 开发规范

- 遵循 IntelliJ Platform 编码规范
- 使用 Kotlin 作为主要开发语言
- 基于 TextMate 语法文件实现语法高亮
- 使用 IntelliJ LSP API 进行语言服务器集成
- 异步处理耗时操作，避免阻塞 UI

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！
