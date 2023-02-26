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

package com.hedera.node.app.workflows.handle.records;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.spi.records.RecordBuilder;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Base implementation of a {@code RecordBuilder} that will eventually track all the
 * "universal" transaction metadata and side-effects.
 */
public abstract class UniversalRecordBuilder<T extends RecordBuilder<T>> implements RecordBuilder<T> {
    private ResponseCodeEnum status = null;

    protected abstract T self();

    /**
     * {@inheritDoc}
     */
    @Override
    public T setFinalStatus(@NonNull final ResponseCodeEnum status) {
        this.status = status;
        return self();
    }

    /**
     * A temporary method to expose the side-effects tracked in this builder to
     * the mono context.
     *
     * @param txnCtx the mono context
     */
    @Deprecated
    protected void exposeSideEffectsToMono(@NonNull final TransactionContext txnCtx) {
        if (status != null) {
            Objects.requireNonNull(txnCtx).setStatus(status);
        }
    }
}
