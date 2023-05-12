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

package com.hedera.node.app.hapi.fees.usage.crypto.entities;

import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BOOL_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.LONG_SIZE;

public enum CryptoEntitySizes {
    CRYPTO_ENTITY_SIZES;

    /* { deleted, smartContract, receiverSigRequired } */
    private static final int NUM_FLAGS_IN_BASE_ACCOUNT_REPRESENTATION = 3;
    /* { expiry, hbarBalance, autoRenewSecs } + (LEGACY) { sendThreshold, receiveThreshold } */
    private static final int NUM_LONG_FIELDS_IN_BASE_ACCOUNT_REPRESENTATION = 5;
    /* { maxAutomaticAssociations }*/
    private static final int NUM_INT_FIELDS_IN_BASE_ACCOUNT_REPRESENTATION = 1;

    public long bytesInTokenAssocRepr() {
        return LONG_SIZE + 3L * BOOL_SIZE;
    }

    public int fixedBytesInAccountRepr() {
        return NUM_FLAGS_IN_BASE_ACCOUNT_REPRESENTATION * BOOL_SIZE
                + NUM_LONG_FIELDS_IN_BASE_ACCOUNT_REPRESENTATION * LONG_SIZE;
    }
}
