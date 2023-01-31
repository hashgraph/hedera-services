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
package com.hedera.node.app.spi.meta;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.token.impl.AccountStore;
import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Metadata collected when transactions are handled as part of "pre-handle" needed for signature
 * verification. This class may have subclasses in the future.
 */
public class SigTransactionMetadata implements TransactionMetadata {
    protected List<HederaKey> requiredKeys = new ArrayList<>();
    protected TransactionBody txn;
    protected AccountStore store;

    protected ResponseCodeEnum status = OK;

    public SigTransactionMetadata(
            final AccountStore store,
            final TransactionBody txn,
            final AccountID payer,
            final List<HederaKey> otherKeys) {
        this.store = store;
        this.txn = txn;
        requiredKeys.addAll(otherKeys);
        addPayerKey(payer);
    }

    public SigTransactionMetadata(
            final AccountStore store, final TransactionBody txn, final AccountID payer) {
        this(store, txn, payer, Collections.emptyList());
    }

    private void addPayerKey(final AccountID payer) {
        final var result = store.getKey(payer);
        if (result.failed()) {
            this.status = INVALID_PAYER_ACCOUNT_ID;
        } else {
            requiredKeys.add(result.key());
        }
    }

    @Override
    public TransactionBody getTxn() {
        return txn;
    }

    @Override
    public List<HederaKey> getReqKeys() {
        return requiredKeys;
    }

    @Override
    public ResponseCodeEnum status() {
        return status;
    }
}
