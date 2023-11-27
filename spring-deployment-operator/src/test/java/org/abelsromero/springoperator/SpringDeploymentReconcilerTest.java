package org.abelsromero.springoperator;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.spring.extended.controller.config.KubernetesInformerAutoConfiguration;
import io.kubernetes.client.util.PatchUtils;
import org.abelsromero.springoperator.models.V1SpringDeployment;
import org.abelsromero.springoperator.models.V1SpringDeploymentSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    SpringDeploymentReconcilerConfiguration.class,
    KubernetesInformerAutoConfiguration.class
})
public class SpringDeploymentReconcilerTest {

    @Autowired
    private SharedIndexInformer informer;

    @MockBean
    private CoreV1Api coreApi;

    @MockBean
    private AppsV1Api appsApi;

    SpringDeploymentReconciler reconciler;
    Lister<V1SpringDeployment> springDeploymentsLister;

    @BeforeEach
    void setup() {
        springDeploymentsLister = Mockito.mock(Lister.class);
        reconciler = new SpringDeploymentReconciler(informer, springDeploymentsLister, coreApi, appsApi);

        Mockito.when(springDeploymentsLister.namespace(Mockito.anyString())).thenReturn(springDeploymentsLister);
    }

    @Test
    void should_create_a_new_spring_deployment() throws ApiException {
        final String requestName = "a-springDeploymentNamespace";
        final String requestNamespace = "a-namespace";
        final String deploymentName = "my-spring-deployment";
        final String deploymentNamespace = "my-target-namespace";
        final V1SpringDeployment value = getV1SpringDeployment(requestName, requestNamespace, deploymentName, deploymentNamespace);

        Mockito.when(springDeploymentsLister.get(requestName))
            .thenReturn(value);
        // no previous Spring Deployment exists
        Mockito.when(appsApi.listNamespacedDeployment(deploymentNamespace, null, null, null, null, null, null, null, null, null, null, null))
            .thenReturn(new V1DeploymentList());

        var request = new Request(requestNamespace, requestName);
        Result result = reconciler.reconcile(request);

        assertThat(result.isRequeue()).isFalse();
        Mockito.verify(appsApi, Mockito.times(1))
            .createNamespacedDeployment(Mockito.eq(deploymentNamespace), Mockito.any(V1Deployment.class), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void should_update_a_spring_deployment() throws ApiException {
        final String requestName = "a-name";
        final String requestNamespace = "a-namespace";
        final String deploymentName = "my-spring-deployment";
        final String deploymentNamespace = "my-target-namespace";
        final V1SpringDeployment value = getV1SpringDeployment(requestName, requestNamespace, deploymentName, deploymentNamespace);

        Mockito.when(springDeploymentsLister.get(requestName))
            .thenReturn(value);

        // There's an existing k8s deployment
        final V1DeploymentList deployments = new V1DeploymentList()
            .items(List.of(new V1Deployment()
                .metadata(new V1ObjectMeta()
                    .name(deploymentName)
                    .namespace(deploymentNamespace))
                .spec(new V1DeploymentSpec()
                    .replicas(1))
            ));
        Mockito.when(appsApi.listNamespacedDeployment(deploymentNamespace, null, null, null, null, null,null, null, null, null, null, null))
            .thenReturn(deployments);

        try (MockedStatic<PatchUtils> patchUtilsMockedStatic = Mockito.mockStatic(PatchUtils.class)) {
            var request = new Request(requestNamespace, requestName);
            Result result = reconciler.reconcile(request);

            assertThat(result.isRequeue()).isFalse();
            // verify Patch
            patchUtilsMockedStatic
                .verify(() -> PatchUtils.patch(
                    Mockito.eq(V1Deployment.class),
                    Mockito.any(),
                    Mockito.eq(V1Patch.PATCH_FORMAT_JSON_MERGE_PATCH),
                    Mockito.any()));
        }
    }

    private static V1SpringDeployment getV1SpringDeployment(String requestName, String requestNamespace, String deploymentName, String deploymentNamespace) {
        return new V1SpringDeployment()
            .metadata(new V1ObjectMeta()
                .name(requestName)
                .namespace(requestNamespace))
            .spec(new V1SpringDeploymentSpec()
                .name(deploymentName)
                .namespace(deploymentNamespace)
                .replicas(2));
    }
}
