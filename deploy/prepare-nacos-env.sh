#!/bin/sh

set -eu

ENV_FILE="${1:-.env}"

umask 077

if ! command -v openssl >/dev/null 2>&1; then
    printf '%s\n' "openssl is required to generate deployment credentials." >&2
    exit 1
fi

if [ -L "$ENV_FILE" ]; then
    printf 'Refusing to update symbolic link: %s\n' "$ENV_FILE" >&2
    exit 1
fi

if [ -e "$ENV_FILE" ] && [ ! -f "$ENV_FILE" ]; then
    printf 'Environment path is not a regular file: %s\n' "$ENV_FILE" >&2
    exit 1
fi

if [ ! -e "$ENV_FILE" ]; then
    : >"$ENV_FILE"
fi

chmod 600 "$ENV_FILE"

has_key() {
    grep -q "^$1=" "$ENV_FILE"
}

has_empty_value() {
    grep -q "^$1=$" "$ENV_FILE"
}

append_if_missing() {
    key="$1"
    value="$2"

    if has_key "$key"; then
        if has_empty_value "$key"; then
            printf '%s is present but empty in %s; set it explicitly.\n' \
                "$key" \
                "$ENV_FILE" \
                >&2
            return 1
        fi

        printf 'Keeping existing %s in %s.\n' "$key" "$ENV_FILE"
        return 0
    fi

    printf '\n%s=%s\n' "$key" "$value" >>"$ENV_FILE"
    printf 'Added %s to %s.\n' "$key" "$ENV_FILE"
}

append_if_missing \
    NACOS_ADMIN_PASSWORD \
    "$(openssl rand -hex 24)"

append_if_missing \
    NACOS_AUTH_TOKEN \
    "$(openssl rand -base64 48 | tr -d '\r\n')"

append_if_missing \
    NACOS_AUTH_IDENTITY_KEY \
    "$(openssl rand -hex 24)"

append_if_missing \
    NACOS_AUTH_IDENTITY_VALUE \
    "$(openssl rand -hex 24)"

append_if_missing \
    GRAFANA_ADMIN_PASSWORD \
    "$(openssl rand -hex 24)"

chmod 600 "$ENV_FILE"
