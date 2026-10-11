#!/usr/bin/env bash
# Install the pinned official modules for CI and Linux development.
set -euo pipefail
cache="${RUOYI_SOURCE_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/simrecrut/ruoyi-source}"
revision=a51a838b71b446ea27256900efe7ed2faa2a02fd
if [ ! -d "$cache/.git" ]; then git clone --no-checkout https://github.com/yangzongzhuan/RuoYi-Vue.git "$cache"; fi
git -C "$cache" checkout --detach "$revision"
"${MAVEN_CMD:-mvn}" -f "$cache/pom.xml" --batch-mode -pl ruoyi-admin -am install -DskipTests -Dspring-boot.repackage.skip=true
