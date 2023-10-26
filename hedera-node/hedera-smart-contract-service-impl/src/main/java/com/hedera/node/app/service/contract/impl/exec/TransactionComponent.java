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

package com.hedera.node.app.service.contract.impl.exec;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.hevm.HydratedEthTxData;
import com.hedera.node.app.spi.workflows.HandleContext;
import dagger.BindsInstance;
import dagger.Subcomponent;
import edu.umd.cs.findbugs.annotations.Nullable;

@Subcomponent(modules = {TransactionModule.class})
@TransactionScope
public interface TransactionComponent {
    @Subcomponent.Factory
    interface Factory {
        TransactionComponent create(
                @BindsInstance HandleContext context, @BindsInstance HederaFunctionality functionality);
    }

    ContextTransactionProcessor contextTransactionProcessor();

    @Nullable
    HydratedEthTxData hydratedEthTxData();
}
