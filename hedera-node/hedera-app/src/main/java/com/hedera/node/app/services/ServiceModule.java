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
package com.hedera.node.app.services;

import com.hedera.node.app.service.consensus.impl.components.ConsensusComponent;
import com.hedera.node.app.service.consensus.impl.components.DaggerConsensusComponent;
import com.hedera.node.app.service.contract.impl.components.ContractComponent;
import com.hedera.node.app.service.contract.impl.components.DaggerContractComponent;
import com.hedera.node.app.service.file.impl.components.DaggerFileComponent;
import com.hedera.node.app.service.file.impl.components.FileComponent;
import com.hedera.node.app.service.network.impl.components.DaggerNetworkComponent;
import com.hedera.node.app.service.network.impl.components.NetworkComponent;
import com.hedera.node.app.service.schedule.impl.components.DaggerScheduleComponent;
import com.hedera.node.app.service.schedule.impl.components.ScheduleComponent;
import com.hedera.node.app.service.token.impl.components.DaggerTokenComponent;
import com.hedera.node.app.service.token.impl.components.TokenComponent;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
public interface ServiceModule {
    @Provides
    @Singleton
    static ConsensusComponent provideConsensusComponent() {
        return DaggerConsensusComponent.create();
    }

    @Provides
    @Singleton
    static FileComponent provideFileComponent() {
        return DaggerFileComponent.create();
    }

    @Provides
    @Singleton
    static NetworkComponent provideNetworkComponent() {
        return DaggerNetworkComponent.create();
    }

    @Provides
    @Singleton
    static ContractComponent provideContractComponent() {
        return DaggerContractComponent.create();
    }

    @Provides
    @Singleton
    static ScheduleComponent provideScheduleComponent() {
        return DaggerScheduleComponent.create();
    }

    @Provides
    @Singleton
    static TokenComponent provideTokenComponent() {
        return DaggerTokenComponent.create();
    }
}
