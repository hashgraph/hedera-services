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

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.RecordListBuilder;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import javax.inject.Inject;

public class HandleContextService {

    private final TransactionChecker checker;
    private final TransactionDispatcher dispatcher;
    private final ServiceScopeLookup serviceScopeLookup;

    @Inject
    public HandleContextService(
            @NonNull final TransactionChecker checker,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final ServiceScopeLookup serviceScopeLookup) {
        this.checker = requireNonNull(checker, "checker must not be null");
        this.dispatcher = requireNonNull(dispatcher, "dispatcher must not be null");
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup, "serviceScopeLookup must not be null");
    }

    @NonNull
    public String getServiceScope(@NonNull final TransactionBody txBody) {
        requireNonNull(txBody, "txBody must not be null");
        return serviceScopeLookup.getServiceName(txBody);
    }

    public SingleTransactionRecordBuilder dispatch(
            @NonNull final TransactionBody txBody,
            @NonNull final TransactionCategory category,
            @NonNull final HederaState root,
            @NonNull final Map<Key, SignatureVerification> keyVerifications,
            @NonNull final RecordListBuilder recordListBuilder,
            @NonNull final SingleTransactionRecordBuilder recordBuilder) {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(category, "category must not be null");
        requireNonNull(root, "root must not be null");
        requireNonNull(keyVerifications, "keyVerifications must not be null");
        requireNonNull(recordListBuilder, "recordListBuilder must not be null");
        requireNonNull(recordBuilder, "recordBuilder must not be null");

        try {
            checker.checkTransactionBody(txBody);
            dispatcher.dispatchValidate(txBody);
        } catch (PreCheckException e) {
            recordBuilder.status(e.responseCode());
            return recordBuilder;
        }

        final var context =
                new HandleContextImpl(root, txBody, category, recordBuilder, keyVerifications, recordListBuilder, this);

        try {
            dispatcher.dispatchHandle(context);
            context.commitStateChanges();
        } catch (HandleException e) {
            recordBuilder.status(e.getStatus());
            recordListBuilder.revertChildRecordBuilders(recordBuilder);
        }
        return recordBuilder;
    }
}
