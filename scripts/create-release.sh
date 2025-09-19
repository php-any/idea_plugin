#!/bin/bash

# 手动创建 GitHub Release 脚本
# 当自动创建失败时使用

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查是否在 Git 仓库中
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    print_error "当前目录不是 Git 仓库"
    exit 1
fi

# 获取当前标签
current_tag=$(git describe --tags --exact-match HEAD 2>/dev/null || echo "")
if [[ -z "$current_tag" ]]; then
    print_error "当前 HEAD 没有对应的标签"
    print_info "请先创建标签: ./scripts/quick-tag.sh"
    exit 1
fi

print_info "当前标签: $current_tag"

# 检查插件文件是否存在
plugin_file="build/distributions/plugin-distributions-${current_tag#v}.zip"
if [[ ! -f "$plugin_file" ]]; then
    print_error "插件文件不存在: $plugin_file"
    print_info "请先构建插件: ./gradlew buildPlugin"
    exit 1
fi

print_info "找到插件文件: $plugin_file"

# 获取仓库信息
repo_url=$(git config --get remote.origin.url)
if [[ "$repo_url" =~ github\.com[:/]([^/]+)/([^/]+) ]]; then
    owner="${BASH_REMATCH[1]}"
    repo="${BASH_REMATCH[2]%.git}"
else
    print_error "无法解析 GitHub 仓库信息"
    exit 1
fi

print_info "仓库: $owner/$repo"

# 检查 GitHub CLI 是否安装
if ! command -v gh &> /dev/null; then
    print_error "GitHub CLI (gh) 未安装"
    print_info "请安装 GitHub CLI: https://cli.github.com/"
    exit 1
fi

# 检查是否已登录
if ! gh auth status &> /dev/null; then
    print_error "GitHub CLI 未登录"
    print_info "请先登录: gh auth login"
    exit 1
fi

# 检查 Release 是否已存在
if gh release view "$current_tag" &> /dev/null; then
    print_warning "Release $current_tag 已存在"
    read -p "是否重新创建? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_info "删除现有 Release..."
        gh release delete "$current_tag" --yes
    else
        print_info "操作已取消"
        exit 0
    fi
fi

# 创建 Release
print_info "创建 GitHub Release: $current_tag"

# 生成 Release 说明
release_notes="## ZY 语言插件 $current_tag

### 功能特性
- 支持 .zy 文件语法高亮
- 提供代码补全功能
- 支持代码跳转和导航
- 集成 IntelliJ IDEA 平台

### 安装方法
1. 下载插件包
2. 在 IntelliJ IDEA 中安装插件
3. 重启 IDE 生效

### 支持版本
- IntelliJ IDEA 2024.1 - 2025.2
- 跨平台支持 (Windows/macOS/Linux)

### 文件信息
- 插件包: plugin-distributions-${current_tag#v}.zip
- 构建时间: $(date)
- 构建分支: $(git branch --show-current)"

# 创建 Release
gh release create "$current_tag" \
    --title "$current_tag" \
    --notes "$release_notes" \
    "$plugin_file"

print_success "Release 创建成功!"
print_info "查看 Release: https://github.com/$owner/$repo/releases/tag/$current_tag"
