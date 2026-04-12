package com.myorg.lsf.template;

import com.myorg.lsf.http.client.EnableLsfHttpClients;
import com.myorg.lsf.template.integration.http.TemplateDependencyClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = TemplateServiceApplication.class)
@EnableLsfHttpClients(basePackageClasses = TemplateDependencyClient.class)
public class TemplateServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TemplateServiceApplication.class, args);
    }
}
