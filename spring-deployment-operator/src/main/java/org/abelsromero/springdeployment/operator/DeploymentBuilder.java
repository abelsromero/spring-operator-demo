package org.abelsromero.springdeployment.operator;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.*;
import org.abelsromero.springdeployment.operator.models.V1SpringDeployment;
import org.abelsromero.springdeployment.operator.models.V1SpringDeploymentSpec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DeploymentBuilder {

    private static final String OPERATOR_NAME = "spring-deployment-operator";
    private static final String DEFAULT_APP_IMAGE_LATEST = "default-spring-boot-app:0.0.1-SNAPSHOT";


    public V1Deployment build(String deploymentName,
                              String deploymentNamespace,
                              V1SpringDeployment deployRequest) {

        return new V1DeploymentBuilder()
            .withMetadata(new V1ObjectMeta()
                .name(deploymentName)
                .namespace(deploymentNamespace)
                .ownerReferences(List.of(createOwnerReference(deployRequest))))
            .withSpec(new V1DeploymentSpecBuilder()
                .withReplicas(getDeploymentReplicas(deployRequest))
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
                                .withImage(getDeploymentImage(deployRequest))
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
    }


    private static Integer getDeploymentReplicas(V1SpringDeployment springDeployment) {
        return Optional.ofNullable(springDeployment.getSpec())
            .map(V1SpringDeploymentSpec::getReplicas)
            .orElse(1);
    }

    private String getDeploymentImage(V1SpringDeployment springDeployment) {
        return Optional.ofNullable(springDeployment.getSpec())
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

    private static Map<String, Quantity> defaultResources() {
        return Map.of(
//            "cpu", Quantity.fromString("1000Mi")
            "memory", Quantity.fromString("1Gi")
        );
    }
}
