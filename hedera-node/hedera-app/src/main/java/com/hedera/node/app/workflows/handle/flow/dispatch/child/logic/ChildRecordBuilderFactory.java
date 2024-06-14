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

package com.hedera.node.app.workflows.handle.flow.dispatch.child.logic;

import static com.hedera.node.app.spi.workflows.HandleContext.PrecedingTransactionCategory.LIMITED_CHILD_RECORDS;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provider of the child record builder based on the dispatched child transaction category
 */
@Singleton
public class ChildRecordBuilderFactory {
    /**
     * Constructs the {@link ChildRecordBuilderFactory} instance.
     */
    @Inject
    public ChildRecordBuilderFactory() {}

    /**
     * Provides the record builder for the child transaction category and initializes it.
     * The record builder is created based on the child category and the reversing behavior.
     * @param txnInfo the transaction info
     * @param recordListBuilder the record list builder
     * @param configuration the configuration
     * @param childCategory the child category
     * @param reversingBehavior the reversing behavior
     * @param customizer the externalized record customizer
     * @return the record builder
     */
    public SingleTransactionRecordBuilderImpl recordBuilderFor(
            TransactionInfo txnInfo,
            final RecordListBuilder recordListBuilder,
            final Configuration configuration,
            HandleContext.TransactionCategory childCategory,
            SingleTransactionRecordBuilderImpl.ReversingBehavior reversingBehavior,
            @Nullable final ExternalizedRecordCustomizer customizer) {
        final var recordBuilder =
                switch (childCategory) {
                    case PRECEDING -> switch (reversingBehavior) {
                        case REMOVABLE -> recordListBuilder.addRemovablePreceding(configuration);
                        case REVERSIBLE -> recordListBuilder.addReversiblePreceding(configuration);
                        case IRREVERSIBLE -> recordListBuilder.addPreceding(configuration, LIMITED_CHILD_RECORDS);
                    };
                    case CHILD -> switch (reversingBehavior) {
                        case REMOVABLE -> recordListBuilder.addRemovableChildWithExternalizationCustomizer(
                                configuration, requireNonNull(customizer));
                        case REVERSIBLE -> recordListBuilder.addChild(configuration, childCategory);
                        case IRREVERSIBLE -> throw new IllegalArgumentException("Unsupported reversing behavior: "
                                + reversingBehavior + " for child category: " + childCategory);
                    };
                    case SCHEDULED -> recordListBuilder.addChild(configuration, childCategory);
                    default -> throw new IllegalArgumentException("Unsupported child category: " + childCategory);
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
