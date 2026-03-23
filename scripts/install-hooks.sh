#!/usr/bin/env sh
set -eu

repo_root=$(git rev-parse --show-toplevel)
hook_path="$repo_root/.git/hooks/pre-commit"

cat > "$hook_path" <<'EOF'
#!/usr/bin/env sh
set -eu

exec "$(git rev-parse --show-toplevel)/scripts/pre-commit.sh"
EOF

chmod +x "$hook_path"

printf '%s\n' '[hook-install] Installed pre-commit hook.'
printf '%s\n' '[hook-install] It now forwards to scripts/pre-commit.sh'
