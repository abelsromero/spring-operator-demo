#!/usr/bin/env bash

set -euo pipefail

readonly operator_module="udp-operator"
readonly operator_version="0.0.1-SNAPSHOT"
readonly install_namespace="udp-operator"

function log_step() {
  echo "[STEP] $1"
}

function install_metrics_server() {
  kubectl apply -f manifests/metrics-server.yaml
}

function install() {
  kubectl apply -f manifests/metrics-server.yaml
}

function clean_upd() {
  log_step "${FUNCNAME[0]}"
  kubectl delete ns $install_namespace || true
}

function build() {
  log_step "${FUNCNAME[0]}"
  docker rmi "$operator_module:$operator_version" || true
  ./$operator_module/gradlew -p $operator_module bootBuildImage
  kind load docker-image "$operator_module:$operator_version"
}

function deploy() {
  log_step "${FUNCNAME[0]}"
  kubectl create ns $install_namespace
  kubectl apply -n $install_namespace -f "$operator_module/k8s"
}

function run_test() {
  kubectl -n $install_namespace create cm "test-cm-$1" --from-literal a-key=a-value
  sleep 3
  echo "=== output"
  kubectl -n $install_namespace logs $pod_name | grep "Found"
}

function main() {
  clean_upd
  install_metrics_server
  build
  deploy

  #kubectl wait pod --for=condition=ready -n $install_namespace -l "name=$pod_name"
  sleep 3
  pod_name=$(kubectl get pods -n $install_namespace --no-headers -o custom-columns=":metadata.name")

  echo "=== output"
  kubectl -n $install_namespace logs $pod_name | grep "Found"

  run_test "1"
  run_test "2"
  run_test "3"

  kubectl config set-context --current --namespace=$install_namespace
}

main
