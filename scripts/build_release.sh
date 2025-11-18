#!/usr/bin/env bash
set -euo pipefail

# Build backend jar and frontend dist, assemble release artifacts, and create checksums.
# Usage:
#   scripts/build_release.sh [VERSION]
# If VERSION not provided, the script tries to infer it from backend/pom.xml and frontend/package.json

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

infer_backend_version() {
  awk '
    /<artifactId>enterprise-file-manager<\/artifactId>/ { hit=1; next }
    hit && match($0, /<version>([^<]+)<\/version>/, m) { print m[1]; exit }
  ' backend/pom.xml
}

infer_frontend_version() {
  sed -n 's/^[[:space:]]*"version"[[:space:]]*:[[:space:]]*"\([^"]\+\)".*/\1/p' frontend/package.json | head -n1
}

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
  bv="$(infer_backend_version || true)"
  fv="$(infer_frontend_version || true)"
  if [[ -n "$bv" && -n "$fv" && "$bv" == "$fv" ]]; then
    VERSION="$bv"
  elif [[ -n "$bv" ]]; then
    VERSION="$bv"
  elif [[ -n "$fv" ]]; then
    VERSION="$fv"
  else
    echo "ERROR: Unable to infer version. Please pass VERSION explicitly." >&2
    exit 1
  fi
fi

echo "==> Using version: $VERSION"

ARTIFACT_DIR="release-artifacts/v$VERSION"
mkdir -p "$ARTIFACT_DIR"

echo "==> Building backend (Maven)"
mvn -f backend/pom.xml clean package -DskipTests

JAR_PATH=$(ls -1 backend/target/*.jar 2>/dev/null | grep -Ev '(sources|javadoc)\.jar$' | head -n1 || true)
if [[ -z "$JAR_PATH" ]]; then
  echo "ERROR: No backend jar found in backend/target" >&2
  exit 1
fi
JAR_NAME="$(basename "$JAR_PATH")"
cp -f "$JAR_PATH" "$ARTIFACT_DIR/enterprise-file-manager-$VERSION.jar" || cp -f "$JAR_PATH" "$ARTIFACT_DIR/$JAR_NAME"

echo "==> Building frontend (npm)"
npm --prefix frontend ci
npm --prefix frontend run build

echo "==> Zipping frontend dist"
DIST_ZIP="$ARTIFACT_DIR/frontend-dist-$VERSION.zip"
(cd frontend/dist && zip -qr "$ROOT_DIR/$DIST_ZIP" .)

echo "==> Generating checksums"
(
  cd "$ARTIFACT_DIR"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum * > "checksums-v$VERSION.sha256"
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 * > "checksums-v$VERSION.sha256"
  else
    echo "WARN: sha256sum/shasum not found; skipping checksums" >&2
  fi
)

echo "\n==> Done. Artifacts: $ARTIFACT_DIR"
ls -lh "$ARTIFACT_DIR"
echo "\nNext steps:"
echo "  1) Create/verify git tag:   git tag -a v$VERSION -m 'release: v$VERSION' && git push origin v$VERSION"
echo "  2) Create GitHub Release:   scripts/gh_release.sh v$VERSION"
echo "     (Requires GitHub CLI 'gh' and authenticated account)"

