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
package com.hedera.services.state.logic;

import com.hedera.services.state.annotations.RunTopLevelTransition;
import com.hedera.services.state.annotations.RunTriggeredTransition;
import com.hedera.services.txns.ProcessLogic;
import dagger.Binds;
import dagger.Module;
import javax.inject.Singleton;

@Module
public interface HandleLogicModule {
    @Binds
    @Singleton
    @RunTopLevelTransition
    Runnable provideTopLevelTransition(TopLevelTransition topLevelTransition);

    @Binds
    @Singleton
    @RunTriggeredTransition
    Runnable provideTriggeredTransition(TriggeredTransition triggeredTransition);

    @Binds
    @Singleton
    ProcessLogic provideProcessLogic(StandardProcessLogic standardProcessLogic);
}
