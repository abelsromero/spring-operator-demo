package org.abelsromero.udp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EchoController {

    @GetMapping("/echo/{message}")
    public MessageResponse echo(@PathVariable String message) {
        return new MessageResponse(message);
    }

    record MessageResponse(String message) {
    }
}
