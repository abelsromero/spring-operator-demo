package org.abelsromero.springdeployment.operator;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.informer.SharedInformerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.Executors;

@Configuration
public class ReconcilersConfiguration {

    @Bean
    ApplicationRunner runner(SharedInformerFactory sharedInformerFactory, List<Controller> controllers) {
        var executorService = Executors.newCachedThreadPool();
        return args -> executorService.execute(() -> {
            sharedInformerFactory.startAllRegisteredInformers();
            controllers.forEach(Runnable::run);
        });
    }

}
