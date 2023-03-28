#!/usr/bin/env bash

set -euo pipefail

readonly app_module="udp"
readonly operator_module="udp-operator"
readonly operator_version="0.0.1-SNAPSHOT"
readonly install_namespace="spring-deploy-operator"

function log_step() {
  echo "[STEP] $1"
}

function install_metrics_server() {
  kubectl apply -f manifests/metrics-server.yaml
}

function install() {
  kubectl apply -f manifests/metrics-server.yaml
}

function clean_up() {
  log_step "${FUNCNAME[0]}"
  kubectl delete ns $install_namespace || true
}

function build_and_push_app() {
  log_step "${FUNCNAME[0]}"
  local -r app_image="$app_module:0.0.1-SNAPSHOT"
  docker rmi "$app_image" || true
  ./$app_module/gradlew -p $app_module bootBuildImage
  kind load docker-image "$app_image"
}

function build_and_push_operator() {
  log_step "${FUNCNAME[0]}"
  local -r operator_image="$operator_module:$operator_version"
  docker rmi "$operator_image" || true
  ./$operator_module/gradlew -p $operator_module bootBuildImage
  kind load docker-image "$operator_image"
}

function deploy_operator() {
  log_step "${FUNCNAME[0]}"
  kubectl create ns $install_namespace
  kubectl apply -n $install_namespace -f "manifests/crds/"
  kubectl apply -n $install_namespace -f "$operator_module/k8s/"
}

function run_configmap_test() {
  kubectl -n $install_namespace create cm "test-cm-$1" --from-literal a-key=a-value
  sleep 3
  echo "=== output"
  kubectl -n $install_namespace logs $pod_name | grep "Found"
}

function run_deployment_test() {
  kubectl apply -f manifests/examples/spring-deployment.yaml
  sleep 3
  echo "=== output"
  kubectl -n $install_namespace logs $pod_name
}

function main() {
  clean_up
  install_metrics_server

  build_and_push_app
  build_and_push_operator


  deploy_operator

  #kubectl wait pod --for=condition=ready -n $install_namespace -l "name=$pod_name"
  sleep 3
  pod_name=$(kubectl get pods -n $install_namespace --no-headers -o custom-columns=":metadata.name")

  echo "=== output"
#  kubectl -n $install_namespace logs $pod_name | grep "Found"

#  run_configmap_test "1"
#  run_configmap_test "2"
#  run_configmap_test "3"

  kubectl config set-context --current --namespace=$install_namespace

  run_deployment_test
}

main
