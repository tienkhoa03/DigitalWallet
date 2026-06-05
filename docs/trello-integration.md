# Trello integration

DigitalWallet is tracked on a Trello board, wired to Claude Code through an MCP
server plus two helper scripts. This document is the operational reference.

## Board

| | |
|---|---|
| **Name** | Personal Project - Digital Wallet |
| **Short link** | `BBVddj3q` |
| **URL** | <https://trello.com/b/BBVddj3q> |
| **Board id** | `6a2239c1f8f0ad9bd0bc5f4f` |

### Structure (seeded from the MVP master roadmap)

- **Lists (Kanban):** `Backlog` → `In Progress` → `In Review` → `Done`.
- **Labels (by area):** `Backend` (green), `Frontend` (blue), `Infra/DevOps`
  (orange), `Docs/ADR` (purple), `Security` (red), `Testing` (yellow).
- **Cards:** one per implementation phase from
  [plans/implementation-plan-mvp-master.md](plans/implementation-plan-mvp-master.md)
  — Phase 0–9 (backend) and F1–F5 (frontend), 15 cards in `Backlog`. Each card
  carries a description (goal + FR/NFR + agent/skill) and a **Tasks** checklist.

Workflow: move a card to `In Progress` when you start its `/make-plan` /
`/implement-plan` cycle, tick checklist items as you go, then `In Review` →
`Done`.

## Credentials

Stored in `.env` at the repo root (git-ignored), using the canonical names the
MCP server reads:

```
export TRELLO_API_KEY="..."
export TRELLO_TOKEN="..."
```

Template: [`../trello.env.example`](../trello.env.example). Never commit real
values — `.env`, `*.env`, and `trello.env` are all in `.gitignore`.

## MCP server

Configured in [`../.mcp.json`](../.mcp.json):

```json
{
  "mcpServers": {
    "trello": {
      "command": "node",
      "args": ["/home/ntkhoa/code/trello/dist/index.js"],
      "env": {
        "TRELLO_API_KEY": "${TRELLO_API_KEY}",
        "TRELLO_TOKEN": "${TRELLO_TOKEN}"
      }
    }
  }
}
```

- The server is a local build at `/home/ntkhoa/code/trello/` (the path is
  machine-specific; `.mcp.json` is not committed by default for that reason).
- It exposes Trello operations as `mcp__trello__*` tools (boards, lists, cards,
  labels, checklists, comments, members, search, plus a generic `trello_request`).
- `.claude/settings.json` pre-approves it via `"enabledMcpjsonServers": ["trello"]`,
  so it loads without a manual approval prompt.

### Default board binding (auto-target this board)

The MCP tools take an explicit board id on every board-scoped call — the server
has no built-in "active board". To avoid restating the board each session, a
`SessionStart` hook in `.claude/settings.json` runs
[`../scripts/trello-board-context.sh`](../scripts/trello-board-context.sh),
which reads **`TRELLO_BOARD_ID`** and injects it into Claude's context. From then
on Claude passes that board id to `get_board`, `get_board_lists`,
`get_board_cards`, `get_board_labels`, `create_list`, etc. without being asked.

```bash
export TRELLO_BOARD_ID="6a2239c1f8f0ad9bd0bc5f4f"   # this board (short link BBVddj3q)
```

- `TRELLO_BOARD_ID` is **not** a secret — only `TRELLO_API_KEY` / `TRELLO_TOKEN`
  are. Set it alongside them (in `./.env` or `~/.bashrc`).
- If the var is not exported, the hook falls back to parsing `TRELLO_BOARD_ID`
  out of `./.env` / `./trello.env` (value only — it never reads the token).
- The hook runs at session start, so a **new** `claude` session picks it up;
  open `/hooks` or restart to load it into a session that predates the change.

### Activating the MCP tools

The server reads `process.env.TRELLO_API_KEY` / `TRELLO_TOKEN` directly (no
dotenv), and `.mcp.json` substitutes `${...}` from **Claude Code's own
environment**. So the variables must be exported in the shell that launches
`claude`:

```bash
set -a; source ./.env; set +a   # export TRELLO_API_KEY + TRELLO_TOKEN
claude                          # mcp__trello__* tools now load
```

(Or add the two `export` lines to `~/.bashrc`.) Inside an already-running
session, reconnect MCP servers (`/mcp`) or restart after exporting.

## Helper scripts (no MCP reload needed)

Both read credentials from the environment; `scripts/seed_trello_board.py` also
falls back to parsing `./.env` at runtime. Secret values are never printed.

| Script | Purpose |
|---|---|
| [`../scripts/trello.sh`](../scripts/trello.sh) | Thin curl CLI: `boards`, `lists`, `cards`, `create`, `move`, `comment`, `raw`, … Accepts `TRELLO_API_KEY` (or legacy `TRELLO_KEY`) + `TRELLO_TOKEN`. |
| [`../scripts/seed_trello_board.py`](../scripts/seed_trello_board.py) | **Idempotent** seeder: ensures lists, area labels, and the 15 phase cards + checklists. Re-running makes no duplicates. |

```bash
# Re-seed / repair the board (safe to run repeatedly):
python3 scripts/seed_trello_board.py BBVddj3q

# Ad-hoc queries (export creds first, e.g. set -a; . ./.env; set +a):
bash scripts/trello.sh lists BBVddj3q
bash scripts/trello.sh cards <list_id>
```

### Extending the board

Edit the `CARDS` list in `scripts/seed_trello_board.py` (add a card dict with
`name`, `labels`, `desc`, `tasks`) and re-run the seeder — existing cards are
matched by exact name and left untouched, so only the new card is created.
