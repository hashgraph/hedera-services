package com.swirlds.baseapi.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("baseapi")
public record BaseApiConfig(@ConfigProperty(defaultValue = "8080") int port,
                            @ConfigProperty(defaultValue = "/api/") String apiBasePath) {
}
