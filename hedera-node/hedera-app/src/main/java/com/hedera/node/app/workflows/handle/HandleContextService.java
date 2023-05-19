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

package com.hedera.node.app.workflows.handle;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.ConsensusConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import javax.inject.Inject;

public class HandleContextService {

    private final TransactionChecker checker;
    private final TransactionDispatcher dispatcher;
    private final ServiceScopeLookup serviceScopeLookup;
    private final ConfigProvider configProvider;

    @Inject
    public HandleContextService(
            @NonNull final TransactionChecker checker,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final ConfigProvider configProvider) {
        this.checker = requireNonNull(checker, "checker must not be null");
        this.dispatcher = requireNonNull(dispatcher, "dispatcher must not be null");
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup, "serviceScopeLookup must not be null");
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
    }

    public SingleTransactionRecordBuilder dispatchPrecedingTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final HederaState root,
            @NonNull final HandleContextBase base) {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(root, "root must not be null");
        requireNonNull(base, "base must not be null");

        final var config = configProvider.getConfiguration().getConfigData(ConsensusConfig.class);
        final var recordBuilder = base.recordListBuilder().addPreceding(config);
        final var consensusNow = base.timeSlotCalculator().getNextAvailablePrecedingSlot();

        return dispatch(txBody, TransactionCategory.PRECEDING, root, base, recordBuilder, consensusNow);
    }

    public SingleTransactionRecordBuilder dispatchChildTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final HederaState root,
            @NonNull final HandleContextBase base) {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(root, "root must not be null");
        requireNonNull(base, "base must not be null");

        final var config = configProvider.getConfiguration().getConfigData(ConsensusConfig.class);
        final var recordBuilder = base.recordListBuilder().addChild(config);
        final var consensusNow = base.timeSlotCalculator().getNextAvailableChildSlot();

        return dispatch(txBody, TransactionCategory.CHILD, root, base, recordBuilder, consensusNow);
    }

    private SingleTransactionRecordBuilder dispatch(
            @NonNull final TransactionBody txBody,
            @NonNull final TransactionCategory category,
            @NonNull final HederaState root,
            @NonNull final HandleContextBase base,
            @NonNull final SingleTransactionRecordBuilder recordBuilder,
            @NonNull final Instant consensusNow) {

        try {
            checker.checkTransactionBody(txBody);
            dispatcher.dispatchValidate(txBody);
        } catch (PreCheckException e) {
            recordBuilder.status(e.responseCode());
            return recordBuilder;
        }

        final var serviceScope = serviceScopeLookup.getServiceName(txBody);
        final var stack = new SavepointStackImpl(configProvider, root);
        final var context =
                new HandleContextImpl(serviceScope, consensusNow, txBody, category, recordBuilder, stack, base, this);

        try {
            dispatcher.dispatchHandle(context);
            stack.commit();
        } catch (HandleException e) {
            recordBuilder.status(e.getStatus());
            base.recordListBuilder().revertChildRecordBuilders(recordBuilder);
        }
        return recordBuilder;
    }
}
