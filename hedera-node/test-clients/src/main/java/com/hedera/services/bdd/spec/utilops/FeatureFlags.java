/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops;

import com.hedera.services.bdd.suites.HapiSuite;
import java.util.Arrays;
import java.util.Map;

public enum FeatureFlags {
    FEATURE_FLAGS;

    public Map<String, String> allEnabled() {
        return all(HapiSuite.TRUE_VALUE);
    }

    public Map<String, String> allDisabled() {
        return all(HapiSuite.FALSE_VALUE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> all(final String choice) {
        return Map.ofEntries(
                Arrays.stream(NAMES).map(name -> Map.entry(name, choice)).toArray(Map.Entry[]::new));
    }

    private static final String[] NAMES = {
        "autoCreation.enabled",
        // Not being tested
        "contracts.itemizeStorageFees",
        // Not being tested
        "contracts.precompile.htsEnableTokenCreate",
        // Not being tested
        "contracts.redirectTokenCalls",
        "contracts.throttle.throttleByGas",
        // Not being tested
        "hedera.allowances.isEnabled",
        "hedera.recordStream.compressFilesOnCreation",
        // Behavior doesn't make sense, but is tested
        "utilPrng.isEnabled",
        "tokens.autoCreations.isEnabled",
        "lazyCreation.enabled",
        "cryptoCreateWithAliasAndEvmAddress.enabled",
        "contracts.allowAutoAssociations",
        "contracts.enforceCreationThrottle",
        "contracts.precompile.atomicCryptoTransfer.enabled",
        "scheduling.longTermEnabled",
        // Not being tested
        "contracts.evm.version.dynamic"
    };
}
