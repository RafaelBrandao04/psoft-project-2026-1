#!/bin/sh
# Executar como root no Ubuntu na VM do DEI, a partir desta pasta.
set -eu

# Portas privadas de escuta no servidor; não são as portas públicas do proxy/NAT.
if [ "$#" -ne 0 ] && [ "$#" -ne 3 ]; then
    echo "Usage: setup-environments.sh [DEV_PORT UAT_PORT PROD_PORT]" >&2
    exit 1
fi
dev_port=${1:-8081}
uat_port=${2:-8082}
prod_port=${3:-8080}
for port in "$dev_port" "$uat_port" "$prod_port"; do
    case "$port" in
        ''|*[!0-9]*) echo "Porta inválida: $port" >&2; exit 1;;
    esac
    if [ "${#port}" -gt 5 ] || [ "$port" -lt 1024 ] || [ "$port" -gt 65535 ]; then
        echo "Usar portas privadas entre 1024 e 65535: $port" >&2
        exit 1
    fi
done
if [ "$dev_port" -eq "$uat_port" ] || [ "$dev_port" -eq "$prod_port" ] || [ "$uat_port" -eq "$prod_port" ]; then
    echo "As três portas privadas devem ser diferentes." >&2
    exit 1
fi

test "$(id -u)" -eq 0

# Garante a conta de deploy caso ainda não exista
if ! id deploy >/dev/null 2>&1; then
    useradd -m -s /bin/bash deploy
fi

# Garante a presença do Java 17 e curl
if [ ! -x /usr/bin/java ] || [ ! -x /usr/bin/curl ]; then
    DEBIAN_FRONTEND=noninteractive apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-17-jre-headless curl
fi

install -m 644 psoft-g1@.service /etc/systemd/system/psoft-g1@.service
install -d -m 755 /etc/psoft-g1
for name in dev uat prod; do
    case "$name" in dev) port=$dev_port;; uat) port=$uat_port;; prod) port=$prod_port;; esac
    if ! id "psoft-g1-$name" >/dev/null 2>&1; then
        useradd --system --no-create-home --shell /usr/sbin/nologin "psoft-g1-$name"
    fi
    install -d -o deploy -g "psoft-g1-$name" -m 755 "/opt/psoft-g1/$name"
    if [ ! -e "/etc/psoft-g1/$name.env" ]; then
        printf 'DEMO_ENVIRONMENT=%s\nPORT=%s\nSPRING_PROFILES_ACTIVE=%s\n' "$name" "$port" "$name" > "/etc/psoft-g1/$name.env"
        chmod 644 "/etc/psoft-g1/$name.env"
    else
        echo "Configuração preservada: /etc/psoft-g1/$name.env (editar PORT para alterar a porta)."
    fi
done

# Instala a regra sudoers limitada para o utilizador deploy
if [ -f psoft-g1.sudoers ]; then
    visudo -cf psoft-g1.sudoers
    install -m 440 psoft-g1.sudoers /etc/sudoers.d/psoft-g1
fi

systemctl daemon-reload
systemctl enable psoft-g1@dev psoft-g1@uat psoft-g1@prod
echo "Ambientes DEV ($dev_port), UAT ($uat_port) e PROD ($prod_port) preparados com sucesso via systemd."

