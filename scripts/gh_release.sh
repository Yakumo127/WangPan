#!/usr/bin/env bash
set -euo pipefail

# Create a GitHub Release using GitHub CLI (gh) with prepared artifacts and notes.
# Usage:
#   scripts/gh_release.sh <VERSION> [--draft] [--prerelease]
# Env:
#   GH_REPO (optional)  e.g. your-org/your-repo; if unset, gh uses current repo

if ! command -v gh >/dev/null 2>&1; then
  echo "ERROR: GitHub CLI 'gh' not found. Install from https://cli.github.com/" >&2
  exit 1
fi

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
  echo "Usage: $0 <VERSION> [--draft] [--prerelease]" >&2
  exit 1
fi
shift || true

DRAFT=false
PRERELEASE=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --draft) DRAFT=true; shift ;;
    --prerelease) PRERELEASE=true; shift ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ARTIFACT_DIR="$ROOT_DIR/release-artifacts/v$VERSION"
NOTES_FILE="$ROOT_DIR/docs/release-notes/v$VERSION.md"

if [[ ! -d "$ARTIFACT_DIR" ]]; then
  echo "ERROR: Artifacts not found: $ARTIFACT_DIR. Run scripts/build_release.sh $VERSION first." >&2
  exit 1
fi
if [[ ! -f "$NOTES_FILE" ]]; then
  echo "ERROR: Release notes not found: $NOTES_FILE" >&2
  exit 1
fi

ASSETS=( )
while IFS= read -r -d '' f; do ASSETS+=("$f"); done < <(find "$ARTIFACT_DIR" -maxdepth 1 -type f -print0)
if [[ ${#ASSETS[@]} -eq 0 ]]; then
  echo "ERROR: No files in $ARTIFACT_DIR to upload" >&2
  exit 1
fi

CMD=(gh release create "v$VERSION" "${ASSETS[@]}" -t "v$VERSION" -n "$(cat "$NOTES_FILE")")
"$DRAFT" && CMD+=(--draft) || true
"$PRERELEASE" && CMD+=(--prerelease) || true

echo "==> Creating GitHub Release v$VERSION"
echo "Repo: ${GH_REPO:-current}"
"${CMD[@]}"

echo "\n==> Release created: v$VERSION"

