/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.txns.ethereum;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;

import com.hedera.node.app.service.mono.fees.annotations.FunctionKey;
import com.hedera.node.app.service.mono.txns.TransitionLogic;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import java.util.List;

@Module
public class EthereumLogicModule {
    @Provides
    @IntoMap
    @FunctionKey(EthereumTransaction)
    public static List<TransitionLogic> provideEthereumTransactionLogic(
            final EthereumTransitionLogic ethereumTransitionLogic) {
        return List.of(ethereumTransitionLogic);
    }

    private EthereumLogicModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
