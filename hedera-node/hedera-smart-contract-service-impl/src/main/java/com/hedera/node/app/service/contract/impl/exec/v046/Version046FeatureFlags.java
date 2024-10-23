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

package com.hedera.node.app.service.contract.impl.exec.v046;

import com.hedera.node.app.service.contract.impl.exec.v034.Version034FeatureFlags;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Version046FeatureFlags extends Version034FeatureFlags {
    @Inject
    public Version046FeatureFlags() {
        // Dagger2
    }

    @Override
    public boolean isAllowCallsToNonContractAccountsEnabled(
            @NonNull ContractsConfig config, @Nullable Long possiblyGrandFatheredEntityNum) {
        final var grandfathered = possiblyGrandFatheredEntityNum != null
                && config.evmNonExtantContractsFail().contains(possiblyGrandFatheredEntityNum);
        return config.evmAllowCallsToNonContractAccounts() && !grandfathered;
    }

    @Override
    public boolean isChargeGasOnPreEvmException(@NonNull Configuration config) {
        return config.getConfigData(ContractsConfig.class).chargeGasOnEvmHandleException();
    }
}
