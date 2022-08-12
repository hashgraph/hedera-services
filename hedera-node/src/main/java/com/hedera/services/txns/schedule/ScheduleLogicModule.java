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
package com.hedera.services.txns.schedule;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;

import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.txns.TransitionLogic;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import java.util.List;

@Module
public final class ScheduleLogicModule {
    @Provides
    @IntoMap
    @FunctionKey(ScheduleCreate)
    public static List<TransitionLogic> provideScheduleCreateLogic(
            final ScheduleCreateTransitionLogic scheduleCreateLogic) {
        return List.of(scheduleCreateLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(ScheduleSign)
    public static List<TransitionLogic> provideScheduleSignLogic(
            final ScheduleSignTransitionLogic scheduleSignLogic) {
        return List.of(scheduleSignLogic);
    }

    @Provides
    @IntoMap
    @FunctionKey(ScheduleDelete)
    public static List<TransitionLogic> provideScheduleDeleteLogic(
            final ScheduleDeleteTransitionLogic scheduleDeleteLogic) {
        return List.of(scheduleDeleteLogic);
    }

    private ScheduleLogicModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
