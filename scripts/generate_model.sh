#!/usr/bin/env bash

set -euo pipefail

readonly output_path="generated/java"
readonly crdmodelgen_tag="v1.0.6"
readonly manifests_path="manifests/crds"

function log_step() {
  echo "[STEP] $1"
}

function clean() {
  # FIXME fails in Linux since container runs as linux and files need sudo
  rm -rf "$output_path" || true
}

function generate() {
  local -r crds_path="$(pwd)/${manifests_path}"

  docker run \
    --rm \
    -v "$crds_path":"/tmp/crds" \
    -v "$(pwd)":"$(pwd)" \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -ti \
    --network host \
    "ghcr.io/kubernetes-client/java/crd-model-gen:$crdmodelgen_tag" \
    /generate.sh \
    -u "/tmp/crds/udp-crd.yaml" \
    -o "$(pwd)/$output_path"

  chown
}

function main() {
  clean
  generate
}

main
