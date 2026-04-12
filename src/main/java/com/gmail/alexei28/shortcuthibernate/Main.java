package com.gmail.alexei28.shortcuthibernate;

import com.gmail.alexei28.shortcuthibernate.configuration.VersionInfoListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
   Check the health of the application: http://localhost:8082/api/v1/actuator/health
   Swagger UI: http://localhost:8082/api/v1/swagger-ui/index.html
*/
@SpringBootApplication
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Main.class);
        app.addListeners(new VersionInfoListener());
        app.run(args);

        logger.info("\n\n ===== Shortcut Hibernate has been started successfully! =====\n");
        logger.info(
                "Java version: {}, Java vendor: {}",
                System.getProperty("java.version"),
                System.getProperty("java.vendor"));
    }
}
