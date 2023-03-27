package org.abelsromero.udp.operator;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.Executors;

@Configuration
public class ConfigMapReconcilerConfiguration {

    // Use shared informer instead of direct API calls
    // https://aly.arriqaaq.com/kubernetes-informers/
    //
    @Bean
    GenericKubernetesApi<V1ConfigMap, V1ConfigMapList> configmapsGenericApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(V1ConfigMap.class, V1ConfigMapList.class,
            "", "v1", "configmaps",
            apiClient);
    }

    @Bean
    public SharedIndexInformer<V1ConfigMap> configMapIndexInformer(SharedInformerFactory factory,
                                                                   GenericKubernetesApi<V1ConfigMap, V1ConfigMapList> genericApi) {
        return factory.sharedIndexInformerFor(genericApi, V1ConfigMap.class, 0);
    }


    // TODO q fa el shutdown ?
    @Bean(destroyMethod = "shutdown")
    public Controller configMapController(SharedInformerFactory sharedInformerFactory,
                                          ConfigMapReconciler reconciler) {
        return ControllerBuilder
            .defaultBuilder(sharedInformerFactory)
            .watch(workQueue -> ControllerBuilder
                .controllerWatchBuilder(V1ConfigMap.class, workQueue)
                .withResyncPeriod(Duration.ofHours(1))
                .withOnUpdateFilter(reconciler::onUpdateFilter)
                .withOnDeleteFilter(reconciler::onDeleteFilter)
                .withOnAddFilter(reconciler::onAddFilter)
                .build())
            .withWorkerCount(2)
            .withReadyFunc(reconciler::hasSynced)
            .withReconciler(reconciler)
            .withName("ConfigMapController")
            .build();
//        return ControllerBuilder
//            .defaultBuilder(sharedInformerFactory)
//            .watch(workQueue -> ControllerBuilder.controllerWatchBuilder(V1ConfigMap.class, workQueue)
//                .withResyncPeriod(Duration.ofHours(1))
//                // necessary?
//                .withOnUpdateFilter(reconciler::onUpdateFilter)
//                .withOnDeleteFilter(reconciler::onDeleteFilter)
//                .withOnAddFilter(reconciler::onAddFilter)
//                .build())
//            // default 16
//            .withWorkerCount(2)
//            // why ?
//            .withReadyFunc(reconciler::hasSynced)
//            .withReconciler(reconciler)
//            .withName("RouteConfigController")
//            .build();
    }

    @Bean
    ConfigMapReconciler configMapReconciler(SharedIndexInformer<V1ConfigMap> lister,
                                            CoreV1Api coreV1Api) {
        return new ConfigMapReconciler(lister, coreV1Api);
    }

    // necessary?
//    @Bean
//    @ConditionalOnMissingBean
//    public SharedInformerFactory sharedInformerFactory() {
//        return new SharedInformerFactory();
//    }

    @Bean
    ApplicationRunner runner(SharedInformerFactory sharedInformerFactory, Controller controller) {
        var executorService = Executors.newCachedThreadPool();
        return args -> executorService.execute(() -> {
            sharedInformerFactory.startAllRegisteredInformers();
            controller.run();
        });
    }

}
