#!/usr/bin/env bash

set -euo pipefail

readonly app_module="default-spring-boot-app"
readonly operator_module="spring-deployment-operator"
readonly operator_version="0.0.1-SNAPSHOT"
readonly install_namespace="spring-deploy-operator"

function log_step() {
  echo "[STEP] $1"
}

# Customized installation for KinD
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

function build_and_push_default_app() {
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

function run_deployment_test() {
  kubectl apply -f manifests/examples/spring-deployment.yaml
  sleep 3
#  echo "=== logs"
#  kubectl -n $install_namespace logs $pod_name
}

function main() {
  clean_up

  install_metrics_server

  build_and_push_default_app
  build_and_push_operator

  deploy_operator

  kubectl config set-context --current --namespace=$install_namespace

#  run_deployment_test
}

main
