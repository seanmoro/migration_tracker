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
            // Serve static files from frontend/dist
            // Exclude /api/** from static resource handling - REST controllers handle those
            // Use a more specific pattern that excludes API routes
            registry.addResourceHandler(
                    "/assets/**",
                    "/*.js",
                    "/*.css",
                    "/*.html",
                    "/*.ico",
                    "/*.svg",
                    "/*.png",
                    "/*.jpg",
                    "/*.jpeg",
                    "/*.gif",
                    "/*.woff",
                    "/*.woff2",
                    "/*.ttf",
                    "/*.eot"
            )
                    .addResourceLocations("file:" + frontendPath + "/")
                    .resourceChain(true);
            
            // Add SPA fallback for all other routes (except /api/**)
            registry.addResourceHandler("/**")
                    .addResourceLocations("file:" + frontendPath + "/")
                    .resourceChain(true)
                    .addResolver(new PathResourceResolver() {
                        @Override
                        protected Resource getResource(String resourcePath, Resource location) throws IOException {
                            // Never handle API routes - let REST controllers handle them
                            if (resourcePath.startsWith("api/") || resourcePath.startsWith("/api/")) {
                                return null; // Return null to indicate this resolver can't handle it
                            }
                            
                            Resource requestedResource = location.createRelative(resourcePath);
                            // If the requested resource exists, return it
                            if (requestedResource.exists() && requestedResource.isReadable()) {
                                return requestedResource;
                            }
                            // Fallback to index.html for React Router (only for non-API routes)
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
        // Redirect root to index.html for SPA routing
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
