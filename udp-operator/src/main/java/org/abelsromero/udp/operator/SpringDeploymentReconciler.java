package org.abelsromero.udp.operator;


import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.PatchUtils;
import org.demo.boring.models.V1SpringDeployment;
import org.demo.boring.models.V1SpringDeploymentSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.abelsromero.udp.operator.SpringDeploymentReconciler.ApiExceptionHelper.wrapApiException;
import static org.springframework.util.StringUtils.hasText;

public class SpringDeploymentReconciler implements Reconciler {

    private static final Logger logger = LoggerFactory.getLogger(SpringDeploymentReconciler.class);

    private static final String OPERATOR_NAME = "udp-operator";
    // KinD workaround
    private static final String DEFAULT_APP_IMAGE_LATEST = "udp:0.0.1-SNAPSHOT";

    private final SharedIndexInformer<V1SpringDeployment> informer;
    private final Lister<V1SpringDeployment> udpLister;
    private final CoreV1Api coreV1Api;
    private final AppsV1Api appsV1Api;

    public SpringDeploymentReconciler(SharedIndexInformer<V1SpringDeployment> informer, CoreV1Api coreV1Api, AppsV1Api appsV1Api) {
        this.informer = informer;
        this.udpLister = new Lister<>(informer.getIndexer());

        this.coreV1Api = coreV1Api;
        this.appsV1Api = appsV1Api;
    }

    @Override
    public Result reconcile(Request request) {
        final V1SpringDeployment requestUdp = udpLister.namespace(request.getNamespace()).get(request.getName());

        final String deploymentNamespace = getDeploymentNamespace(requestUdp);
        final String deploymentName = requestUdp.getSpec().getName();
        listDeployments(deploymentNamespace)
            .getItems()
            .stream()
            .filter(deployment -> deployment.getMetadata().getName().equals(deploymentName))
            .findFirst()
            .ifPresentOrElse(deployment -> {
                // Compare current with desired state to decide if we need to act
                // For the demo: we only support replica update
                final Integer currentReplicas = deployment.getSpec().getReplicas();
                final Integer replicas = requestUdp.getSpec().getReplicas();
                if (currentReplicas != replicas) {
                    V1Patch patch = new V1Patch("{\"spec\":{\"replicas\":" + replicas + "}}");
                    logger.info("Updating deployment replicas: {}/{}, {}", deploymentNamespace, deploymentName, replicas);
                    patchDeployment(deploymentNamespace, deploymentName, patch);
                } else {
                    logger.info("State did not change, no actions required.");
                }
                // Delete already handled by K8s GC via owner reference
            }, () -> {
                // create
                V1Deployment deployment = new V1DeploymentBuilder()
                    .withMetadata(new V1ObjectMeta()
                        .name(deploymentName)
                        .namespace(deploymentNamespace)
                        .ownerReferences(List.of(createOwnerReference(requestUdp))))
                    .withSpec(new V1DeploymentSpecBuilder()
                        .withReplicas(getDeploymentReplicas(requestUdp))
                        .withNewSelector()
                        .addToMatchLabels("app", deploymentName)
                        .endSelector()
                        .withTemplate(new V1PodTemplateSpecBuilder()
                            .withNewMetadata()
                            .withLabels(Map.of(
                                "app", deploymentName,
                                "managed-by", OPERATOR_NAME
                            ))
                            .and()
                            .withSpec(new V1PodSpecBuilder()
                                .withContainers(List.of(
                                    new V1ContainerBuilder()
                                        .withName(deploymentName)
                                        .withImage(getDeploymentImage(requestUdp))
                                        .withLivenessProbe(buildProbe("actuator/health/liveness"))
                                        .withReadinessProbe(buildProbe("actuator/health/readiness"))
                                        .withStartupProbe(buildProbe("actuator/health/readiness"))
                                        .withResources(new V1ResourceRequirementsBuilder()
                                            .withLimits(defaultResources())
                                            .withRequests(defaultResources())
                                            .build())
                                        .build()
                                ))
                                .build())
                            .build())
                        .build()
                    )
                    .build();

                createDeployment(deploymentNamespace, deployment);
                logger.info("New Spring Boot Deployment created: {}/{}", deploymentNamespace, deploymentName);
            });

        // Being optimistic
        return new Result(false);
    }

