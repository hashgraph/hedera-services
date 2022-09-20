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
package com.hedera.evm.usage.token.entities;

import static com.hedera.evm.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

public enum TokenEntitySizes {
    TOKEN_ENTITY_SIZES;

    public int bytesUsedToRecordTokenTransfers(
            int numTokens, int fungibleNumTransfers, int uniqueNumTransfers) {
        return numTokens * BASIC_ENTITY_ID_SIZE
                + fungibleNumTransfers * USAGE_PROPERTIES.accountAmountBytes()
                + uniqueNumTransfers * USAGE_PROPERTIES.nftTransferBytes();
    }
}
