package com.billboard.social.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()

                .withCoercionConfig(LogicalType.Textual, cfg -> {
                    cfg.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                    cfg.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                    cfg.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                })
                .addModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
