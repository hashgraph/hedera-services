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

package com.hedera.node.app.workflows.handle.dispatch;

import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.AbstractSavePoint;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior.REVERSIBLE;

/**
 * Provider of the child record builder based on the dispatched child transaction category
 */
@Singleton
public class ChildRecordBuilderFactory {
    /**
     * Constructs the {@link ChildRecordBuilderFactory} instance.
     */
    @Inject
    public ChildRecordBuilderFactory() {
        // Dagger2
    }

    /**
     * Provides the record builder for the child transaction category and initializes it.
     * The record builder is created based on the child category and the reversing behavior.
     * @param txnInfo the transaction info
     * @param category the child category
     * @param reversingBehavior the reversing behavior
     * @param customizer the externalized record customizer
     * @return the record builder
     */
    public SingleTransactionRecordBuilderImpl recordBuilderFor(
            @NonNull final TransactionInfo txnInfo,
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final SingleTransactionRecordBuilderImpl.ReversingBehavior reversingBehavior,
            @Nullable ExternalizedRecordCustomizer customizer,
            @NonNull final AbstractSavePoint savePoint) {
        customizer = customizer == null ? NOOP_EXTERNALIZED_RECORD_CUSTOMIZER : customizer;
        final var recordBuilder =
                switch (category) {
                    case PRECEDING -> switch (reversingBehavior) {
                        case REMOVABLE, REVERSIBLE, IRREVERSIBLE -> savePoint.addRecord(reversingBehavior, category, customizer);
                    };
                    case CHILD -> switch (reversingBehavior) {
                        case REMOVABLE, REVERSIBLE -> savePoint.addRecord(reversingBehavior, category, customizer);
                        case IRREVERSIBLE -> throw new IllegalArgumentException("CHILD cannot be IRREVERSIBLE");
                    };
                    case SCHEDULED -> savePoint.addRecord(REVERSIBLE, category, customizer);
                    case USER -> throw new IllegalArgumentException("USER not a valid child category");
                };
        return initializedForChild(recordBuilder, txnInfo);
    }

    /**
     * Initializes the user record with the transaction information.
     * @param recordBuilder the record builder
     * @param txnInfo the transaction info
     */
    private SingleTransactionRecordBuilderImpl initializedForChild(
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder, @NonNull final TransactionInfo txnInfo) {
        recordBuilder
                .transaction(txnInfo.transaction())
                .transactionBytes(txnInfo.signedBytes())
                .memo(txnInfo.txBody().memo());
        final var transactionID = txnInfo.txBody().transactionID();
        if (transactionID != null) {
            recordBuilder.transactionID(transactionID);
        }
        return recordBuilder;
    }
}
