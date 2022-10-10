/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.keys;

import com.hedera.services.context.TransactionContext;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
public final class KeysModule {
    @Provides
    @Singleton
    public static InHandleActivationHelper provideActivationHelper(
            final TransactionContext txnCtx, final CharacteristicsFactory characteristicsFactory) {
        return new InHandleActivationHelper(characteristicsFactory, txnCtx::swirldsTxnAccessor);
    }

    @Provides
    @Singleton
    public static ActivationTest provideActivationTest() {
        return HederaKeyActivation::isActive;
    }

    private KeysModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
