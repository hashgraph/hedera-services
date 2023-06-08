/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.testConfigOf;

import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.exec.v030.Version030FeatureFlags;
import com.hedera.node.config.data.AutoCreationConfig;
import com.hedera.node.config.data.LazyCreationConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * The v0.34+ implementation of {@link FeatureFlags}; lazy creation enabled if config says so.
 */
public class Version034FeatureFlags extends Version030FeatureFlags {
    @Override
    public boolean isImplicitCreationEnabled(@NonNull final MessageFrame frame) {
        return testConfigOf(
                frame,
                config -> config.getConfigData(AutoCreationConfig.class).enabled()
                        && config.getConfigData(LazyCreationConfig.class).enabled());
    }
}
