/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.node.app.service.contract.impl.annotations.InitialState;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.workflows.HandleContext;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;

@Module
public interface TransactionInitialStateModule {
    @Provides
    @InitialState
    @TransactionScope
    static ReadableFileStore provideInitialFileStore(@NonNull final HandleContext context) {
        return context.storeFactory().readableStore(ReadableFileStore.class);
    }

    @Provides
    @InitialState
    @TransactionScope
    static ReadableAccountStore provideInitialAccountStore(@NonNull final HandleContext context) {
        return context.storeFactory().readableStore(ReadableAccountStore.class);
    }

    @Provides
    @InitialState
    @TransactionScope
    static TokenServiceApi provideInitialTokenServiceApi(@NonNull final HandleContext context) {
        return context.storeFactory().serviceApi(TokenServiceApi.class);
    }
}
