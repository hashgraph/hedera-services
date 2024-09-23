package com.swirlds.common.test.fixtures;

import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.virtualmap.config.VirtualMapConfig;

public class ConfigurationUtils {
    public static Configuration configuration() {
        return ConfigurationBuilder.create()
                .withConfigDataType(TemporaryFileConfig.class)
                .withConfigDataType(StateCommonConfig.class)
                .withConfigDataType(VirtualMapConfig.class)
                .build();
    }
}
