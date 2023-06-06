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

package com.hedera.node.app.service.contract.impl.exec.v030;

import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * The initial implementation of {@link FeatureFlags} from v0.30; lazy creation never enabled.
 */
@Singleton
public class DisabledFeatureFlags implements FeatureFlags {
    @Inject
    public DisabledFeatureFlags() {
        // Dagger
    }

    @Override
    public boolean isImplicitCreationEnabled(@NonNull MessageFrame frame) {
        return false;
    }
}
