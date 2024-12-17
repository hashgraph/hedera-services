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

package com.hedera.node.app.service.contract.impl.exec.v030;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.configOf;

import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * The initial implementation of {@link FeatureFlags} from v0.30; lazy creation never enabled
 * but {@code CREATE2} still with a feature flag.
 */
@Singleton
public class Version030FeatureFlags implements FeatureFlags {
    @Inject
    public Version030FeatureFlags() {
        // Dagger2
    }

    @Override
    public boolean isCreate2Enabled(@NonNull final MessageFrame frame) {
        return configOf(frame).getConfigData(ContractsConfig.class).allowCreate2();
    }

    @Override
    public boolean isImplicitCreationEnabled(@NonNull Configuration config) {
        return false;
    }
}
