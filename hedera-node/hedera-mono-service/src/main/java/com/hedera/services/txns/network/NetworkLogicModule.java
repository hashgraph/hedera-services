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
package com.hedera.services.txns.network;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;

import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.utils.UnzipUtility;
import com.swirlds.common.system.SwirldDualState;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Module
public final class NetworkLogicModule {
    @Provides
    @Singleton
    public static UpgradeActions.UnzipAction provideUnzipAction() {
        return UnzipUtility::unzip;
    }

    @Provides
    @Singleton
    public static Supplier<SwirldDualState> provideDualState(DualStateAccessor dualStateAccessor) {
        return dualStateAccessor::getDualState;
    }

    @Provides
    @IntoMap
    @FunctionKey(Freeze)
    public static List<TransitionLogic> provideFreezeLogic(
            final FreezeTransitionLogic freezeLogic) {
        return List.of(freezeLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(UncheckedSubmit)
    public static List<TransitionLogic> provideUncheckedSubLogic(
            final UncheckedSubmitTransitionLogic uncheckedSubLogic) {
        return List.of(uncheckedSubLogic);
    }

    private NetworkLogicModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
