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

package com.hedera.node.app.workflows.handle.flow.dispatch.child;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.workflows.ComputeDispatchFeesAsTopLevel;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.flow.dispatch.Dispatch;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.modules.ChildQualifier;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import dagger.BindsInstance;
import dagger.Subcomponent;

/**
 * The Dagger subcomponent to provide the bindings for the child transaction dispatch scope.
 */
@Subcomponent
@ChildDispatchScope
public interface ChildDispatchComponent extends Dispatch {
    @Subcomponent.Factory
    interface Factory {
        ChildDispatchComponent create(
                @BindsInstance SingleTransactionRecordBuilderImpl recordBuilder,
                @BindsInstance @ChildQualifier TransactionInfo txnInfo,
                @BindsInstance ComputeDispatchFeesAsTopLevel computeDispatchFeesAsTopLevel,
                @BindsInstance AccountID syntheticPayer,
                @BindsInstance HandleContext.TransactionCategory childCategory,
                @BindsInstance @ChildQualifier SavepointStackImpl stack,
                @BindsInstance @ChildQualifier PreHandleResult preHandleResult,
                @BindsInstance KeyVerifier keyVerifier);
    }
    /**
     * The savepoint stack for the transaction scope
     * @return the savepoint stack
     */
    @ChildQualifier
    SavepointStackImpl stack();

    /**
     * The transaction info for the transaction
     * @return the transaction info
     */
    @ChildQualifier
    TransactionInfo txnInfo();

    @ChildQualifier
    PreHandleResult preHandleResult();
}
