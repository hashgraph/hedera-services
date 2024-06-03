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

package com.hedera.node.app.workflows.handle.flow.modules;

import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.handle.HandleContextImpl;
import com.hedera.node.app.workflows.handle.flow.annotations.HandleScope;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.swirlds.state.HederaState;
import dagger.BindsInstance;
import dagger.Subcomponent;

@Subcomponent(modules = {HandleContextModule.class})
@HandleScope
public interface HandleContextComponent {
    @Subcomponent.Factory
    interface Factory {
        HandleContextComponent create(
                @BindsInstance HederaState state,
                @BindsInstance HandleContext.TransactionCategory txnCategory,
                @BindsInstance KeyVerifier keyVerifier,
                @BindsInstance BlockRecordInfo blockRecordInfo,
                @BindsInstance SingleTransactionRecordBuilderImpl recordBuilder);
    }

    HandleContextImpl handleContext();
}
