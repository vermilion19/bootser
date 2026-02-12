#!/usr/bin/env bash
set -euo pipefail

PRUNE=false
SKIP_PULL=false

usage() {
  cat <<'EOF'
Usage: ./scripts/sync-skills.sh [--prune] [--skip-pull]

Options:
  --prune      Remove local skills not present in .claude/skills
  --skip-pull  Skip git pull and run sync only
  -h, --help   Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --prune)
      PRUNE=true
      ;;
    --skip-pull)
      SKIP_PULL=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SOURCE_ROOT="$REPO_ROOT/.claude/skills"
TARGET_ROOT="$HOME/.codex/skills"

if [[ ! -d "$SOURCE_ROOT" ]]; then
  echo "Source skills directory not found: $SOURCE_ROOT" >&2
  exit 1
fi

contains_name() {
  local needle="$1"
  shift
  local item
  for item in "$@"; do
    if [[ "$item" == "$needle" ]]; then
      return 0
    fi
  done
  return 1
}

capitalize() {
  local s="$1"
  if [[ -z "$s" ]]; then
    printf ''
    return
  fi
  local first="${s:0:1}"
  local rest="${s:1}"
  printf '%s%s' "$(printf '%s' "$first" | tr '[:lower:]' '[:upper:]')" "$rest"
}

get_display_name() {
  local skill_name="$1"
  local IFS='-'
  local parts=()
  read -r -a parts <<< "$skill_name"

  local out=""
  local idx=0
  local part lower formatted

  for part in "${parts[@]}"; do
    [[ -z "$part" ]] && continue
    lower="$(printf '%s' "$part" | tr '[:upper:]' '[:lower:]')"

    case "$lower" in
      api|ui|db|pr|sql|mcp|ci|cli)
        formatted="$(printf '%s' "$lower" | tr '[:lower:]' '[:upper:]')"
        ;;
      and|or|to|up|with)
        if (( idx > 0 )); then
          formatted="$lower"
        else
          formatted="$(capitalize "$lower")"
        fi
        ;;
      *)
        formatted="$(capitalize "$lower")"
        ;;
    esac

    if [[ -z "$out" ]]; then
      out="$formatted"
    else
      out="$out $formatted"
    fi
    idx=$((idx + 1))
  done

  printf '%s' "$out"
}

get_short_description() {
  local display_name="$1"
  local desc="Help with ${display_name} tasks and workflows"

  if (( ${#desc} > 64 )); then
    desc="Help with ${display_name} tasks"
  fi
  if (( ${#desc} > 64 )); then
    desc="${display_name} helper"
  fi
  if (( ${#desc} < 25 )); then
    desc="${display_name} workflows helper"
  fi
  if (( ${#desc} > 64 )); then
    desc="${desc:0:64}"
  fi

  printf '%s' "$desc"
}

escape_yaml() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  printf '%s' "$value"
}

sync_skill() {
  local src_dir="$1"
  local skill_name="$2"
  local dst_dir="$TARGET_ROOT/$skill_name"

  mkdir -p "$dst_dir"
  awk '!/^[[:space:]]*user-invocable:[[:space:]]*/' "$src_dir/SKILL.md" > "$dst_dir/SKILL.md"

  local agents_dir="$dst_dir/agents"
  mkdir -p "$agents_dir"

  local display_name short_description default_prompt
  display_name="$(get_display_name "$skill_name")"
  short_description="$(get_short_description "$display_name")"
  default_prompt="Use \`\$$skill_name to handle this task from start to finish."

  cat > "$agents_dir/openai.yaml" <<EOF
interface:
  display_name: "$(escape_yaml "$display_name")"
  short_description: "$(escape_yaml "$short_description")"
  default_prompt: "$(escape_yaml "$default_prompt")"
EOF
}

cd "$REPO_ROOT"

if [[ "$SKIP_PULL" == false ]]; then
  echo "[1/2] Running git pull..."
  git pull
fi

echo "[2/2] Syncing skills to $TARGET_ROOT..."
mkdir -p "$TARGET_ROOT"

synced=0
deleted=0
source_names=()

shopt -s nullglob
for dir in "$SOURCE_ROOT"/*; do
  [[ -d "$dir" ]] || continue
  [[ -f "$dir/SKILL.md" ]] || continue

  name="$(basename "$dir")"
  source_names+=("$name")
  sync_skill "$dir" "$name"
  synced=$((synced + 1))
done

if [[ "$PRUNE" == true ]]; then
  for dir in "$TARGET_ROOT"/*; do
    [[ -d "$dir" ]] || continue
    name="$(basename "$dir")"

    if [[ "$name" == ".system" ]]; then
      continue
    fi

    if ! contains_name "$name" "${source_names[@]}"; then
      rm -rf "$dir"
      deleted=$((deleted + 1))
    fi
  done
fi

echo "Synced $synced skill(s) from '$SOURCE_ROOT' to '$TARGET_ROOT'."
if [[ "$PRUNE" == true ]]; then
  echo "Removed $deleted stale skill(s) from target."
fi
