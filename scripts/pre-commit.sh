#!/usr/bin/env sh
set -eu

./gradlew ktlintFormat detekt
