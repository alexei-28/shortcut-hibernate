package com.gmail.alexei28.shortcuthibernate.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/hello")
    @Operation(summary = "Simple hello endpoint")
    public Map<String, Object> hello() {
        // Map.of(...) — Spring автоматически преобразует в JSON
        return Map.of(
                "message", "Hello, Shortcut Hibernate!",
                "date", LocalDateTime.now()
        );
    }
}