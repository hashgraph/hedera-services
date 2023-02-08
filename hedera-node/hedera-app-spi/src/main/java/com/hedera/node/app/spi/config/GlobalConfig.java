package com.hedera.node.app.spi.config;

import static com.hedera.node.app.spi.config.PropertyNames.WORKFLOWS_ENABLED;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData
public record GlobalConfig(@ConfigProperty(WORKFLOWS_ENABLED) boolean workflowsEnabled) {

}