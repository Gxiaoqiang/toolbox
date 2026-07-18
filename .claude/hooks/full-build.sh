#!/bin/bash
# 全量验证脚本: 前端 vue-tsc + build → 后端 test + package
# Stop hook 调用，确保每次会话结束时代码可构建可测试

set -e
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

echo "=========================================="
echo "  [Hook] Full Build Verification"
echo "=========================================="

# 1. 前端构建 (含 vue-tsc 类型检查)
echo ""
echo "▶ Step 1/3: Frontend build (vue-tsc + vite)..."
cd "$ROOT/frontend"
npm run build
echo "✓ Frontend build passed"

# 2. 后端测试
echo ""
echo "▶ Step 2/3: Backend tests (mvn test)..."
cd "$ROOT/backend"
mvn test -q 2>&1 | tail -5
echo "✓ Backend tests passed"

# 3. 后端打包 (含前端静态资源)
echo ""
echo "▶ Step 3/3: Backend package (mvn clean package)..."
mvn clean package -DskipTests -q 2>&1 | tail -3
echo "✓ Backend package passed"

echo ""
echo "=========================================="
echo "  [Hook] All checks passed ✓"
echo "=========================================="