/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.checks;

import static com.hedera.services.bdd.junit.hedera.SystemFunctionalityTarget.NA;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.getUniqueTimestampPlusSecs;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;

public class VerifyUserFreezeNotAuthorized extends UtilOp {
    private static final long USER_PAYER_NUM = 1001L;

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        final var validStart = getUniqueTimestampPlusSecs(spec.setup().txnStartOffsetSecs());
        final var txnId = TransactionID.newBuilder()
                .setTransactionValidStart(validStart)
                .setAccountID(
                        AccountID.newBuilder().setAccountNum(USER_PAYER_NUM).build())
                .build();
        final var body = TransactionBody.newBuilder()
                .setTransactionID(txnId)
                .setNodeAccountID(AccountID.newBuilder().setAccountNum(3).build())
                .setTransactionValidDuration(
                        Duration.newBuilder().setSeconds(120).build())
                .setFreeze(FreezeTransactionBody.getDefaultInstance())
                .build();
        final var txn = Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(body.toByteString())
                        .setSigMap(SignatureMap.getDefaultInstance())
                        .build()
                        .toByteString())
                .build();
        final var response = spec.targetNetworkOrThrow().submit(txn, Freeze, NA, targetNodeFor(spec));
        assertEquals(NOT_SUPPORTED, response.getNodeTransactionPrecheckCode());
        return false;
    }
}
