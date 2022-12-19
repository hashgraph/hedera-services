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
package com.hedera.node.app.service.consensus.impl;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;

import com.hedera.node.app.service.consensus.ConsensusPreTransactionHandler;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadata;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;

@SuppressWarnings("DanglingJavadoc")
public class ConsensusPreTransactionHandlerImpl implements ConsensusPreTransactionHandler {

    private final AccountKeyLookup keyFinder;

    public ConsensusPreTransactionHandlerImpl(@NonNull final AccountKeyLookup keyFinder) {
        this.keyFinder = keyFinder;
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleCreateTopic(TransactionBody txn, AccountID payer) {
        final var op = txn.getConsensusCreateTopic();

        final var payerKeyLookup = keyFinder.getKey(payer);
        final var payerKey = payerKeyLookup.key();
        if (payerKeyLookup.failed()) {
            return new SigTransactionMetadata(
                    txn, payer, payerKeyLookup.failureReason(), payerKey, List.of());
        }

        final var adminKey = asHederaKey(op.getAdminKey());
        final var submitKey = asHederaKey(op.getSubmitKey());
        if (adminKey.isPresent() || submitKey.isPresent()) {
            final var otherReqs = new ArrayList<HederaKey>();
            adminKey.ifPresent(otherReqs::add);
            submitKey.ifPresent(otherReqs::add);
            return new SigTransactionMetadata(txn, payer, ResponseCodeEnum.OK, payerKey, otherReqs);
        }

        return new SigTransactionMetadata(txn, payer, ResponseCodeEnum.OK, payerKey, List.of());
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleUpdateTopic(TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleDeleteTopic(TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleSubmitMessage(TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }
}
