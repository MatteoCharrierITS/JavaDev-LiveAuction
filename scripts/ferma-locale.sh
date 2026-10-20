#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
for demo_service in consumer producer; do
  demo_pidfile=".local/runtime/$demo_service.pid"
  if [ -f "$demo_pidfile" ]; then
    demo_pid=$(< "$demo_pidfile")
    if [[ "$demo_pid" =~ ^[0-9]+$ ]] && kill -0 "$demo_pid" 2>/dev/null; then
      demo_command=$(ps -p "$demo_pid" -o command=)
      case "$demo_command" in
        *"-jar $demo_service/target/liveauction-$demo_service-0.0.1-SNAPSHOT.jar"*)
          kill "$demo_pid"
          for demo_attempt in $(seq 1 30); do
            kill -0 "$demo_pid" 2>/dev/null || break
            sleep 1
          done
          if kill -0 "$demo_pid" 2>/dev/null; then
            echo "$demo_service ancora attivo: non fermo PostgreSQL." >&2; exit 1
          fi;;
        *) echo "PID $demo_pid non riconosciuto: nessun processo terminato." >&2; exit 1;;
      esac
    fi
  fi
done
for demo_port in 8081 8082; do
  if lsof -nP -iTCP:"$demo_port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Porta $demo_port ancora in uso: non fermo PostgreSQL." >&2; exit 1
  fi
done
docker compose stop postgres
echo 'Servizi spenti. Database e volume conservati.'
