#!/bin/bash

# ZY 语言插件自动版本标签脚本
# 支持自动递增版本号并创建 Git 标签

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
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

# 显示使用帮助
show_help() {
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -p, --patch    递增补丁版本 (1.0.0 -> 1.0.1)"
    echo "  -m, --minor    递增次版本 (1.0.0 -> 1.1.0)"
    echo "  -M, --major    递增主版本 (1.0.0 -> 2.0.0)"
    echo "  -c, --current  显示当前版本"
    echo "  -h, --help     显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 --patch     # 创建补丁版本标签"
    echo "  $0 --minor     # 创建次版本标签"
    echo "  $0 --major     # 创建主版本标签"
}

# 获取当前版本号
get_current_version() {
    # 获取最新的标签版本
    local latest_tag=$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")
    echo "${latest_tag#v}"  # 移除 'v' 前缀
}

# 解析版本号
parse_version() {
    local version=$1
    local IFS='.'
    read -ra VERSION_PARTS <<< "$version"
    echo "${VERSION_PARTS[@]}"
}

# 递增版本号
increment_version() {
    local version=$1
    local increment_type=$2
    
    local IFS='.'
    read -ra VERSION_PARTS <<< "$version"
    
    local major=${VERSION_PARTS[0]:-0}
    local minor=${VERSION_PARTS[1]:-0}
    local patch=${VERSION_PARTS[2]:-0}
    
    case $increment_type in
        "major")
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        "minor")
            minor=$((minor + 1))
            patch=0
            ;;
        "patch")
            patch=$((patch + 1))
            ;;
        *)
            print_error "无效的版本递增类型: $increment_type"
            exit 1
            ;;
    esac
    
    echo "$major.$minor.$patch"
}

# 验证当前分支
validate_branch() {
    local current_branch=$(git branch --show-current)
    
    # 检查是否在 main 分支
    if [[ "$current_branch" != "main" ]]; then
        print_warning "当前分支: $current_branch"
        print_warning "建议在 main 分支上创建标签"
        read -p "是否继续? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "操作已取消"
            exit 0
        fi
    fi
}

# 检查工作目录是否干净
check_working_directory() {
    if ! git diff-index --quiet HEAD --; then
        print_error "工作目录不干净，请先提交或暂存更改"
        git status --short
        exit 1
    fi
}

# 创建标签
create_tag() {
    local version=$1
    local tag_name="v$version"
    
    print_info "创建标签: $tag_name"
    
    # 创建带注释的标签
    git tag -a "$tag_name" -m "Release version $version"
    
    print_success "标签 $tag_name 创建成功"
    
    # 推送到远程仓库
    print_info "推送标签到远程仓库..."
    git push origin "$tag_name"
    
    print_success "标签已推送到远程仓库"
}

# 主函数
main() {
    local increment_type=""
    local show_current=false
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -p|--patch)
                increment_type="patch"
                shift
                ;;
            -m|--minor)
                increment_type="minor"
                shift
                ;;
            -M|--major)
                increment_type="major"
                shift
                ;;
            -c|--current)
                show_current=true
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                print_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 显示当前版本
    if [[ "$show_current" == true ]]; then
        local current_version=$(get_current_version)
        print_info "当前版本: v$current_version"
        exit 0
    fi
    
    # 检查是否指定了递增类型
    if [[ -z "$increment_type" ]]; then
        print_error "请指定版本递增类型"
        show_help
        exit 1
    fi
    
    # 验证环境
    validate_branch
    check_working_directory
    
    # 获取当前版本并递增
    local current_version=$(get_current_version)
    local new_version=$(increment_version "$current_version" "$increment_type")
    
    print_info "当前版本: v$current_version"
    print_info "新版本: v$new_version"
    
    # 确认创建标签
    read -p "确认创建标签 v$new_version? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_info "操作已取消"
        exit 0
    fi
    
    # 创建标签
    create_tag "$new_version"
    
    print_success "版本标签创建完成！"
    print_info "新标签: v$new_version"
    print_info "GitHub Actions 将自动构建并发布插件"
}

# 运行主函数
main "$@"
