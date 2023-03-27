package org.abelsromero.udp.operator.cm;


import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigMapReconciler implements Reconciler {

    private static final Logger logger = LoggerFactory.getLogger(ConfigMapReconciler.class);

    private static final String NAMESPACE = "spring-deploy-operator";

    private final SharedIndexInformer<V1ConfigMap> informer;
    private final Lister<V1ConfigMap> configMapLister;
    private final CoreV1Api coreV1Api;

    public ConfigMapReconciler(SharedIndexInformer<V1ConfigMap> informer, CoreV1Api coreV1Api) {
        this.informer = informer;
        this.configMapLister = new Lister<>(informer.getIndexer());

        this.coreV1Api = coreV1Api;
    }

    @Override
    public Result reconcile(Request request) {
        if (request.getNamespace().equals(NAMESPACE)) {
            logger.info("Received request (original): {}", request);
            configMapLister.namespace(NAMESPACE)
                .list()
                .forEach(configMap -> {
                    logger.info("Found config map (from reconciler): " + configMap.getMetadata().getName());
                    logger.info("{}", configMap);
                });
        }
        // causing a loop to see logs
        return new Result(false);
    }

    public boolean hasSynced() {
        logger.info("hasSynced");
        return informer.hasSynced();
    }

    public boolean onUpdateFilter(V1ConfigMap v1ConfigMap, V1ConfigMap v1ConfigMap1) {
        return true;
    }

    public boolean onDeleteFilter(V1ConfigMap v1ConfigMap, Boolean v1ConfigMap1) {
        return false;
    }

    public boolean onAddFilter(V1ConfigMap v1ConfigMap) {
        return true;
    }
}
