# ZY Language Support Plugin

IntelliJ IDEA 插件，为 `.zy` 文件提供语法高亮、代码提示和代码跳转功能。

## 功能特性

- ✅ **语法高亮**: 基于 TextMate 语法文件 `origami.tmLanguage.json`
- ✅ **代码提示**: 智能代码补全和自动完成
- ✅ **代码跳转**: 支持定义跳转和引用查找
- ✅ **文件类型支持**: 支持 `.zy` 文件扩展名
- ✅ **错误高亮**: 语法错误和诊断信息
- ✅ **符号索引**: 快速符号搜索和导航

## 安装方法

### 从 Release 安装（推荐）

1. **下载插件包**

   - 访问 [Releases 页面](https://github.com/php-any/idea_plugin/releases)
   - 下载最新版本的 `plugin-distributions-*.zip` 文件

2. **在 IntelliJ IDEA 中安装**

   - 打开 IntelliJ IDEA
   - 进入 `File` → `Settings` → `Plugins`
   - 点击 `⚙️` 图标 → `Install Plugin from Disk...`
   - 选择下载的 `.zip` 文件
   - 点击 `OK` 并重启 IDE

3. **验证安装**
   - 创建或打开 `.zy` 文件
   - 检查是否有语法高亮和代码提示功能

### 支持版本

- **IntelliJ IDEA**: 2024.1 - 2025.2
- **平台**: Windows / macOS / Linux
- **插件大小**: ~96 KB

## 技术栈

- **开发语言**: Java
- **构建工具**: Gradle
- **IntelliJ Platform SDK**: 2024.1+
- **实现方式**: 本地 Java 实现，无外部依赖

## 使用方法

### 创建 .zy 文件

1. 在 IntelliJ IDEA 中创建新文件
2. 文件名以 `.zy` 结尾
3. 开始编写 ZY 语言代码

### 功能使用

- **语法高亮**: 自动识别 ZY 语言语法并高亮显示
- **代码补全**: 输入时按 `Ctrl+Space` 触发代码提示
- **代码跳转**: `Ctrl+Click` 或 `F12` 跳转到定义
- **符号搜索**: `Ctrl+Shift+Alt+N` 搜索符号

## 项目结构

```
src/
├── main/
│   ├── java/
│   │   └── com/company/plugin/
│   │       ├── language/          # 语言相关功能
│   │       ├── completion/       # 代码提示
│   │       ├── navigation/       # 代码跳转
│   │       ├── highlighting/     # 语法高亮
│   │       └── index/            # 符号索引
│   └── resources/
│       ├── META-INF/
│       │   └── plugin.xml        # 插件配置
│       ├── icons/               # 图标资源
│       └── fileTypes/           # 文件类型定义
└── test/
    └── java/
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

### 版本管理

```bash
# 快速创建下一个补丁版本
./scripts/quick-tag.sh

# 创建次版本
./scripts/tag-version.sh --minor

# 创建主版本
./scripts/tag-version.sh --major
```

## 配置

### 支持的文件类型

- **扩展名**: `.zy`
- **MIME 类型**: `text/x-zy`
- **语言**: ZY 语言 (Origami)
- **图标**: 自定义 ZY 文件图标

## 故障排除

### 插件安装失败

1. **检查 IntelliJ IDEA 版本**

   - 确保使用 2024.1 或更高版本
   - 检查插件兼容性

2. **重新安装插件**

   - 卸载现有插件
   - 重启 IDE
   - 重新安装插件

3. **检查插件文件**
   - 确保下载的是完整的 `.zip` 文件
   - 检查文件大小（约 96 KB）

### 功能不工作

1. **重启 IDE**

   - 安装后需要重启 IntelliJ IDEA

2. **检查文件类型**

   - 确保文件扩展名是 `.zy`
   - 检查文件是否被正确识别

3. **查看日志**
   - 打开 `Help` → `Show Log in Explorer`
   - 查看是否有错误信息

## 开发规范

- 遵循 IntelliJ Platform 编码规范
- 使用 Java 作为主要开发语言
- 基于 TextMate 语法文件实现语法高亮
- 本地实现所有功能，无外部依赖
- 异步处理耗时操作，避免阻塞 UI

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！

## 更新日志

### v0.0.3

- ✅ 支持 .zy 文件语法高亮
- ✅ 实现代码补全功能
- ✅ 添加代码跳转支持
- ✅ 集成符号索引服务
- ✅ 支持 IntelliJ IDEA 2024.1 - 2025.2
