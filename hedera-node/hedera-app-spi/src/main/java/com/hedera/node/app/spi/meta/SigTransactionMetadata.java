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

import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Metadata collected when transactions are handled as part of "pre-handle" needed for signature
 * verification. NOTE : This class may have subclasses in the future.
 */
public class SigTransactionMetadata implements TransactionMetadata {
    protected List<HederaKey> requiredKeys = new ArrayList<>();
    protected TransactionBody txn;
    protected AccountID payer;
    protected ResponseCodeEnum status;

    public SigTransactionMetadata(
            final TransactionBody txn,
            final AccountID payer,
            final ResponseCodeEnum status,
            final List<HederaKey> otherKeys) {
        this.txn = txn;
        this.payer = payer;
        this.status = status;
        requiredKeys.addAll(otherKeys);
    }

    public SigTransactionMetadata(
            final TransactionBody txn, final AccountID payer, final ResponseCodeEnum status) {
        this(txn, payer, status, Collections.emptyList());
    }

    @Override
    public TransactionBody txnBody() {
        return txn;
    }

    @Override
    public List<HederaKey> requiredKeys() {
        return requiredKeys;
    }

    @Override
    public ResponseCodeEnum status() {
        return status;
    }
    @Override
    public SigTransactionMetadataBuilder copy(final AccountKeyLookup lookup){
            return new SigTransactionMetadataBuilder(lookup)
                    .txnBody(txn)
                    .status(status)
                    .addAllReqKeys(requiredKeys);
        }
}
