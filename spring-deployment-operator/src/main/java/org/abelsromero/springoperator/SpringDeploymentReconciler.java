package org.abelsromero.springoperator;


import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.util.PatchUtils;
import org.abelsromero.springoperator.models.V1SpringDeployment;
import org.abelsromero.springoperator.models.V1SpringDeploymentSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.abelsromero.springoperator.SpringDeploymentReconciler.ApiExceptionHelper.wrapApiException;
import static org.springframework.util.StringUtils.hasText;

public class SpringDeploymentReconciler implements Reconciler {

    private static final Logger logger = LoggerFactory.getLogger(SpringDeploymentReconciler.class);

    private final SharedIndexInformer<V1SpringDeployment> informer;
    private final Lister<V1SpringDeployment> springDeploymentsLister;
    private final CoreV1Api coreV1Api;
    private final AppsV1Api appsV1Api;

    public SpringDeploymentReconciler(SharedIndexInformer<V1SpringDeployment> informer,
                                      Lister<V1SpringDeployment> springDeploymentsLister,
                                      CoreV1Api coreV1Api, AppsV1Api appsV1Api) {
        this.informer = informer;
        this.springDeploymentsLister = springDeploymentsLister;
        this.coreV1Api = coreV1Api;
        this.appsV1Api = appsV1Api;
    }

    @Override
    public Result reconcile(Request request) {
        final V1SpringDeployment deployRequest = springDeploymentsLister.namespace(request.getNamespace()).get(request.getName());

        final String deploymentNamespace = getDeploymentNamespace(deployRequest);
        final String deploymentName = deployRequest.getSpec().getName();

        listDeployments(deploymentNamespace)
            .getItems()
            .stream()
            .filter(deployment -> deployment.getMetadata().getName().equals(deploymentName))
            .findFirst()
            .ifPresentOrElse(deployment -> {
                // Compare current with desired state to decide if we need to act
                // For the demo: we only support replica update
                final Integer currentReplicas = deployment.getSpec().getReplicas();
                final Integer replicas = deployRequest.getSpec().getReplicas();
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
                V1Deployment deployment = new DeploymentBuilder()
                    .build(deploymentName, deploymentNamespace, deployRequest);
                createDeployment(deploymentNamespace, deployment);
                logger.info("New Spring Boot Deployment created: {}/{}", deploymentNamespace, deploymentName);
            });

        // Being optimistic
        return new Result(false);
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
                null, null, null, null, null, null, null, null, null, null, null);
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
     * spec.namespace, or if not set, dp.metadata.namespace.
     */
    private String getDeploymentNamespace(V1SpringDeployment springDeployment) {
        return Optional.ofNullable(springDeployment.getSpec())
            .map(V1SpringDeploymentSpec::getNamespace)
            .orElse(springDeployment.getMetadata().getNamespace());
    }

    public boolean hasSynced() {
        return informer.hasSynced();
    }

    public boolean onUpdateFilter(V1SpringDeployment sd0, V1SpringDeployment sd1) {
        return true;
    }

    public boolean onDeleteFilter(V1SpringDeployment sd, Boolean aBoolean) {
        return true;
    }

    public boolean onAddFilter(V1SpringDeployment sd) {
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
