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

package com.hedera.node.app.workflows.handle.stack;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;

import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.state.WrappedHederaState;
import edu.umd.cs.findbugs.annotations.NonNull;

public class BaseSavepoint extends AbstractSavepoint {
    private final HandleContext.TransactionCategory txnCategory;

    protected BaseSavepoint(
            @NonNull final WrappedHederaState state,
            @NonNull final BuilderSink parent,
            @NonNull final HandleContext.TransactionCategory txnCategory) {
        super(state, parent, txnCategory == PRECEDING ? parent.precedingCapacity() : parent.followingCapacity());
        this.txnCategory = txnCategory;
    }

    @Override
    void commitRecords() {
        if (txnCategory == PRECEDING) {
            flushPreceding(parentSink);
        } else {
            flushFollowing(parentSink);
        }
    }
}
