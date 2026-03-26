#!/usr/bin/env sh
set -eu

repo_root=$(git rev-parse --show-toplevel)
pre_commit_hook_path="$repo_root/.git/hooks/pre-commit"
pre_push_hook_path="$repo_root/.git/hooks/pre-push"

cat > "$pre_commit_hook_path" <<'EOF'
#!/usr/bin/env sh
set -eu

exec "$(git rev-parse --show-toplevel)/scripts/pre-commit.sh"
EOF

cat > "$pre_push_hook_path" <<'EOF'
#!/usr/bin/env sh
set -eu

exec "$(git rev-parse --show-toplevel)/scripts/pre-push.sh"
EOF

chmod +x "$pre_commit_hook_path" "$pre_push_hook_path"

printf '%s\n' '[hook-install] Installed pre-commit hook.'
printf '%s\n' '[hook-install] It now forwards to scripts/pre-commit.sh'
printf '%s\n' '[hook-install] Installed pre-push hook.'
printf '%s\n' '[hook-install] It now forwards to scripts/pre-push.sh'
