/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.crypto;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;

import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.txns.TransitionLogic;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import java.util.List;

@Module
public final class CryptoLogicModule {
    @Provides
    @IntoMap
    @FunctionKey(CryptoCreate)
    public static List<TransitionLogic> provideCryptoCreateLogic(
            final CryptoCreateTransitionLogic cryptoCreateTransitionLogic) {
        return List.of(cryptoCreateTransitionLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(CryptoUpdate)
    public static List<TransitionLogic> provideCryptoUpdateLogic(
            final CryptoUpdateTransitionLogic cryptoUpdateTransitionLogic) {
        return List.of(cryptoUpdateTransitionLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(CryptoDelete)
    public static List<TransitionLogic> provideCryptoDeleteLogic(
            final CryptoDeleteTransitionLogic cryptoDeleteTransitionLogic) {
        return List.of(cryptoDeleteTransitionLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(CryptoTransfer)
    public static List<TransitionLogic> provideCryptoTransferLogic(
            final CryptoTransferTransitionLogic cryptoTransferTransitionLogic) {
        return List.of(cryptoTransferTransitionLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(CryptoApproveAllowance)
    public static List<TransitionLogic> provideCryptoApproveAllowanceLogic(
            final CryptoApproveAllowanceTransitionLogic cryptoApproveAllowanceTransitionLogic) {
        return List.of(cryptoApproveAllowanceTransitionLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(CryptoDeleteAllowance)
    public static List<TransitionLogic> provideCryptoDeleteAllowanceLogic(
            final CryptoDeleteAllowanceTransitionLogic cryptoDeleteAllowanceTransitionLogic) {
        return List.of(cryptoDeleteAllowanceTransitionLogic);
    }

    private CryptoLogicModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
