package org.abelsromero.springoperator;

import org.abelsromero.springoperator.aot.CrdModelRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(CrdModelRuntimeHints.class)
public class OperatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OperatorApplication.class, args);
    }

}
