#!/usr/bin/env bash
# SessionStart hook helper: bind the Trello board id into Claude's context.
#
# Emits a SessionStart `additionalContext` payload naming the active Trello
# board, so every mcp__trello__ call targets the right board without the user
# having to restate it. Reads $TRELLO_BOARD_ID (set in the shell that launches
# `claude`, alongside TRELLO_API_KEY / TRELLO_TOKEN); falls back to parsing a
# local .env / trello.env if the var is not exported.
#
# The board id is NOT secret — the secrets are TRELLO_API_KEY / TRELLO_TOKEN,
# which this script never reads or prints. See docs/trello-integration.md.
set -euo pipefail

# Hooks run from the launch cwd; normalise to the project root so the .env
# fallback resolves, then stay quiet on any failure (a hook must never block).
cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || true

board="${TRELLO_BOARD_ID:-}"

# Fallback: pull TRELLO_BOARD_ID's value (only) from a local env file.
if [[ -z "$board" ]]; then
  for f in ./.env ./trello.env; do
    [[ -f "$f" ]] || continue
    # `|| true`: no matching line is normal, not an error (don't trip set -e).
    board="$(grep -E '^[[:space:]]*(export[[:space:]]+)?TRELLO_BOARD_ID=' "$f" 2>/dev/null \
      | tail -n1 \
      | sed -E 's/^[[:space:]]*(export[[:space:]]+)?TRELLO_BOARD_ID=//; s/^["'\'']//; s/["'\'']$//' || true)"
    [[ -n "$board" ]] && break
  done
fi

# No board configured anywhere -> inject nothing, exit clean.
[[ -z "$board" ]] && exit 0

# Board ids are alphanumeric, so the JSON below needs no escaping.
ctx="Trello board binding (DigitalWallet): the active Trello board for this project is board id ${board} (from TRELLO_BOARD_ID). For any mcp__trello__ tool that needs a board (get_board, get_board_lists, get_board_cards, get_board_labels, get_board_members, create_list, create_label, board-scoped search), pass this board id and resolve list/card ids under it, unless the user explicitly names a different board."

printf '{"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"%s"}}\n' "$ctx"
