# 版本标签管理脚本

这个目录包含了用于管理 ZY 语言插件版本的自动化脚本。

## 脚本说明

### 1. `tag-version.sh` - 完整版本管理脚本

功能完整的版本标签管理脚本，支持多种版本递增类型。

#### 使用方法

```bash
# 显示帮助信息
./scripts/tag-version.sh --help

# 查看当前版本
./scripts/tag-version.sh --current

# 创建补丁版本 (1.0.0 -> 1.0.1)
./scripts/tag-version.sh --patch

# 创建次版本 (1.0.0 -> 1.1.0)
./scripts/tag-version.sh --minor

# 创建主版本 (1.0.0 -> 2.0.0)
./scripts/tag-version.sh --major
```

#### 功能特性

- ✅ 自动检测当前版本
- ✅ 支持三种版本递增类型
- ✅ 分支验证（建议在 main 分支使用）
- ✅ 工作目录状态检查
- ✅ 交互式确认
- ✅ 自动推送到远程仓库
- ✅ 彩色输出和错误处理

### 2. `quick-tag.sh` - 快速标签脚本

简化的快速标签创建脚本，自动递增补丁版本。

#### 使用方法

```bash
# 快速创建下一个补丁版本标签
./scripts/quick-tag.sh
```

#### 功能特性

- ✅ 自动递增补丁版本
- ✅ 工作目录状态检查
- ✅ 交互式确认
- ✅ 自动推送到远程仓库
- ✅ 友好的用户界面

## 版本号规则

遵循 [语义化版本](https://semver.org/lang/zh-CN/) 规范：

- **主版本号 (Major)**: 不兼容的 API 修改
- **次版本号 (Minor)**: 向下兼容的功能性新增
- **补丁版本号 (Patch)**: 向下兼容的问题修正

## 使用建议

### 日常开发

```bash
# 修复 bug 或小改动
./scripts/quick-tag.sh
```

### 功能发布

```bash
# 新功能发布
./scripts/tag-version.sh --minor
```

### 重大更新

```bash
# 重大版本更新
./scripts/tag-version.sh --major
```

## 自动化流程

1. **创建标签** → 脚本自动创建 Git 标签
2. **推送标签** → 自动推送到远程仓库
3. **触发构建** → GitHub Actions 检测到新标签
4. **自动发布** → 构建完成后自动创建 GitHub Release

## 注意事项

- 确保在 `main` 分支上创建标签
- 确保工作目录干净（无未提交的更改）
- 标签创建后会自动触发 GitHub Actions 构建
- 建议在创建标签前先测试代码

## 故障排除

### 工作目录不干净

```bash
# 提交所有更改
git add .
git commit -m "准备发布新版本"

# 或者暂存更改
git stash
```

### 标签已存在

```bash
# 删除本地标签
git tag -d v1.0.0

# 删除远程标签
git push origin :refs/tags/v1.0.0
```

### 权限问题

```bash
# 确保脚本有执行权限
chmod +x scripts/*.sh
```
