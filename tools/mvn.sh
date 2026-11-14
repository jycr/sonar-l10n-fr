#!/usr/bin/env sh
# Runs Maven inside a container, so no local Maven/JDK install is required.
#
# JDK 21 is not arbitrary: sonar-core and sonar-testing-harness 26.11 are compiled
# with class file major version 65 (Java 21). An older JDK cannot read them. The
# plugin itself still targets Java 17 (maven.compiler.release in pom.xml), so the
# jar keeps loading on older servers.
#
# The whole repository is mounted, .git included: maven-git-versioning-extension
# derives the artifact version from git metadata and fails without it.
#
# Usage: tools/mvn.sh clean verify
set -eu

REPO_ROOT=$(cd "$(dirname "$0")/.." && pwd)

# Git Bash / MSYS rewrites both the mount source and any argument that looks like
# a Unix path, turning -w /workspace into -w C:/Program Files/Git/workspace.
# MSYS_NO_PATHCONV disables the argument rewriting; cygpath fixes the mount source.
if command -v cygpath >/dev/null 2>&1; then
  REPO_ROOT=$(cygpath -w "$REPO_ROOT")
  MSYS_NO_PATHCONV=1
  export MSYS_NO_PATHCONV
fi

exec docker run --rm \
  -v "$REPO_ROOT":/workspace \
  -w /workspace \
  -v sonar-l10n-fr-m2:/root/.m2 \
  maven:3.9-eclipse-temurin-21 \
  mvn "$@"
