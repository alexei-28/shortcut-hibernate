package com.gmail.alexei28.shortcuthibernate.configuration;


import java.io.IOException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Gradle при сборке (команда ./gradlew build или ./gradlew bootRun) создает файл по пути
 * build/resources/main/META-INF/build-info.properties. Spring Boot на этапе
 * EnvironmentPreparedEvent уже знает пути к ресурсам, но еще не начал сканирование компонентов.
 * Использование ClassPathResource позволяет нам "вручную" заглянуть в метаданные сборки до старта
 * всего фреймворка.
 */
public class VersionInfoListener
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    private static final Logger logger = LoggerFactory.getLogger(VersionInfoListener.class);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        String version = "unknown";
        String appName = event.getEnvironment().getProperty("spring.application.name", "unknown-app");
        try {
            // Загружаем сгенерированный Gradle файл build-info.properties
            ClassPathResource resource = new ClassPathResource("META-INF/build-info.properties");
            if (resource.exists()) {
                Properties props = PropertiesLoaderUtils.loadProperties(resource);
                version = props.getProperty("build.version", version);
            }
        } catch (IOException e) {
            // Файл может отсутствовать при запуске из IDE, если не был выполнен build
            logger.error("Failed to load build-info.properties, version will be set to 'unknown'", e);
        }
        logger.info("\n\nApplication name: {}, ver: {}", appName, version);
    }
}
