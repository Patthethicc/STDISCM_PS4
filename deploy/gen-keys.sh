#!/usr/bin/env bash
# Generates the RS256 key pair used for JWTs.
#   deploy/keys/private.pem  -> auth-service ONLY (signs tokens)
#   deploy/keys/public.pem   -> every service (verifies tokens)
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)/keys"
mkdir -p "$DIR"

if [[ -f "$DIR/private.pem" && "${1:-}" != "--force" ]]; then
  echo "Keys already exist in $DIR (use --force to regenerate)."
  exit 0
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$DIR/private.pem"
openssl pkey -in "$DIR/private.pem" -pubout -out "$DIR/public.pem"
chmod 600 "$DIR/private.pem"
chmod 644 "$DIR/public.pem"
echo "Wrote $DIR/private.pem and $DIR/public.pem"
