#!/usr/bin/env sh
set -eu

printf '%s\n' '[pre-push] Running pre-push hook: lint + test'

if ./gradlew lint test; then
    printf '%s\n' '[pre-push] Hook passed.'
else
    printf '%s\n' '[pre-push] Hook failed. Fix the reported issues, then retry push.'
    printf '%s\n' '[pre-push] Useful commands: ./gradlew.bat lint | ./gradlew.bat test'
    exit 1
fi
