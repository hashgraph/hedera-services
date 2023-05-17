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

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.stack.Savepoint;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.ConfigProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import javax.inject.Inject;

public class TransactionRunner {

    private final TransactionChecker checker;
    private final TransactionDispatcher dispatcher;
    private final ServiceScopeLookup serviceScopeLookup;
    private final ConfigProvider configProvider;

    @Inject
    public TransactionRunner(
            @NonNull final TransactionChecker checker,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final ConfigProvider configProvider) {
        this.checker = requireNonNull(checker, "checker must not be null");
        this.dispatcher = requireNonNull(dispatcher, "dispatcher must not be null");
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup, "serviceScopeLookup must not be null");
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
    }

    public ResponseCodeEnum run(
            @NonNull final Instant consensusNow,
            @NonNull final TransactionBody txBody,
            @NonNull final Savepoint rootSavepoint,
            @NonNull final HandleContextBase base) {
        requireNonNull(consensusNow, "consensusNow must not be null");
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(rootSavepoint, "rootSavepoint must not be null");
        requireNonNull(base, "base must not be null");

        final var recordBuilderList = base.recordBuilderList();

        final var recordBuilder = new SingleTransactionRecordBuilder();
        recordBuilderList.add(recordBuilder);
        final int childRecordPos = recordBuilderList.size();

        try {
            checker.checkTransactionBody(txBody);
            dispatcher.dispatchValidate(txBody);
        } catch (PreCheckException e) {
            recordBuilder.status(e.responseCode());
            return e.responseCode();
        }

        try {
            final var serviceScope = serviceScopeLookup.getServiceName(txBody);
            final var stack = new SavepointStackImpl(configProvider, rootSavepoint);
            final var context = new HandleContextImpl(
                    serviceScope, consensusNow, txBody, recordBuilder, stack, base, this);
            dispatcher.dispatchHandle(context);

            stack.flatten();
        } catch (HandleException e) {
            recordBuilder.status(e.getStatus());
            for (int i = childRecordPos, n = recordBuilderList.size(); i < n; i++) {
                final var currentBuilder = recordBuilderList.get(i);
                if (currentBuilder.status() == ResponseCodeEnum.OK) {
                    currentBuilder.status(ResponseCodeEnum.REVERTED_SUCCESS);
                }
            }
            return e.getStatus();
        }

        return ResponseCodeEnum.OK;
    }
}
