#!/usr/bin/env bash
# Install Eclipse Temurin 25 (LTS) JDK on Ubuntu.
# Idempotent — re-running is safe.
#
# Usage:
#   sudo ./install-java25.sh

set -euo pipefail

INSTALL_ROOT="/opt/java"
LINK="${INSTALL_ROOT}/current"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Re-running as root..."
  exec sudo bash "$0" "$@"
fi

# Already installed and current symlink valid?
if [[ -x "${LINK}/bin/java" ]] && "${LINK}/bin/java" -version 2>&1 | grep -q '"25'; then
  echo "Temurin 25 already installed at ${LINK}"
  "${LINK}/bin/java" -version
  exit 0
fi

apt-get update -y >/dev/null
apt-get install -y curl tar ca-certificates >/dev/null

ARCH="$(dpkg --print-architecture)"
case "${ARCH}" in
  amd64) API_ARCH="x64" ;;
  arm64) API_ARCH="aarch64" ;;
  *) echo "Unsupported arch: ${ARCH}"; exit 1 ;;
esac

API_URL="https://api.adoptium.net/v3/binary/latest/25/ga/linux/${API_ARCH}/jdk/hotspot/normal/eclipse"
TMP_TGZ="$(mktemp --suffix=.tar.gz)"

echo "Downloading Temurin 25 (${API_ARCH})..."
curl -fsSL -o "${TMP_TGZ}" "${API_URL}"

mkdir -p "${INSTALL_ROOT}"
echo "Extracting to ${INSTALL_ROOT}..."
tar -xzf "${TMP_TGZ}" -C "${INSTALL_ROOT}"
rm -f "${TMP_TGZ}"

JDK_DIR="$(find "${INSTALL_ROOT}" -maxdepth 1 -type d -name "jdk-25*" | sort -r | head -n1)"
if [[ -z "${JDK_DIR}" ]]; then
  echo "ERROR: extraction did not produce a jdk-25* directory"
  exit 1
fi

ln -sfn "${JDK_DIR}" "${LINK}"

cat >/etc/profile.d/java.sh <<'EOF'
export JAVA_HOME=/opt/java/current
export PATH="${JAVA_HOME}/bin:${PATH}"
EOF
chmod +x /etc/profile.d/java.sh

# Register java/javac in update-alternatives so `java` is on PATH for all shells
update-alternatives --install /usr/bin/java  java  "${LINK}/bin/java"  1000
update-alternatives --install /usr/bin/javac javac "${LINK}/bin/javac" 1000

echo
echo "Installed: ${JDK_DIR}"
"${LINK}/bin/java" -version
echo
echo "Re-login (or run: source /etc/profile.d/java.sh) to pick up JAVA_HOME and PATH."
