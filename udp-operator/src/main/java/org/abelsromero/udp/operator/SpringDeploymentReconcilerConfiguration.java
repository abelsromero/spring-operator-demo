package org.abelsromero.udp.operator;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import org.demo.boring.models.V1SpringDeployment;
import org.demo.boring.models.V1SpringDeploymentList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
class SpringDeploymentReconcilerConfiguration {

    @Bean(destroyMethod = "shutdown")
    Controller controller(SharedInformerFactory sharedInformerFactory,
                          SpringDeploymentReconciler reconciler) {
        return ControllerBuilder
            .defaultBuilder(sharedInformerFactory)
            .watch(workQueue -> ControllerBuilder
                .controllerWatchBuilder(V1SpringDeployment.class, workQueue)
                .withResyncPeriod(Duration.ofHours(1))
                .withOnUpdateFilter(reconciler::onUpdateFilter)
                .withOnDeleteFilter(reconciler::onDeleteFilter)
                .withOnAddFilter(reconciler::onAddFilter)
                .build())
            .withWorkerCount(2)
            .withReadyFunc(reconciler::hasSynced)
            .withReconciler(reconciler)
            .withName("UdpController")
            .build();
    }

    @Bean
    SpringDeploymentReconciler reconciler(SharedIndexInformer<V1SpringDeployment> lister,
                                          CoreV1Api coreV1Api,
                                          AppsV1Api appsV1Api) {
        return new SpringDeploymentReconciler(lister, coreV1Api, appsV1Api);
    }

    @Bean
    GenericKubernetesApi<V1SpringDeployment, V1SpringDeploymentList> genericApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(V1SpringDeployment.class, V1SpringDeploymentList.class,
            "boring.demo.org", "v1", "springdeployments",
            apiClient);
    }

    @Bean
    SharedIndexInformer<V1SpringDeployment> indexInformer(SharedInformerFactory factory,
                                                          GenericKubernetesApi<V1SpringDeployment, V1SpringDeploymentList> genericApi) {
        return factory.sharedIndexInformerFor(genericApi, V1SpringDeployment.class, 0);
    }

}
