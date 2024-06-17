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

package com.hedera.node.app.workflows.handle.flow.txn.modules;

import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.handle.flow.txn.UserTxnScope;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.swirlds.state.HederaState;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The module that provides the state dependencies in UserTxnScope.
 */
@Module
public interface StateModule {
    @Provides
    @UserTxnScope
    static HederaState provideHederaState(@NonNull final WorkingStateAccessor workingStateAccessor) {
        return workingStateAccessor.getHederaState();
    }

    @Provides
    @UserTxnScope
    static SavepointStackImpl provideSavepointStackImpl(@NonNull final HederaState state) {
        return new SavepointStackImpl(state);
    }

    @Provides
    @UserTxnScope
    static ReadableStoreFactory provideReadableStoreFactory(@NonNull final SavepointStackImpl stack) {
        return new ReadableStoreFactory(stack);
    }
}
