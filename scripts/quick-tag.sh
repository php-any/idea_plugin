#!/bin/bash

# 快速标签脚本 - 自动递增补丁版本
# 用法: ./scripts/quick-tag.sh

echo "🚀 ZY 语言插件快速标签创建"
echo "================================"

# 检查是否有未提交的更改
if ! git diff-index --quiet HEAD --; then
    echo "❌ 工作目录不干净，请先提交更改"
    git status --short
    exit 1
fi

# 获取当前版本
current_version=$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")
current_version=${current_version#v}

echo "📋 当前版本: v$current_version"

# 解析版本号
IFS='.' read -ra VERSION_PARTS <<< "$current_version"
major=${VERSION_PARTS[0]:-0}
minor=${VERSION_PARTS[1]:-0}
patch=${VERSION_PARTS[2]:-0}

# 递增补丁版本
patch=$((patch + 1))
new_version="$major.$minor.$patch"

echo "🆕 新版本: v$new_version"

# 确认创建
read -p "确认创建标签 v$new_version? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ 操作已取消"
    exit 0
fi

# 创建标签
echo "🏷️  创建标签 v$new_version..."
git tag -a "v$new_version" -m "Release version $new_version"

# 推送标签
echo "📤 推送标签到远程仓库..."
git push origin "v$new_version"

echo "✅ 标签创建成功！"
echo "🎉 GitHub Actions 将自动构建并发布插件"
echo "🔗 查看发布: https://github.com/$(git config --get remote.origin.url | sed 's/.*github.com[:/]\([^.]*\).*/\1/')/releases"
