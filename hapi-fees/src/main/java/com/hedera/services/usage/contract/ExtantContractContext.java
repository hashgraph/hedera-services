/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.usage.contract;

import static com.hedera.services.usage.contract.entities.ContractEntitySizes.CONTRACT_ENTITY_SIZES;

import com.hedera.services.usage.crypto.ExtantCryptoContext;

public class ExtantContractContext {
    private final int currentNumKvPairs;
    private final ExtantCryptoContext currentCryptoContext;

    public ExtantContractContext(
            final int currentNumKvPairs, final ExtantCryptoContext currentCryptoContext) {
        this.currentNumKvPairs = currentNumKvPairs;
        this.currentCryptoContext = currentCryptoContext;
    }

    public long currentRb() {
        return CONTRACT_ENTITY_SIZES.fixedBytesInContractRepr()
                + currentCryptoContext.currentNonBaseRb();
    }

    public long currentNumKvPairs() {
        return currentNumKvPairs;
    }
}
