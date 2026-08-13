#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<USAGE
Usage: $0 <base-version> [--target-timestamp <YYYYMMDD.HHMMSS>] [--install] [--device <adb-serial>]

Builds the non-debuggable issue #271 diagnostic variant with the regular
Pastiera Nightly signing key.
USAGE
}

BASE_VERSION="${1:-}"
INSTALL=false
DEVICE_SERIAL="${ADB_SERIAL:-}"
TARGET_TIMESTAMP=""

if [ -z "$BASE_VERSION" ] || [[ "$BASE_VERSION" == --* ]]; then
  usage >&2
  exit 1
fi
shift

while [ $# -gt 0 ]; do
  case "$1" in
    --install)
      INSTALL=true
      shift
      ;;
    --target-timestamp)
      [ $# -ge 2 ] || { usage >&2; exit 1; }
      TARGET_TIMESTAMP="$2"
      shift 2
      ;;
    --device)
      [ $# -ge 2 ] || { usage >&2; exit 1; }
      DEVICE_SERIAL="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [ -n "$TARGET_TIMESTAMP" ] && [[ ! "$TARGET_TIMESTAMP" =~ ^[0-9]{8}\.[0-9]{6}$ ]]; then
  echo "Invalid target timestamp: $TARGET_TIMESTAMP" >&2
  usage >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NIGHTLY_SECRETS_FILE="${NIGHTLY_SECRETS_FILE:-$ROOT_DIR/release/nightly-secrets.env}"
KEYSTORE_PROPS_FILE="$ROOT_DIR/release/keystore.properties"
if [ -n "$TARGET_TIMESTAMP" ]; then
  VERSION_INFO="$(PASTIERA_NIGHTLY_TIMESTAMP="$TARGET_TIMESTAMP" "$ROOT_DIR/scripts/nightly-version.sh" "$BASE_VERSION")"
else
  VERSION_INFO="$("$ROOT_DIR/scripts/nightly-version.sh" "$BASE_VERSION")"
fi
TIMESTAMP="$(printf '%s\n' "$VERSION_INFO" | awk -F= '/^timestamp=/{print $2}')"
VERSION_CODE="$(printf '%s\n' "$VERSION_INFO" | awk -F= '/^version_code=/{print $2}')"
NIGHTLY_VERSION="$(printf '%s\n' "$VERSION_INFO" | awk -F= '/^full_version=/{print $2}')"
FULL_VERSION="${NIGHTLY_VERSION}-diagnostic.issue271"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/nightly/diagnostic/app-nightly-diagnostic.apk"
SHA_PATH="${APK_PATH}.sha256"
EXPECTED_NIGHTLY_CERT_SHA256="${EXPECTED_NIGHTLY_CERT_SHA256:-8c5dce860a65a7a3c3befcb7f7f35a1f3523c1d01462271d6ae03f4df402e685}"

read_prop() {
  local key="$1"
  local file="$2"
  awk -v target="$key" '
    $0 ~ "^[[:space:]]*"target"=" {
      line = $0
      sub(/^[[:space:]]*/, "", line)
      sub("^[^=]*=", "", line)
      print line
      exit
    }
  ' "$file"
}

first_non_empty_prop() {
  local key
  local value=""
  for key in "$@"; do
    value="$(read_prop "$key" "$KEYSTORE_PROPS_FILE")"
    if [ -n "$value" ]; then
      printf '%s\n' "$value"
      return 0
    fi
  done
  return 1
}

configure_nightly_signing_env() {
  if [ -n "${PASTIERA_NIGHTLY_KEYSTORE_PATH:-}" ] &&
    [ -n "${PASTIERA_NIGHTLY_KEYSTORE_PASSWORD:-}" ] &&
    [ -n "${PASTIERA_NIGHTLY_KEY_ALIAS:-}" ] &&
    [ -n "${PASTIERA_NIGHTLY_KEY_PASSWORD:-}" ]; then
    return
  fi

  if [ -f "$NIGHTLY_SECRETS_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source "$NIGHTLY_SECRETS_FILE"
    set +a
  fi

  bash "$ROOT_DIR/scripts/materialize-signing-keystores.sh" "$KEYSTORE_PROPS_FILE" >/dev/null || true

  if [ -f "$KEYSTORE_PROPS_FILE" ]; then
    if [ -z "${PASTIERA_NIGHTLY_KEYSTORE_PATH:-}" ]; then
      local nightly_store_file
      nightly_store_file="$(first_non_empty_prop "nightlyStoreFile" "NIGHTLY_KEYSTORE_FILE" || true)"
      if [ -n "$nightly_store_file" ]; then
        if [ "${nightly_store_file#/}" != "$nightly_store_file" ]; then
          export PASTIERA_NIGHTLY_KEYSTORE_PATH="$nightly_store_file"
        else
          export PASTIERA_NIGHTLY_KEYSTORE_PATH="$ROOT_DIR/release/$nightly_store_file"
        fi
      fi
    fi
    [ -n "${PASTIERA_NIGHTLY_KEYSTORE_PASSWORD:-}" ] || export PASTIERA_NIGHTLY_KEYSTORE_PASSWORD="$(first_non_empty_prop "nightlyStorePassword" "PASTIERA_NIGHTLY_KEYSTORE_PASSWORD" || true)"
    [ -n "${PASTIERA_NIGHTLY_KEY_ALIAS:-}" ] || export PASTIERA_NIGHTLY_KEY_ALIAS="$(first_non_empty_prop "nightlyKeyAlias" "PASTIERA_NIGHTLY_KEY_ALIAS" || true)"
    [ -n "${PASTIERA_NIGHTLY_KEY_PASSWORD:-}" ] || export PASTIERA_NIGHTLY_KEY_PASSWORD="$(first_non_empty_prop "nightlyKeyPassword" "PASTIERA_NIGHTLY_KEY_PASSWORD" || true)"
  fi
}

resolve_apksigner() {
  if [ -n "${APKSIGNER_BIN:-}" ] && [ -x "$APKSIGNER_BIN" ]; then
    printf '%s\n' "$APKSIGNER_BIN"
    return 0
  fi
  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return 0
  fi
  local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  local candidate=""
  for root in "$sdk_root" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    [ -n "$root" ] || continue
    candidate="$(ls -1d "$root"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1 || true)"
    if [ -n "$candidate" ] && [ -x "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

resolve_adb() {
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi
  local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  local candidate
  for candidate in \
    "$sdk_root/platform-tools/adb" \
    "$HOME/Library/Android/sdk/platform-tools/adb" \
    "$HOME/Android/Sdk/platform-tools/adb"; do
    [ -n "$candidate" ] && [ -x "$candidate" ] && { printf '%s\n' "$candidate"; return 0; }
  done
  return 1
}

cd "$ROOT_DIR"
configure_nightly_signing_env

./gradlew :app:testNightlyDiagnosticUnitTest :app:assembleNightlyDiagnostic \
  -PPASTIERA_VERSION_NAME="$BASE_VERSION" \
  -PPASTIERA_NIGHTLY_VERSION_CODE="$VERSION_CODE" \
  -PPASTIERA_NIGHTLY_VERSION_SUFFIX="-nightly.${TIMESTAMP}"

sha256sum "$APK_PATH" > "$SHA_PATH"

APKSIGNER="$(resolve_apksigner || true)"
[ -n "$APKSIGNER" ] || { echo "apksigner not found" >&2; exit 1; }
ACTUAL_CERT_SHA256="$($APKSIGNER verify --print-certs "$APK_PATH" | awk -F': ' '/certificate SHA-256 digest/ { print tolower($2); exit }')"
if [ "$ACTUAL_CERT_SHA256" != "$EXPECTED_NIGHTLY_CERT_SHA256" ]; then
  echo "Nightly signing certificate mismatch." >&2
  echo "Expected: $EXPECTED_NIGHTLY_CERT_SHA256" >&2
  echo "Actual:   $ACTUAL_CERT_SHA256" >&2
  exit 1
fi

if [ "$INSTALL" = true ]; then
  ADB_BIN="$(resolve_adb || true)"
  [ -n "$ADB_BIN" ] || { echo "adb not found" >&2; exit 1; }
  ADB_DEVICE_ARGS=()
  if [ -n "$DEVICE_SERIAL" ]; then
    ADB_DEVICE_ARGS=(-s "$DEVICE_SERIAL")
  fi
  PREVIOUS_DEFAULT_IME="$(
    "$ADB_BIN" "${ADB_DEVICE_ARGS[@]}" shell settings get secure default_input_method 2>/dev/null |
      tr -d '\r'
  )"
  "$ADB_BIN" "${ADB_DEVICE_ARGS[@]}" install -r "$APK_PATH"
  if [[ "$PREVIOUS_DEFAULT_IME" == it.palsoftware.pastiera.nightly/* ]]; then
    "$ADB_BIN" "${ADB_DEVICE_ARGS[@]}" shell ime enable "$PREVIOUS_DEFAULT_IME" >/dev/null
    "$ADB_BIN" "${ADB_DEVICE_ARGS[@]}" shell ime set "$PREVIOUS_DEFAULT_IME" >/dev/null
  fi
fi

printf 'full_version=%s\n' "$FULL_VERSION"
printf 'version_code=%s\n' "$VERSION_CODE"
printf 'certificate_sha256=%s\n' "$ACTUAL_CERT_SHA256"
printf 'apk=%s\n' "$APK_PATH"
printf 'sha256=%s\n' "$SHA_PATH"
