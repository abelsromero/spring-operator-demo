#!/usr/bin/env bash

set -euo pipefail

readonly crd_modelgen_tag="v1.0.6"
readonly crd_group="org.demo.boring"
readonly crd_destination_package="org.abelsromero.springdeployment.operator"
readonly manifests_path="manifests/crds"
readonly output_path="generated/java"

function log_step() {
  echo "[STEP] $1"
}

function clean() {
  rm -rf "$output_path" || true
}

# https://github.com/kubernetes-client/java/blob/master/docs/generate-model-from-third-party-resources.md
# models can be generated under the application package regardless of the group id using '-p' option.
function generate() {
  local -r crds_path="$(pwd)/${manifests_path}"

  docker run \
    --rm \
    -v "$crds_path":"/tmp/crds" \
    -v "$(pwd)":"$(pwd)" \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -ti \
    --network host \
    "ghcr.io/kubernetes-client/java/crd-model-gen:$crd_modelgen_tag" \
    /generate.sh \
    -u "/tmp/crds/spring-deployment-crd.yaml" \
    -n "$crd_group" \
    -p "$crd_destination_package" \
    -o "$(pwd)/$output_path"

  # TODO move classes too
  if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    sudo chown -R $(whoami):$(whoami) generated
  fi
}

function main() {
  clean
  generate
}

main