    private static Map<String, Quantity> defaultResources() {
        return Map.of(
//            "cpu", Quantity.fromString("1000Mi")
            "memory", Quantity.fromString("1Gi")
        );
    }

    private V1Deployment createDeployment(String deploymentNamespace, V1Deployment deployment) {
        try {
            return appsV1Api.createNamespacedDeployment(deploymentNamespace, deployment, null, null, null, null);
        } catch (ApiException e) {
            throw wrapApiException(e);
        }
    }

    private V1DeploymentList listDeployments(String namespace) {
        try {
            return appsV1Api.listNamespacedDeployment(namespace,
                null, null, null, null, null, null, null, null, null, null);
        } catch (ApiException e) {
            throw wrapApiException(e);
        }
    }

    private void patchDeployment(String deploymentNamespace, String deploymentName, V1Patch patch) {
        try {
            PatchUtils.patch(
                V1Deployment.class,
                () -> appsV1Api.patchNamespacedDeploymentCall(deploymentName, deploymentNamespace, patch,
                    null, null, null, null, null, null),
                V1Patch.PATCH_FORMAT_JSON_MERGE_PATCH,
                coreV1Api.getApiClient());
        } catch (ApiException e) {
            throw wrapApiException(e);
        }
    }

    /**
     * Return namespace for the deployment:
     * spec.namespace, or if not set, udp.metadata.namespace.
     */
    private String getDeploymentNamespace(V1SpringDeployment udp) {
        return Optional.ofNullable(udp.getSpec())
            .map(V1SpringDeploymentSpec::getNamespace)
            .orElse(udp.getMetadata().getNamespace());
    }

    private static Integer getDeploymentReplicas(V1SpringDeployment udp) {
        return Optional.ofNullable(udp.getSpec())
            .map(V1SpringDeploymentSpec::getReplicas)
            .orElse(1);
    }

    private String getDeploymentImage(V1SpringDeployment udp) {
        return Optional.ofNullable(udp.getSpec())
            .map(V1SpringDeploymentSpec::getImage)
            .orElse(DEFAULT_APP_IMAGE_LATEST);
    }

    private static V1OwnerReference createOwnerReference(V1SpringDeployment owner) {
        return new V1OwnerReference()
            .controller(true)
            .name(owner.getMetadata().getName())
            .uid(owner.getMetadata().getUid())
            .kind(owner.getKind())
            .apiVersion(owner.getApiVersion())
            .blockOwnerDeletion(true);
    }


    private V1Probe buildProbe(String path) {
        return new V1Probe()
            .initialDelaySeconds(2)
            .failureThreshold(10)
            .periodSeconds(2)
            .timeoutSeconds(2)
            .successThreshold(1)
            .httpGet(new V1HTTPGetAction()
                .path(path)
                .port(new IntOrString(8080))
                .scheme("HTTP"));
    }

    public boolean hasSynced() {
        logger.info("hasSynced");
        return informer.hasSynced();
    }

    public boolean onUpdateFilter(V1SpringDeployment v1Udp, V1SpringDeployment v1Udp1) {
        return true;
    }

    public boolean onDeleteFilter(V1SpringDeployment v1Udp, Boolean aBoolean) {
        return true;
    }

    public boolean onAddFilter(V1SpringDeployment v1Udp) {
        return true;
    }

    class ApiExceptionHelper {

        static RuntimeException wrapApiException(ApiException e) {
            return new RuntimeException(extractApiExceptionCause(e));
        }

        static String extractApiExceptionCause(ApiException e) {
            var sb = new StringBuilder();
            sb.append("Status: ").append(e.getCode());
            if (hasText(e.getResponseBody())) {
                sb.append(" ResponseBody: ").append(e.getResponseBody());
            }
            return sb.toString();
        }
    }
}
