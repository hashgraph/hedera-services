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

package com.hedera.node.app.integration.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageRecordBuilder;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.TransactionHandlers;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Singleton
public class ReplayFacilityTransactionDispatcher extends TransactionDispatcher {
    private TransactionBody dispatchedTxn;
    private Consumer<TransactionRecord> assertionForDispatched;

    @Inject
    public ReplayFacilityTransactionDispatcher(
            @NonNull HandleContext handleContext,
            @NonNull TransactionHandlers handlers,
            @NonNull HederaAccountNumbers accountNumbers,
            @NonNull GlobalDynamicProperties dynamicProperties) {
        super(handleContext, handlers, accountNumbers, dynamicProperties);
    }

    @Override
    public void dispatchHandle(
            final @NonNull HederaFunctionality function,
            final @NonNull TransactionBody txn,
            final @NonNull WritableStoreFactory writableStoreFactory) {
        try {
            dispatchedTxn = txn;
            assertionForDispatched = null;
            super.dispatchHandle(function, txn, writableStoreFactory);
        } catch (IllegalArgumentException e) {
            if (TYPE_NOT_SUPPORTED.equals(e.getMessage())) {
                System.out.println("Skipping unsupported transaction type " + function);
            } else {
                throw e;
            }
        }
    }

    public void assertCustomizationsMatch(final TransactionRecord expectedRecord) {
        if (assertionForDispatched != null) {
            assertionForDispatched.accept(expectedRecord);
        }
    }

    @Override
    protected void finishConsensusCreateTopic(
            @NonNull final ConsensusCreateTopicRecordBuilder recordBuilder,
            @NonNull final WritableTopicStore topicStore) {
        assertionForDispatched = expectedRecord -> assertEquals(
                expectedRecord.receipt().topicID().topicNum(),
                recordBuilder.getCreatedTopic(),
                "Topic number mismatch");
    }

    @Override
    protected void finishConsensusSubmitMessage(
            @NonNull final ConsensusSubmitMessageRecordBuilder recordBuilder,
            @NonNull final WritableTopicStore topicStore) {
        assertionForDispatched = expectedRecord -> {
            assertEquals(
                    expectedRecord.receipt().topicRunningHashVersion(),
                    recordBuilder.getUsedRunningHashVersion(),
                    "Topic running hash version mismatch");
            assertEquals(
                    expectedRecord.receipt().topicSequenceNumber(),
                    recordBuilder.getNewTopicSequenceNumber(),
                    "Topic sequence number mismatch");
            assertEquals(
                    expectedRecord.receipt().topicRunningHash(),
                    Bytes.wrap(recordBuilder.getNewTopicRunningHash()),
                    "Topic running hash mismatch");
        };
    }
}
