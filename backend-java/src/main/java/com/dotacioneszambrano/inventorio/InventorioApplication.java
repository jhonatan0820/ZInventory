package com.dotacioneszambrano.inventorio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InventorioApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventorioApplication.class, args);
    }
}
