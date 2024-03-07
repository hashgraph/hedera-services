package com.hedera.node.blocknode.core;

import com.hedera.node.config.NodeProperty;
import com.hedera.node.config.types.Profile;
import com.swirlds.config.api.ConfigProperty;

public record BlockNodeConfig (
        @ConfigProperty(value = "profiles.active", defaultValue = "PROD") @NodeProperty Profile activeProfile
) {}
