#!/usr/bin/env sh
set -eu

./gradlew ktlintCheck detekt lint test
