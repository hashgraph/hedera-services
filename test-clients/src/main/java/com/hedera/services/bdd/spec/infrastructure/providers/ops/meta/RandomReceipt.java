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
package com.hedera.services.bdd.spec.infrastructure.providers.ops.meta;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECEIPT_NOT_FOUND;
import static java.util.Collections.EMPTY_LIST;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.queries.meta.HapiGetReceipt;
import com.hedera.services.bdd.spec.transactions.TxnFactory;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import java.util.Optional;

public class RandomReceipt implements OpProvider {
    private final TxnFactory txns;

    public RandomReceipt(TxnFactory txns) {
        this.txns = txns;
    }

    @Override
    public List<HapiSpecOperation> suggestedInitializers() {
        return EMPTY_LIST;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        TransactionID txnId = txns.sampleRecentTxnId();
        if (txnId == TransactionID.getDefaultInstance()) {
            return Optional.empty();
        } else {
            HapiGetReceipt op = getReceipt(txnId).hasAnswerOnlyPrecheckFrom(OK, RECEIPT_NOT_FOUND);
            return Optional.of(op);
        }
    }
}
