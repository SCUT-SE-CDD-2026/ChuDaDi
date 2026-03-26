#!/usr/bin/env sh
set -eu

printf '%s\n' '[pre-push] Running pre-push hook: detekt + lintDebug + testDebugUnitTest'

if ./gradlew detekt lintDebug testDebugUnitTest --daemon --console=plain; then
    printf '%s\n' '[pre-push] Hook passed.'
else
    printf '%s\n' '[pre-push] Hook failed. Fix the reported issues, then retry push.'
    printf '%s\n' '[pre-push] Useful commands: ./gradlew.bat detekt | ./gradlew.bat lintDebug | ./gradlew.bat testDebugUnitTest'
    exit 1
fi
