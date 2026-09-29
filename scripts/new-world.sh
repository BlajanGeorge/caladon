#!/usr/bin/env bash
# Creates a world and opens it for play, against a backend running locally.
#
#   scripts/new-world.sh "Caladon III"
#
# Only an administrator may create a world, so the script keeps one of its own: a development account
# it registers the first time it runs and promotes in the database. Nothing here is a real credential
# and none of it belongs anywhere but a local machine.
set -euo pipefail

NAME="${1:?usage: scripts/new-world.sh "'"<world name>"'"}"
API="${CALADON_API:-http://localhost:8080/api/v1}"
PSQL=(psql -h "${CALADON_DB_HOST:-localhost}" -p "${CALADON_DB_PORT:-5433}" -U "${CALADON_DB_USER:-caladon}" -d "${CALADON_DB_NAME:-caladon}" -qtA)
export PGPASSWORD="${CALADON_DB_PASSWORD:-caladon}"

# The script's own administrator. Development only: the backend refuses these hosts anywhere else.
ADMIN_EMAIL='worldsmith@seed.test'
ADMIN_NICK='worldsmith'
ADMIN_PASS='worldsmith-dev-only'

say() { printf '%s\n' "$*" >&2; }

# Register once; an account that already exists simply fails here, which is fine.
curl -fsS -X POST "$API/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"nickname\":\"$ADMIN_NICK\",\"password\":\"$ADMIN_PASS\"}" >/dev/null 2>&1 \
  && say "Registered $ADMIN_NICK" || say "Using the existing $ADMIN_NICK"

"${PSQL[@]}" -c "UPDATE users SET role = 'ADMINISTRATOR' WHERE email = '$ADMIN_EMAIL';" >/dev/null

TOKEN=$(curl -fsS -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASS\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { say "Could not sign in as $ADMIN_NICK"; exit 1; }

# Creating a world generates its terrain, city slots and barbarian villages, which takes a moment.
say "Generating ${NAME}..."
CREATED=$(curl -fsS -X POST "$API/admin/worlds" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" -d "{\"name\":\"$NAME\"}")
ID=$(printf '%s' "$CREATED" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
[ -n "$ID" ] || { say "Could not create the world: $CREATED"; exit 1; }

# A new world is a draft, and a draft is invisible to players until it is opened.
curl -fsS -X POST "$API/admin/worlds/$ID/approve" -H "Authorization: Bearer $TOKEN" >/dev/null
say "World $ID '$NAME' is open for play."
