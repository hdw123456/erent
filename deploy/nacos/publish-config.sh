#!/bin/sh

set -eu

NACOS_SERVER_URL="${NACOS_SERVER_URL:-http://nacos:8848/nacos}"
NACOS_USERNAME="${NACOS_USERNAME:-nacos}"
NACOS_CONFIG_GROUP="${NACOS_CONFIG_GROUP:-AI_GATEWAY}"
NACOS_CONFIG_DIR="${NACOS_CONFIG_DIR:-/deploy/config}"

: "${NACOS_ADMIN_PASSWORD:?NACOS_ADMIN_PASSWORD is required}"

wait_for_nacos() {
    attempt=1
    while [ "$attempt" -le 60 ]; do
        if curl -fsS \
            "${NACOS_SERVER_URL}/v3/admin/core/state/readiness" \
            >/dev/null; then
            return 0
        fi

        attempt=$((attempt + 1))
        sleep 2
    done

    printf '%s\n' "Nacos did not become ready in time." >&2
    return 1
}

login() {
    curl -fsS \
        -X POST \
        "${NACOS_SERVER_URL}/v3/auth/user/login" \
        --data-urlencode "username=${NACOS_USERNAME}" \
        --data-urlencode "password=${NACOS_ADMIN_PASSWORD}" \
        2>/dev/null || true
}

extract_access_token() {
    sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

wait_for_nacos

login_response="$(login)"
access_token="$(printf '%s' "$login_response" | extract_access_token)"

if [ -z "$access_token" ]; then
    curl -fsS \
        -X POST \
        "${NACOS_SERVER_URL}/v3/auth/user/admin" \
        --data-urlencode "password=${NACOS_ADMIN_PASSWORD}" \
        >/dev/null 2>&1 || true

    login_response="$(login)"
    access_token="$(printf '%s' "$login_response" | extract_access_token)"
fi

if [ -z "$access_token" ]; then
    printf '%s\n' \
        "Unable to authenticate to Nacos. Check NACOS_ADMIN_PASSWORD and existing admin state." \
        >&2
    exit 1
fi

found_config=false

for config_file in "${NACOS_CONFIG_DIR}"/*.yml; do
    if [ ! -f "$config_file" ]; then
        continue
    fi

    found_config=true
    data_id="$(basename "$config_file")"

    response="$(
        curl -fsS \
            -X POST \
            "${NACOS_SERVER_URL}/v3/admin/cs/config" \
            -H "accessToken:${access_token}" \
            --data-urlencode "dataId=${data_id}" \
            --data-urlencode "groupName=${NACOS_CONFIG_GROUP}" \
            --data-urlencode "type=yaml" \
            --data-urlencode "content@${config_file}"
    )"

    if ! printf '%s' "$response" \
        | grep -Eq '"code"[[:space:]]*:[[:space:]]*0'; then
        printf 'Failed to publish %s: %s\n' "$data_id" "$response" >&2
        exit 1
    fi

    printf 'Published Nacos Data ID %s in group %s.\n' \
        "$data_id" \
        "$NACOS_CONFIG_GROUP"
done

if [ "$found_config" != "true" ]; then
    printf 'No YAML configuration files found in %s.\n' \
        "$NACOS_CONFIG_DIR" \
        >&2
    exit 1
fi
