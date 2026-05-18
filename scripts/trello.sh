#!/usr/bin/env bash
# Trello REST API helper. Source trello.env first:
#   source ./trello.env
#   ./scripts/trello.sh <command> [args...]
#
# Commands:
#   boards                          List all your boards (id, name, url)
#   board    <board_id>             Show board details
#   lists    <board_id>             List columns/lists in a board
#   cards    <list_id>              List cards in a list
#   card     <card_id>              Show card details (incl. desc, labels, due)
#   search   <query>                Search across your boards/cards
#   create   <list_id> <name> [desc] Create a card
#   move     <card_id> <list_id>    Move a card to another list
#   rename   <card_id> <new_name>   Rename a card
#   desc     <card_id> <text>       Set card description
#   comment  <card_id> <text>       Add a comment
#   close    <card_id>              Archive (close) a card
#   delete   <card_id>              Permanently delete a card
#   members  <board_id>             List members on a board
#   labels   <board_id>             List labels on a board
#   assign   <card_id> <member_id>  Add member to card
#   due      <card_id> <ISO_DATE>   Set due date (e.g. 2026-05-20T17:00:00Z)
#   raw      <METHOD> <path> [-d k=v ...]  Pass-through to api.trello.com/1/<path>
#
# All output is JSON; pipe to jq if jq is not auto-applied.

set -euo pipefail

API="https://api.trello.com/1"

require_env() {
  if [[ -z "${TRELLO_KEY:-}" || -z "${TRELLO_TOKEN:-}" ]]; then
    echo "ERROR: TRELLO_KEY and TRELLO_TOKEN must be set. Run: source ./trello.env" >&2
    exit 2
  fi
}

auth() {
  echo "key=$TRELLO_KEY&token=$TRELLO_TOKEN"
}

# GET <path> [extra_query]
api_get() {
  local path="$1"
  local extra="${2:-}"
  local sep="?"
  [[ -n "$extra" ]] && extra="&$extra"
  curl -fsSL "${API}${path}${sep}$(auth)${extra}"
}

# POST <path> [--data-urlencode k=v ...]
api_post() {
  local path="$1"
  shift
  curl -fsSL -X POST "${API}${path}?$(auth)" "$@"
}

# PUT <path> [--data-urlencode k=v ...]
api_put() {
  local path="$1"
  shift
  curl -fsSL -X PUT "${API}${path}?$(auth)" "$@"
}

# DELETE <path>
api_delete() {
  local path="$1"
  curl -fsSL -X DELETE "${API}${path}?$(auth)"
}

pretty() {
  if command -v jq >/dev/null 2>&1; then jq .; else cat; fi
}

usage() {
  sed -n '2,30p' "$0"
  exit 1
}

main() {
  local cmd="${1:-}"
  [[ -z "$cmd" ]] && usage
  shift || true

  case "$cmd" in
    -h|--help|help) usage ;;
  esac

  require_env

  case "$cmd" in
    boards)
      api_get "/members/me/boards" "fields=name,url,closed" | pretty
      ;;
    board)
      api_get "/boards/$1" | pretty
      ;;
    lists)
      api_get "/boards/$1/lists" "fields=name,closed,pos" | pretty
      ;;
    cards)
      api_get "/lists/$1/cards" "fields=name,desc,idMembers,labels,due,url,shortLink" | pretty
      ;;
    card)
      api_get "/cards/$1" "fields=name,desc,idList,idBoard,idMembers,labels,due,url,shortLink" | pretty
      ;;
    search)
      api_get "/search" "query=$(printf '%s' "$1" | jq -sRr @uri)&modelTypes=cards,boards" | pretty
      ;;
    create)
      local list_id="$1" name="$2" desc="${3:-}"
      api_post "/cards" \
        --data-urlencode "idList=$list_id" \
        --data-urlencode "name=$name" \
        --data-urlencode "desc=$desc" | pretty
      ;;
    move)
      api_put "/cards/$1" --data-urlencode "idList=$2" | pretty
      ;;
    rename)
      api_put "/cards/$1" --data-urlencode "name=$2" | pretty
      ;;
    desc)
      api_put "/cards/$1" --data-urlencode "desc=$2" | pretty
      ;;
    comment)
      api_post "/cards/$1/actions/comments" --data-urlencode "text=$2" | pretty
      ;;
    close)
      api_put "/cards/$1" --data-urlencode "closed=true" | pretty
      ;;
    delete)
      api_delete "/cards/$1"
      ;;
    members)
      api_get "/boards/$1/members" "fields=fullName,username" | pretty
      ;;
    labels)
      api_get "/boards/$1/labels" "fields=name,color" | pretty
      ;;
    assign)
      api_post "/cards/$1/idMembers" --data-urlencode "value=$2" | pretty
      ;;
    due)
      api_put "/cards/$1" --data-urlencode "due=$2" | pretty
      ;;
    raw)
      local method="$1" path="$2"; shift 2
      curl -fsSL -X "$method" "${API}/${path#/}?$(auth)" "$@" | pretty
      ;;
    *)
      echo "Unknown command: $cmd" >&2
      usage
      ;;
  esac
}

main "$@"
