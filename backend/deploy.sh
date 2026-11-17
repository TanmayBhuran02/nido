#!/usr/bin/env bash
# Run on the Oracle VM from the backend/ folder.
set -euo pipefail

DATA_DIR="${DATA_DIR:-$HOME/nido-data}"
mkdir -p "$DATA_DIR/db" "$DATA_DIR/media"

if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
  echo "Docker installed. Log out, log in, run this script again."
  exit 0
fi

if [ ! -f .env ]; then
  cp .env.example .env
  sed -i "s|JWT_SECRET=.*|JWT_SECRET=$(openssl rand -hex 32)|" .env
  sed -i "s|INVITE_CODE=.*|INVITE_CODE=$(openssl rand -hex 6)|" .env
  echo "Made .env. Your invite code:"
  grep INVITE_CODE .env
fi

# open firewall (Oracle Ubuntu images block by default)
sudo iptables -C INPUT -p tcp --dport 80 -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 6 -p tcp --dport 80 -j ACCEPT
sudo iptables -C INPUT -p tcp --dport 443 -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 6 -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save 2>/dev/null || true

docker compose up -d --build
echo "Done. Test: curl https://\$(grep -m1 duckdns Caddyfile | awk '{print \$1}')/health"
