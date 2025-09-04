# ZY Language Support Plugin

为 Origami/折言 语言提供 IntelliJ IDEA/GoLand 插件支持。

## 功能特性

### 基础功能

- ✅ 语法高亮（关键字、类型、注解、变量、字符串、注释等）
- ✅ 代码补全（PHP/Go 关键字、类型、注解）
- ✅ 自动分号插入
- ✅ 代码折叠（`{}` 代码块）
- ✅ 文件图标支持（`.cjp`, `.cj`, `.zy` 文件）

### LSP 集成功能

- ✅ 自动启动本地 `zy-lsp` 服务（stdio 协议）
- ✅ 代码跳转（Go to Definition）
- ✅ 实时文档同步
- ✅ 项目级语言服务

## 安装要求

### 必需组件

1. **IntelliJ IDEA** 或 **GoLand** (2024.1+)
2. **Java 17+** 运行时
3. **zy-lsp** 语言服务器（需要单独安装）

### 安装 zy-lsp

```bash
# 方法1：从源码编译
git clone https://github.com/php-any/zy-lsp
cd zy-lsp
go build -o zy-lsp
sudo mv zy-lsp /usr/local/bin/

# 方法2：检查是否已安装
which zy-lsp
zy-lsp --help
```

## 安装插件

1. 下载 `zy-idea-plugin-0.1.0.zip`
2. 打开 IntelliJ IDEA/GoLand
3. 进入 `Settings` → `Plugins`
4. 点击齿轮图标 → `Install Plugin from Disk`
5. 选择下载的 zip 文件
6. 重启 IDE

## 使用方法

### 基础使用

1. 创建或打开 `.cjp`, `.cj`, `.zy` 文件
2. 享受语法高亮和代码补全
3. 使用 `Ctrl+Enter` 自动插入分号
4. 点击代码块左侧的折叠图标进行代码折叠

### LSP 功能

1. 打开包含 ZY 文件的项目
2. 插件会自动启动 `zy-lsp` 服务（stdio 协议）
3. 使用 `Ctrl+Click` 或 `Ctrl+B` 跳转到定义
4. 享受完整的语言服务支持

### 故障排除

- 如果 LSP 功能不工作，请检查：
  - `zy-lsp` 是否正确安装并可在 PATH 中找到
  - 项目是否包含 ZY 文件
  - 查看 IDE 日志中的错误信息

## 开发

### 构建环境

- Java 17+
- Gradle 8.7+
- IntelliJ Platform SDK

### 构建命令

```bash
./gradlew buildPlugin
```

### 运行测试

```bash
./gradlew runIde
```

## 技术架构

### 插件组件

- **ZyLanguage**: 语言定义
- **ZyFileType**: 文件类型注册
- **ZyLexerAdapter**: 词法分析器
- **ZySyntaxHighlighter**: 语法高亮器
- **ZyCompletionContributor**: 代码补全
- **ZySemicolonTypedHandler**: 自动分号插入
- **ZyFoldingBuilder**: 代码折叠
- **ZyLspClient**: LSP 客户端（stdio 协议）
- **ZyLspService**: LSP 服务管理

### LSP 集成

插件通过以下方式与 `zy-lsp` 集成：

1. **自动启动**: 项目打开时自动启动 LSP 服务
2. **stdio 通信**: 通过标准输入输出与 LSP 服务器通信
3. **JSON-RPC**: 使用标准的 LSP 协议
4. **异步处理**: 非阻塞的消息处理

## 许可证

MIT License
