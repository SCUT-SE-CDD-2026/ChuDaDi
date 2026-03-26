#!/usr/bin/env sh
set -eu

staged_files=$(git diff --cached --name-only --diff-filter=ACMR)

if [ -z "$staged_files" ]; then
    printf '%s\n' '[pre-commit] No staged files. Skip hook.'
    exit 0
fi

needs_ktlint_check=false

for file in $staged_files; do
    case "$file" in
        *.kt|*.kts)
            needs_ktlint_check=true
            break
            ;;
    esac
done

if [ "$needs_ktlint_check" = false ]; then
    printf '%s\n' '[pre-commit] No staged Kotlin files. Skip ktlint check.'
    exit 0
fi

printf '%s\n' '[pre-commit] Running pre-commit hook: ktlintCheck'

if ./gradlew ktlintCheck --daemon --console=plain; then
    printf '%s\n' '[pre-commit] Hook passed.'
else
    printf '%s\n' '[pre-commit] Hook failed. Fix the reported issues, then retry commit.'
    printf '%s\n' '[pre-commit] Useful commands: ./gradlew.bat ktlintFormat | ./gradlew.bat ktlintCheck'
    exit 1
fi
