/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.contract.impl.exec.v034;

import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.exec.v030.Version030FeatureFlags;
import com.hedera.node.config.data.AutoCreationConfig;
import com.hedera.node.config.data.LazyCreationConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The v0.34+ implementation of {@link FeatureFlags}; lazy creation enabled if config says so.
 */
@Singleton
public class Version034FeatureFlags extends Version030FeatureFlags {
    @Inject
    public Version034FeatureFlags() {
        // Dagger2
    }

    @Override
    public boolean isImplicitCreationEnabled(@NonNull final Configuration config) {
        return config.getConfigData(AutoCreationConfig.class).enabled()
                && config.getConfigData(LazyCreationConfig.class).enabled();
    }
}
