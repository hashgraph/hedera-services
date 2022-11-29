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

import static com.hedera.node.app.Utils.asHederaKey;

import com.hedera.node.app.SigTransactionMetadata;
import com.hedera.node.app.service.token.impl.AccountStore;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import org.apache.commons.lang3.NotImplementedException;

@SuppressWarnings("DanglingJavadoc")
public class ConsensusPreTransactionHandlerImpl
        implements com.hedera.node.app.service.consensus.ConsensusPreTransactionHandler {

    private final AccountStore accountStore;

    public ConsensusPreTransactionHandlerImpl(@NonNull final AccountStore accountStore) {
        this.accountStore = accountStore;
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleCreateTopic(TransactionBody txn) {
        final var op = txn.getConsensusCreateTopic();
        final var payer = txn.getTransactionID().getAccountID();

        final var adminKey = asHederaKey(op.getAdminKey());
        final var submitKey = asHederaKey(op.getSubmitKey());
        if (adminKey.isPresent() || submitKey.isPresent()) {
            final var otherReqs = new ArrayList<HederaKey>();
            adminKey.ifPresent(otherReqs::add);
            submitKey.ifPresent(otherReqs::add);
            return new SigTransactionMetadata(accountStore, txn, payer, otherReqs);
        }

        return new SigTransactionMetadata(accountStore, txn, payer);
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleUpdateTopic(TransactionBody txn) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleDeleteTopic(TransactionBody txn) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleSubmitMessage(TransactionBody txn) {
        throw new NotImplementedException();
    }
}
