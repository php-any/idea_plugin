# ZY Language Support Plugin - 安装与调试指南

## 安装步骤

### 1. 环境准备

#### 必需组件

- **IntelliJ IDEA** 或 **GoLand** (2024.1+)
- **Java 17+** 运行时
- **zy-lsp** 语言服务器

#### 检查 Java 版本

```bash
java -version
# 应该显示 Java 17 或更高版本
```

#### 安装 zy-lsp

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

### 2. 安装插件

#### 方法 1：从 ZIP 文件安装

1. 下载 `zy-idea-plugin-0.1.0.zip`
2. 打开 IntelliJ IDEA/GoLand
3. 进入 `Settings` → `Plugins`
4. 点击齿轮图标 → `Install Plugin from Disk`
5. 选择下载的 zip 文件
6. 重启 IDE

#### 方法 2：从源码构建安装

```bash
# 克隆源码
git clone https://github.com/php-any/idea
cd idea

# 构建插件
./gradlew buildPlugin

# 安装到本地 IDE
./gradlew runIde
```

### 3. 验证安装

1. 创建测试文件 `test.cjp`：

```php
<?php
// 测试语法高亮
function hello($name) {
    echo "Hello, $name!";
    return true;
}

@Controller
class UserController {
    public function index() {
        $users = [];
        return $users;
    }
}
```

2. 检查功能：
   - ✅ 语法高亮正常
   - ✅ 代码补全可用
   - ✅ 自动分号插入
   - ✅ 代码折叠功能

## 故障排除

### 常见问题

#### 1. 插件不兼容

```
Plugin 'ZY Language Support' is not compatible with the current version of the IDE
```

**解决方案**：

- 检查 IDE 版本是否为 2024.1+
- 更新插件版本或 IDE 版本

#### 2. 语法高亮不工作

**检查步骤**：

1. 确认文件扩展名为 `.cjp`, `.cj`, `.zy`
2. 检查插件是否正确安装
3. 重启 IDE
4. 查看 IDE 日志

#### 3. LSP 功能不工作

**检查步骤**：

1. 确认 `zy-lsp` 已安装：
   ```bash
   which zy-lsp
   zy-lsp --help
   ```
2. 检查项目是否包含 ZY 文件
3. 查看 IDE 日志中的 LSP 错误

#### 4. 编译错误

```
Unresolved reference: done
```

**解决方案**：

- 更新到最新版本的插件
- 检查 Java 版本是否为 17+

### 调试模式

#### 启用详细日志

1. 打开 `Help` → `Diagnostic Tools` → `Debug Log Settings`
2. 添加以下日志类别：
   ```
   com.phpany.zy
   com.intellij.openapi.project
   ```

#### 查看 LSP 通信

1. 打开 `Help` → `Show Log in Explorer`
2. 查找包含 `ZY LSP` 的日志条目
3. 检查是否有错误信息

### 性能优化

#### 大文件处理

- 对于大型 ZY 文件，LSP 可能需要更多时间
- 考虑将大文件拆分为多个小文件

#### 内存使用

- 如果遇到内存不足，增加 IDE 的堆内存：
  - 编辑 `idea.vmoptions` 文件
  - 增加 `-Xmx` 参数

## 开发调试

### 本地开发环境

```bash
# 克隆项目
git clone https://github.com/php-any/idea
cd idea

# 设置 Java 环境
export JAVA_HOME="/path/to/java17"
export PATH="$JAVA_HOME/bin:$PATH"

# 构建并运行
./gradlew buildPlugin
./gradlew runIde
```

### 调试技巧

1. **热重载**：修改代码后重新构建插件
2. **日志输出**：使用 `LOG.info()` 输出调试信息
3. **断点调试**：在 IDE 中设置断点进行调试

## 支持与反馈

### 报告问题

1. 查看现有 Issues
2. 创建新的 Issue，包含：
   - IDE 版本
   - 插件版本
   - 错误日志
   - 重现步骤

### 贡献代码

1. Fork 项目
2. 创建功能分支
3. 提交 Pull Request

## 更新日志

### v0.1.0

- ✅ 基础语法高亮
- ✅ 代码补全功能
- ✅ 自动分号插入
- ✅ 代码折叠支持
- ✅ LSP 集成框架
- ✅ 文件图标支持
