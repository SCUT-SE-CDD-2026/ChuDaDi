#!/usr/bin/env sh
set -eu

printf '%s\n' '[pre-commit] Running pre-commit hook: ktlintFormat + detekt'

if ./gradlew ktlintFormat detekt --daemon --console=plain; then
    printf '%s\n' '[pre-commit] Hook passed.'
else
    printf '%s\n' '[pre-commit] Hook failed. Fix the reported issues, then retry commit.'
    printf '%s\n' '[pre-commit] Useful commands: ./gradlew.bat ktlintFormat | ./gradlew.bat detekt | ./gradlew.bat lintFix'
    exit 1
fi
