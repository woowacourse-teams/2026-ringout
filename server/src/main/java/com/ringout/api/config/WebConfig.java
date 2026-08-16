  package com.ringout.api.config;

  import org.springframework.context.annotation.Configuration;
  import org.springframework.web.method.HandlerTypePredicate;
  import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
  import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
  import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

  @Configuration
  public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
      configurer.addPathPrefix("/api/{version}",
          HandlerTypePredicate.forBasePackage("com.ringout.api"));
    }

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
      configurer.usePathSegment(1)
          .addSupportedVersions("v1")
          .detectSupportedVersions(false);
    }
  }
