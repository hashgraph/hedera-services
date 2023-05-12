/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.fees.usage.contract.entities;

import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BOOL_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.LONG_SIZE;

public enum ContractEntitySizes {
    CONTRACT_ENTITY_SIZES;

    /* { accountId, contractId } */
    static final int NUM_ENTITY_IDS_IN_BASE_CONTRACT_REPRESENTATION = 2;
    /* { deleted } */
    static final int NUM_FLAGS_IN_BASE_CONTRACT_REPRESENTATION = 1;
    /* { expiry, hbarBalance, autoRenewSecs, storageBytes } */
    static final int NUM_LONG_FIELDS_IN_BASE_CONTRACT_REPRESENTATION = 4;

    static final int NUM_BYTES_IN_SOLIDITY_ADDRESS_REPR = 40;

    public static final int NUM_BYTES_PER_KV_PAIR = 64;

    public int fixedBytesInContractRepr() {
        return NUM_FLAGS_IN_BASE_CONTRACT_REPRESENTATION * BOOL_SIZE
                + NUM_LONG_FIELDS_IN_BASE_CONTRACT_REPRESENTATION * LONG_SIZE
                + NUM_ENTITY_IDS_IN_BASE_CONTRACT_REPRESENTATION * BASIC_ENTITY_ID_SIZE
                + NUM_BYTES_IN_SOLIDITY_ADDRESS_REPR;
    }
}
