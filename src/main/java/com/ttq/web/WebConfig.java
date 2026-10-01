package com.ttq.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The customer landing page is the site root ({@code static/index.html}), the Merinco medical supply
 * chat; the earlier loan landing page is {@code /loan.html}. The developer console lives
 * under {@code /console/}. Spring Boot only serves a directory's index page at the root, so the
 * console's folder paths are mapped to its page here.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/console", "/console/");
        registry.addViewController("/console/").setViewName("forward:/console/index.html");
    }
}
