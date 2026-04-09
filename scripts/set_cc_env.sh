#!/bin/sh

LINK_PATH=".claude/skills"
TARGET_PATH="../.agents/skills"

rm -rf "$LINK_PATH"

mkdir -p .claude
ln -s "$TARGET_PATH" "$LINK_PATH"