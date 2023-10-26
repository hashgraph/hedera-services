/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.domain;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;

/**
 * A record providing convenient access to just a {@link TransactionBody} and a {@link TransactionRecord}
 * from a record stream item for use in snapshot fuzzy-matching.
 *
 * @param itemBody the transaction body
 * @param itemRecord the transaction record
 */
public record ParsedItem(TransactionBody itemBody, TransactionRecord itemRecord) {
    public static ParsedItem parse(final RecordStreamItem item) throws InvalidProtocolBufferException {
        final var txn = item.getTransaction();
        final TransactionBody body;
        if (txn.getBodyBytes().size() > 0) {
            body = TransactionBody.parseFrom(txn.getBodyBytes());
        } else {
            final var signedTxnBytes = item.getTransaction().getSignedTransactionBytes();
            final var signedTxn = SignedTransaction.parseFrom(signedTxnBytes);
            body = TransactionBody.parseFrom(signedTxn.getBodyBytes());
        }
        return new ParsedItem(body, item.getRecord());
    }
}
