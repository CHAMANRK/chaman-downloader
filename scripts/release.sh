#!/usr/bin/env bash
# GitHub Release banao: wahi APK jo CI ne us tag ke commit par banayi (device pe test wali).
# Use:   scripts/release.sh v0.4.24 "notes"          -> normal release (latest, sirf isi ka update aata hai)
#        scripts/release.sh v0.4.26 "notes" --pre    -> pre-release (kisi ko update nahi aata)
# Zaroori: tag lag chuka aur push ho chuka ho, `gh` login ho, us commit ki CI build success ho.
set -euo pipefail
REPO="CHAMANRK/chaman-downloader"
TAG="${1:-}"
NOTES="${2:-}"
PRE="${3:-}"
[ -n "$TAG" ] || { echo "Use: scripts/release.sh v0.4.24 \"notes\" [--pre]"; exit 1; }
[ -n "$NOTES" ] || NOTES="$TAG"

git rev-parse -q --verify "refs/tags/$TAG" >/dev/null || { echo "Tag $TAG local me nahi hai. Pehle tag lagao."; exit 1; }
git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null || { echo "Tag $TAG GitHub pe push nahi hua."; exit 1; }
SHA=$(git rev-list -n 1 "$TAG")

RUN=$(gh api "repos/$REPO/actions/runs?head_sha=$SHA&status=success" --jq '[.workflow_runs[] | select(.name=="Build APK")][0].id')
if [ -z "$RUN" ] || [ "$RUN" = "null" ]; then
  echo "Is tag ($SHA) ki koi successful 'Build APK' run nahi mili."
  exit 1
fi
echo "CI run: $RUN"

TMP=$(mktemp -d)
gh run download "$RUN" -R "$REPO" -n chaman-downloader-debug -D "$TMP"
cat "$TMP/BUILD_INFO.txt"
if grep -q "signing: debug" "$TMP/BUILD_INFO.txt"; then
  echo "Ye APK debug key se bani hai. Release me mat daalo (update install nahi hoga). Secrets check karo."
  exit 1
fi

cp "$TMP/chaman-downloader-debug.apk" "$TMP/Chaman-Downloader.apk"
if [ "$PRE" = "--pre" ]; then
  KIND="--prerelease"
else
  KIND="--latest"
fi
gh release create "$TAG" "$TMP/Chaman-Downloader.apk" "$TMP/BUILD_INFO.txt" -R "$REPO" --title "$TAG" --notes "$NOTES" "$KIND"
echo "Release ban gayi: $TAG ($KIND)"
