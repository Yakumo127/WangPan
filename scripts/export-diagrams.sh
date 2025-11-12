#!/usr/bin/env bash
set -euo pipefail

# 批量导出 PlantUML 图为 PNG/SVG
# 目录：docs/系统设计/活动图 与 docs/系统设计/时序图
# 输出：各自目录下 预览/png 与 预览/svg

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
DIRS=(
  "docs/系统设计/活动图"
  "docs/系统设计/时序图"
)

have_cmd() { command -v "$1" >/dev/null 2>&1; }

render_with_plantuml() {
  local dir="$1"
  local fmt="$2"  # png 或 svg
  ( cd "$dir" && mkdir -p "预览/$fmt" && plantuml -t"$fmt" -o "预览/$fmt" ./*.puml )
}

render_with_docker() {
  local dir="$1"
  local fmt="$2"
  docker run --rm -v "$ROOT_DIR":/work -w /work/"$dir" plantuml/plantuml -t"$fmt" -o "预览/$fmt" ./*.puml
}

main() {
  local used=""
  if have_cmd plantuml; then
    used="plantuml"
  elif have_cmd docker; then
    used="docker"
  else
    echo "未找到 plantuml 或 docker。请先安装 plantuml 或 docker，或手动执行导出。" >&2
    exit 2
  fi

  for d in "${DIRS[@]}"; do
    if [ -d "$ROOT_DIR/$d" ] && ls "$ROOT_DIR/$d"/*.puml >/dev/null 2>&1; then
      echo "导出目录: $d"
      if [ "$used" = "plantuml" ]; then
        render_with_plantuml "$d" png
        render_with_plantuml "$d" svg
      else
        render_with_docker "$d" png
        render_with_docker "$d" svg
      fi
    fi
  done
  echo "导出完成。预览文件已生成至各目录的 预览/png 与 预览/svg。"
}

main "$@"

