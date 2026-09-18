package com.lordsai.lsi.config;

import com.lordsai.lsi.entity.enums.SiteCode;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new Converter<String, SiteCode>() {
            @Override
            public SiteCode convert(String source) {
                return SiteCode.fromString(source);
            }
        });
    }
}

