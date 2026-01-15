package com.spectralogic.migrationtracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Try to find frontend dist directory
        String[] possiblePaths = {
            "../frontend/dist",
            "frontend/dist",
            "../dist",
            "dist"
        };
        
        String frontendPath = null;
        for (String path : possiblePaths) {
            File distDir = new File(path);
            if (distDir.exists() && distDir.isDirectory()) {
                frontendPath = distDir.getAbsolutePath();
                break;
            }
        }
        
        if (frontendPath != null) {
            // Serve static files from frontend/dist
            registry.addResourceHandler("/**")
                    .addResourceLocations("file:" + frontendPath + "/")
                    .resourceChain(false);
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Redirect root to index.html for SPA routing
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
