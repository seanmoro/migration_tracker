package com.spectralogic.migrationtracker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Try to find frontend dist directory
        // Check multiple possible locations
        String[] possiblePaths = {
            "/home/seans/new_tracker/frontend/dist",  // Known installation path
            "../frontend/dist",                        // Relative from JAR location
            "frontend/dist",                           // Relative from working directory
            "../dist",                                 // Alternative relative path
            "dist"                                     // Current directory
        };
        
        String frontendPath = null;
        for (String path : possiblePaths) {
            File distDir = new File(path);
            if (distDir.exists() && distDir.isDirectory()) {
                frontendPath = distDir.getAbsolutePath();
                logger.info("Found frontend dist directory at: {}", frontendPath);
                break;
            } else {
                logger.debug("Frontend dist not found at: {} (absolute: {})", path, distDir.getAbsolutePath());
            }
        }
        
        if (frontendPath != null) {
            // Serve static files from frontend/dist
            // Note: /api/** routes are handled by REST controllers with higher precedence
            // Serve all non-API routes as static files
            registry.addResourceHandler("/**")
                    .addResourceLocations("file:" + frontendPath + "/")
                    .resourceChain(false);
            logger.info("Configured static file serving from: {}", frontendPath);
        } else {
            logger.warn("Frontend dist directory not found! Static files will not be served.");
            logger.warn("Current working directory: {}", System.getProperty("user.dir"));
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Redirect root to index.html for SPA routing
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
