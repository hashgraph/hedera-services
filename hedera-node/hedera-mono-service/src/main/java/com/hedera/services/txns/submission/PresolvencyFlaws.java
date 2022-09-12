/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.submission;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS;

import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.EnumMap;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

/** Error response factory that caches well-known responses by status code. */
public final class PresolvencyFlaws {
    static final Map<ResponseCodeEnum, Pair<TxnValidityAndFeeReq, SignedTxnAccessor>>
            WELL_KNOWN_FLAWS = new EnumMap<>(ResponseCodeEnum.class);

    static {
        putTo(WELL_KNOWN_FLAWS, PLATFORM_NOT_ACTIVE);
        /* Structural */
        putTo(WELL_KNOWN_FLAWS, INVALID_TRANSACTION);
        putTo(WELL_KNOWN_FLAWS, TRANSACTION_OVERSIZE);
        putTo(WELL_KNOWN_FLAWS, INVALID_TRANSACTION_BODY);
        putTo(WELL_KNOWN_FLAWS, TRANSACTION_TOO_MANY_LAYERS);
        /* Syntactic */
        putTo(WELL_KNOWN_FLAWS, INVALID_TRANSACTION_ID);
        putTo(WELL_KNOWN_FLAWS, TRANSACTION_ID_FIELD_NOT_ALLOWED);
        putTo(WELL_KNOWN_FLAWS, INSUFFICIENT_TX_FEE);
        putTo(WELL_KNOWN_FLAWS, PAYER_ACCOUNT_NOT_FOUND);
        putTo(WELL_KNOWN_FLAWS, INVALID_NODE_ACCOUNT);
        putTo(WELL_KNOWN_FLAWS, MEMO_TOO_LONG);
        putTo(WELL_KNOWN_FLAWS, INVALID_ZERO_BYTE_IN_STRING);
        putTo(WELL_KNOWN_FLAWS, INVALID_TRANSACTION_DURATION);
        putTo(WELL_KNOWN_FLAWS, INVALID_TRANSACTION_START);
        putTo(WELL_KNOWN_FLAWS, TRANSACTION_EXPIRED);
        putTo(WELL_KNOWN_FLAWS, DUPLICATE_TRANSACTION);
    }

    static Pair<TxnValidityAndFeeReq, SignedTxnAccessor> responseForFlawed(
            final ResponseCodeEnum status) {
        final var response = WELL_KNOWN_FLAWS.get(status);
        return (null != response) ? response : failureWithUnknownFeeReq(status);
    }

    private static Pair<TxnValidityAndFeeReq, SignedTxnAccessor> failureWithUnknownFeeReq(
            final ResponseCodeEnum error) {
        return Pair.of(new TxnValidityAndFeeReq(error), null);
    }

    private static void putTo(
            final Map<ResponseCodeEnum, Pair<TxnValidityAndFeeReq, SignedTxnAccessor>> map,
            final ResponseCodeEnum code) {
        map.put(code, failureWithUnknownFeeReq(code));
    }

    private PresolvencyFlaws() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
