/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

import com.swirlds.demo.platform.fs.stresstest.proto.AppTransactionSignatureType;

/**
 * Configuration used for setting up transaction pool
 */
public class TransactionPoolConfig {
    /** An array to define the percentage of each specific signing algorithm should be generated */
    private SigTypeConfig[] sigTypeConfigs;

    /** generate random signature type based on configuration */
    WeightedRandomBag<AppTransactionSignatureType> signatureTypeGenerator = new WeightedRandomBag<>();

    public SigTypeConfig[] getSigTypeConfigs() {
        return sigTypeConfigs;
    }

    public void setSigTypeConfigs(SigTypeConfig[] sigTypeConfigs) {
        this.sigTypeConfigs = sigTypeConfigs;
        for (SigTypeConfig item : sigTypeConfigs) {
            signatureTypeGenerator.addEntry(item.signatureType, item.percentage);
        }
    }

    /**
     * Return a random signing algorithm type
     */
    public AppTransactionSignatureType getRandomSigType() {
        if (sigTypeConfigs == null || sigTypeConfigs.length == 0) {
            // return default type if distribution not defined
            return AppTransactionSignatureType.ED25519;
        } else {
            return signatureTypeGenerator.getRandom();
        }
    }
}
