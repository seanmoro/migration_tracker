package com.spectralogic.migrationtracker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.File;
import java.io.IOException;

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
            // Ensure path ends with / for proper resource resolution
            String resourceLocation = frontendPath.endsWith("/") ? "file:" + frontendPath : "file:" + frontendPath + "/";
            logger.info("Using resource location: {}", resourceLocation);
            
            // First, serve static assets explicitly (assets, images, etc.)
            // This ensures static files are served correctly
            registry.addResourceHandler("/assets/**")
                    .addResourceLocations(resourceLocation + "assets/")
                    .resourceChain(false); // No chain for static assets - serve directly
            
            // Serve root-level static files (JS, CSS, etc. in root of dist)
            registry.addResourceHandler("/*.js", "/*.css", "/*.ico", "/*.svg", "/*.png", "/*.jpg", "/*.woff", "/*.woff2")
                    .addResourceLocations(resourceLocation)
                    .resourceChain(false);
            
            // Then, handle all other routes with SPA fallback
            // REST controllers have higher precedence and will handle /api/** routes first
            registry.addResourceHandler("/**")
                    .addResourceLocations(resourceLocation)
                    .resourceChain(true)
                    .addResolver(new PathResourceResolver() {
                        @Override
                        protected Resource getResource(String resourcePath, Resource location) throws IOException {
                            // Never handle API routes - REST controllers handle those with higher precedence
                            if (resourcePath.startsWith("api/") || resourcePath.startsWith("/api/")) {
                                logger.debug("Skipping API route in resource handler: " + resourcePath);
                                return null; // Return null to let Spring try other handlers/controllers
                            }
                            
                            Resource requestedResource = location.createRelative(resourcePath);
                            // If the requested resource exists, return it
                            if (requestedResource.exists() && requestedResource.isReadable()) {
                                logger.debug("Serving static resource: " + resourcePath);
                                return requestedResource;
                            }
                            // Fallback to index.html for React Router (only for non-API routes)
                            logger.debug("Resource not found, falling back to index.html for: " + resourcePath);
                            return location.createRelative("index.html");
                        }
                    });
            logger.info("Configured static file serving from: {} with SPA fallback (excluding /api/**)", frontendPath);
        } else {
            logger.warn("Frontend dist directory not found! Static files will not be served.");
            logger.warn("Current working directory: {}", System.getProperty("user.dir"));
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Add explicit view controller for root to ensure it's handled
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
