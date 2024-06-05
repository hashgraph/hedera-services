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

import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.flow.annotations.UserTxnScope;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;

@Module
public interface UserModule {
    @Provides
    @UserTxnScope
    static Fees provideFees(@NonNull FeeContext feeContext, @NonNull TransactionDispatcher dispatcher) {
        return dispatcher.dispatchComputeFees(feeContext);
    }
}
