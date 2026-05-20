#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="${KIND_CLUSTER_NAME:-aupp-local}"
kind delete cluster --name "$CLUSTER_NAME"
