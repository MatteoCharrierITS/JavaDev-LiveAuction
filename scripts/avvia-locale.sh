#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ ! -f .env ]; then echo 'Manca .env: configura prima il database locale.' >&2; exit 1; fi
set -a
source .env
set +a
if [ "${1:-}" = '--ip' ]; then
  if [ -z "${2:-}" ]; then echo 'Uso: bash scripts/avvia-locale.sh [--ip IP_DEL_MAC]' >&2; exit 1; fi
  demo_ip="$2"
elif [ -x /sbin/route ] && command -v ipconfig >/dev/null; then
  demo_interface=$(/sbin/route -n get default | awk '/interface:/{print $2; exit}')
  demo_ip=$(ipconfig getifaddr "$demo_interface" || true)
else
  echo 'Indirizzo non rilevabile: usa --ip IP_DEL_SERVER.' >&2; exit 1
fi
if [[ ! "$demo_ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
  echo 'Serve un indirizzo IPv4 locale; usa --ip con quello della rete hotspot.' >&2; exit 1
fi
IFS=. read -r -a demo_octets <<< "$demo_ip"
for demo_octet in "${demo_octets[@]}"; do
  if (( 10#$demo_octet > 255 )); then
    echo 'Indirizzo IPv4 non valido.' >&2; exit 1
  fi
done
for demo_port in 8081 8082; do
  if lsof -nP -iTCP:"$demo_port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Porta $demo_port già occupata. Nessun processo è stato terminato." >&2; exit 1
  fi
done
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21)
fi
demo_java="$JAVA_HOME/bin/java"
"$demo_java" -version
export JAVA_HOME
export SERVER_ADDRESS=0.0.0.0
export SERVER_SERVLET_SESSION_COOKIE_SECURE=false
export LIVEAUCTION_PRODUCER_URL=http://127.0.0.1:8081/api/v1
export LIVEAUCTION_PRODUCER_WS_URL=auto
export LIVEAUCTION_WS_ALLOWED_ORIGINS="http://localhost:8082,http://127.0.0.1:8082,http://$demo_ip:8082"
export EMAIL_ENABLED=false
umask 077
mkdir -p .local/runtime
# Il volume persistente non viene mai cancellato; il database deve già esistere.
docker compose up -d postgres
sh producer/mvnw -f producer/pom.xml -DskipTests package > .local/runtime/build-producer.log 2>&1
sh producer/mvnw -f consumer/pom.xml -DskipTests package > .local/runtime/build-consumer.log 2>&1
wait_health() {
  local demo_port="$1" demo_attempt demo_body
  for demo_attempt in $(seq 1 60); do
    demo_body=$(curl --silent --fail --max-time 2 "http://127.0.0.1:$demo_port/actuator/health" || true)
    case "$demo_body" in *'"status":"UP"'*) return 0;; esac
    sleep 1
  done
  echo "Servizio $demo_port non pronto: controlla .local/runtime/*.log" >&2
  return 1
}
cleanup() {
  trap - EXIT INT TERM
  bash scripts/ferma-locale.sh
}
trap cleanup EXIT
trap 'exit 0' INT TERM
nohup "$demo_java" -jar producer/target/liveauction-producer-0.0.1-SNAPSHOT.jar > .local/runtime/producer.log 2>&1 < /dev/null &
demo_producer_pid=$!
echo "$!" > .local/runtime/producer.pid
wait_health 8081
nohup "$demo_java" -jar consumer/target/liveauction-consumer-0.0.1-SNAPSHOT.jar > .local/runtime/consumer.log 2>&1 < /dev/null &
demo_consumer_pid=$!
echo "$!" > .local/runtime/consumer.pid
wait_health 8082
echo "Pronto: http://$demo_ip:8082/marketplace"
echo 'Tutti i PC devono essere sullo stesso hotspot. Non cambiare rete durante la prova.'
echo 'Per fermare: bash scripts/ferma-locale.sh (dati e volume conservati).'
echo 'Lascia questo terminale aperto; Ctrl+C ferma anche PostgreSQL senza cancellare dati.'
wait "$demo_producer_pid" "$demo_consumer_pid"
