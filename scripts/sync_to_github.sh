#!/bin/bash
# Ember 项目同步脚本：源码 + 构建APK + Release 上传 + README
# 用法：
#   ./scripts/sync_to_github.sh           # 自动检测版本号同步
#   ./scripts/sync_to_github.sh v0.03 "更新说明"  # 指定版本和说明
#
# 依赖：git, curl, gradle, mise(java@17.0.2)
# 环境变量（必需）：
#   GH_TOKEN  - GitHub Personal Access Token（需 repo 权限）
# 可选环境变量：
#   GH_OWNER  - GitHub 用户名（默认 xmfx11）
#   GH_REPO   - 仓库名（默认 Ember）

set -e

# ====== 配置（从环境变量读取，避免硬编码密钥）======
REPO_DIR="/workspace/Ember"
GH_OWNER="${GH_OWNER:-xmfx11}"
GH_REPO="${GH_REPO:-Ember}"
BRANCH="master"
APK_NAME_PREFIX="Ember-"

GH_TOKEN="${GH_TOKEN:-}"
if [ -z "$GH_TOKEN" ]; then
    echo "[ERROR] 请设置环境变量 GH_TOKEN（GitHub Personal Access Token）" >&2
    exit 1
fi

REMOTE_URL="https://${GH_OWNER}:${GH_TOKEN}@github.com/${GH_OWNER}/${GH_REPO}.git"

cd "$REPO_DIR"

# ====== 工具函数 ======
log() { echo "[$(date '+%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; exit 1; }

# 读取 build.gradle.kts 中的 versionName
get_version() {
    grep 'versionName' app/build.gradle.kts | grep -oE '"[0-9]+\.[0-9]+"' | tr -d '"'
}

# ====== 1. 环境：切换 JDK 17 ======
log "切换到 JDK 17..."
if command -v mise >/dev/null 2>&1; then
    eval "$(mise activate bash)"
    mise use java@17.0.2 2>/dev/null || true
fi
java -version 2>&1 | head -1

# ====== 2. 构建 Release APK ======
log "开始构建 Release APK..."
gradle assembleRelease 2>&1 | tail -5
APK_PATH="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK_PATH" ] || err "APK 构建失败"

VERSION=$(get_version)
APK_FILE="${APK_NAME_PREFIX}v${VERSION}.apk"
cp "$APK_PATH" "$APK_FILE"
log "构建完成: $APK_FILE ($(du -h "$APK_FILE" | cut -f1))"

# ====== 3. Git 提交并推送源码 + README ======
log "提交源码到 Git..."
git config user.email "${GH_OWNER}@users.noreply.github.com"
git config user.name "${GH_OWNER}"
# 注意：不把 token 写入 git config 或 remote URL（会被 secret scanning 拦截）
# 改用 GIT_ASKPASS 或 push 时临时注入
export GIT_TERMINAL_PROMPT=0

# 暂存源码改动（.gitignore 已排除 build 产物、APK、keystore）
git add -A
if git diff --cached --quiet; then
    log "无源码改动需要提交"
else
    COMMIT_MSG="release: v${VERSION} 同步源码与构建产物"
    git commit -m "$COMMIT_MSG" 2>&1 | tail -3
fi

log "推送到 GitHub..."
# 临时设置带 token 的 remote URL 用于 push（不持久化到 .git/config）
git -c "http.https://github.com/.extraheader=Authorization: basic $(printf '%s:%s' "$GH_OWNER" "$GH_TOKEN" | base64 -w0)" \
    push "https://github.com/${GH_OWNER}/${GH_REPO}.git" "$BRANCH" 2>&1 | tail -3

# ====== 4. 检查是否已存在该版本 Release ======
TAG="v${VERSION}"
log "检查 Release $TAG 是否已存在..."
EXISTING=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: token $GH_TOKEN" \
    "https://api.github.com/repos/${GH_OWNER}/${GH_REPO}/releases/tags/${TAG}")

if [ "$EXISTING" = "200" ]; then
    log "Release $TAG 已存在，跳过创建（如需更新请先手动删除）"
    log "完成。源码已推送，APK 本地路径: $REPO_DIR/$APK_FILE"
    exit 0
fi

# ====== 5. 创建 Release ======
RELEASE_NOTES="${2:-自动同步版本 $TAG（源码 + APK + README）}"
log "创建 Release $TAG..."
RESPONSE=$(curl -s -X POST -H "Authorization: token $GH_TOKEN" -H "Content-Type: application/json" -d "{
  \"tag_name\": \"$TAG\",
  \"target_commitish\": \"$BRANCH\",
  \"name\": \"Ember $TAG\",
  \"body\": \"## $TAG\\n\\n$RELEASE_NOTES\\n\\n### 下载\\n- \`$APK_FILE\`（已签名）\\n\\n---\\n\\n**完整源码**：[master 分支](https://github.com/${GH_OWNER}/${GH_REPO}/tree/${BRANCH})\",
  \"draft\": false,
  \"prerelease\": false
}" "https://api.github.com/repos/${GH_OWNER}/${GH_REPO}/releases")

RELEASE_ID=$(echo "$RESPONSE" | grep -m1 '"id":' | grep -oE '[0-9]+')
[ -z "$RELEASE_ID" ] && err "创建 Release 失败: $RESPONSE"
log "Release 创建成功，ID: $RELEASE_ID"

# ====== 6. 上传 APK 到 Release ======
log "上传 $APK_FILE 到 Release..."
UPLOAD_RESP=$(curl -s -X POST \
    -H "Authorization: token $GH_TOKEN" \
    -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary @"$APK_FILE" \
    "https://uploads.github.com/repos/${GH_OWNER}/${GH_REPO}/releases/${RELEASE_ID}/assets?name=${APK_FILE}")

DOWNLOAD_URL=$(echo "$UPLOAD_RESP" | grep -oE '"browser_download_url":"[^"]+"' | cut -d'"' -f4)
if [ -z "$DOWNLOAD_URL" ]; then
    err "APK 上传失败: $UPLOAD_RESP"
fi

log "========================================"
log "同步完成！"
log "  版本:   $TAG"
log "  源码:   https://github.com/${GH_OWNER}/${GH_REPO}"
log "  Release: https://github.com/${GH_OWNER}/${GH_REPO}/releases/tag/${TAG}"
log "  APK:    $DOWNLOAD_URL"
log "========================================"
