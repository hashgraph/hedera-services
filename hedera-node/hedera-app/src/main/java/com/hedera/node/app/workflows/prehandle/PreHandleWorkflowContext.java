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
package com.hedera.node.app.workflows.prehandle;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.meta.TransactionMetadataBuilder;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.state.HederaState;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

public abstract class PreHandleWorkflowContext {

    private final TransactionBody txBody;
    private final AccountID payerID;
    private TransactionMetadataBuilder<?> metadataBuilder;

    protected PreHandleWorkflowContext(
            @NonNull final TransactionBody txBody, @NonNull final AccountID payerID) {
        this.txBody = requireNonNull(txBody);
        this.payerID = requireNonNull(payerID);
    }

    @NonNull
    public static PreHandleWorkflowContext of(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID payerID,
            @NonNull final HederaState hederaState) {
        return new TopLevelContext(txBody, payerID, hederaState);
    }

    @NonNull
    public TransactionBody getTxBody() {
        return txBody;
    }

    @NonNull
    public AccountID getPayerID() {
        return payerID;
    }

    @Nullable
    public TransactionMetadataBuilder<?> getMetadataBuilder() {
        return metadataBuilder;
    }

    public void setMetadataBuilder(@NonNull final TransactionMetadataBuilder<?> metadataBuilder) {
        this.metadataBuilder = requireNonNull(metadataBuilder);
    }

    @NonNull
    public abstract Map<String, ReadableStates> getUsedStates();

    @NonNull
    public abstract ReadableStates getReadableStates(@NonNull final String key);

    public PreHandleWorkflowContext createNestedContext(
            @NonNull final TransactionBody txBody, @NonNull final AccountID payerID) {
        return new NestedContext(txBody, payerID, this);
    }

    private static class TopLevelContext extends PreHandleWorkflowContext {
        private final HederaState hederaState;
        private final Map<String, ReadableStates> usedStates = new HashMap<>();

        public TopLevelContext(
                @NonNull final TransactionBody txBody,
                @NonNull final AccountID payerID,
                @NonNull final HederaState hederaState) {
            super(txBody, payerID);
            this.hederaState = requireNonNull(hederaState);
        }

        @NonNull
        @Override
        public Map<String, ReadableStates> getUsedStates() {
            return usedStates;
        }

        @NonNull
        @Override
        public ReadableStates getReadableStates(@NonNull final String key) {
            requireNonNull(key);
            return usedStates.computeIfAbsent(key, hederaState::createReadableStates);
        }
    }

    private static class NestedContext extends PreHandleWorkflowContext {

        private final PreHandleWorkflowContext parent;

        public NestedContext(
                final TransactionBody txBody,
                final AccountID payerID,
                final PreHandleWorkflowContext parent) {
            super(txBody, payerID);
            this.parent = requireNonNull(parent);
        }

        @NonNull
        @Override
        public Map<String, ReadableStates> getUsedStates() {
            return parent.getUsedStates();
        }

        @NonNull
        @Override
        public ReadableStates getReadableStates(@NonNull final String key) {
            return parent.getReadableStates(key);
        }
    }
}
