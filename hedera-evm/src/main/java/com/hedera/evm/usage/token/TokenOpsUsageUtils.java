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
package com.hedera.evm.usage.token;

import static com.hedera.evm.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

import com.hedera.evm.usage.token.meta.TokenWipeMeta;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import java.util.function.IntSupplier;

public enum TokenOpsUsageUtils {
    TOKEN_OPS_USAGE_UTILS;

    private static final int AMOUNT_REPR_BYTES = 8;

    public TokenWipeMeta tokenWipeUsageFrom(final TokenWipeAccountTransactionBody op) {
        final var subType =
                op.getSerialNumbersCount() > 0 ? TOKEN_NON_FUNGIBLE_UNIQUE : TOKEN_FUNGIBLE_COMMON;
        return tokenWipeUsageFrom(op, subType);
    }

    public TokenWipeMeta tokenWipeUsageFrom(
            final TokenWipeAccountTransactionBody op, final SubType subType) {
        return retrieveRawDataFrom(subType, op::getSerialNumbersCount, TokenWipeMeta::new);
    }

    public <R> R retrieveRawDataFrom(
            SubType subType, IntSupplier getDataForNFT, Producer<R> producer) {
        int serialNumsCount = 0;
        int bpt = 0;
        int transferRecordRb = 0;
        if (subType == TOKEN_NON_FUNGIBLE_UNIQUE) {
            serialNumsCount = getDataForNFT.getAsInt();
            transferRecordRb =
                    TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 0, serialNumsCount);
            bpt = serialNumsCount * LONG_SIZE;
        } else {
            bpt = AMOUNT_REPR_BYTES;
            transferRecordRb = TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 1, 0);
        }
        bpt += BASIC_ENTITY_ID_SIZE;

        return producer.create(bpt, subType, transferRecordRb, serialNumsCount);
    }

    @FunctionalInterface
    interface Producer<R> {
        R create(int bpt, SubType subType, long recordDb, int t);
    }
}
