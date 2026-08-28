package com.dotacioneszambrano.inventorio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.report")
public record ReportProperties(
        String companyName,
        String companyLogo,
        String companyAddress,
        String companyPhone
) {}
