/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl;

import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKeySerializer;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.serdes.MonoSchedulingStateAdapterSerdes;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.serdes.MonoMapSerdesAdapter;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Standard implementation of the {@link ScheduleService} {@link com.hedera.node.app.spi.Service}.
 */
public final class ScheduleServiceImpl implements ScheduleService {
    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().setMinor(34).build();

    private static final String SCHEDULING_STATE_KEY = "SCHEDULING_STATE";
    private static final String SCHEDULES_BY_ID_KEY = "SCHEDULES_BY_ID";
    private static final String SCHEDULES_BY_EXPIRY_SEC_KEY = "SCHEDULES_BY_EXPIRY_SEC";
    private static final String SCHEDULES_BY_EQUALITY_KEY = "SCHEDULES_BY_EQUALITY";

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry) {
        registry.register(scheduleSchema());
    }

    private Schema scheduleSchema() {
        // Everything in memory for now
        return new Schema(CURRENT_VERSION) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(
                        StateDefinition.singleton(SCHEDULING_STATE_KEY, new MonoSchedulingStateAdapterSerdes()),
                        schedulesByIdDef(),
                        schedulesByExpirySec(),
                        schedulesByEquality());
            }
        };
    }

    private StateDefinition<EntityNumVirtualKey, ScheduleVirtualValue> schedulesByIdDef() {
        final var keySerdes = MonoMapSerdesAdapter.serdesForVirtualKey(
                EntityNumVirtualKey.CURRENT_VERSION, EntityNumVirtualKey::new, new EntityNumVirtualKeySerializer());
        final var valueSerdes = MonoMapSerdesAdapter.serdesForSelfSerializable(
                ScheduleVirtualValue.CURRENT_VERSION, ScheduleVirtualValue::new);
        return StateDefinition.inMemory(SCHEDULES_BY_ID_KEY, keySerdes, valueSerdes);
    }

    private StateDefinition<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> schedulesByExpirySec() {
        final var keySerdes = MonoMapSerdesAdapter.serdesForVirtualKey(
                SecondSinceEpocVirtualKey.CURRENT_VERSION,
                SecondSinceEpocVirtualKey::new,
                new SecondSinceEpocVirtualKeySerializer());
        final var valueSerdes = MonoMapSerdesAdapter.serdesForSelfSerializable(
                ScheduleVirtualValue.CURRENT_VERSION, ScheduleSecondVirtualValue::new);
        return StateDefinition.inMemory(SCHEDULES_BY_EXPIRY_SEC_KEY, keySerdes, valueSerdes);
    }

    private StateDefinition<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> schedulesByEquality() {
        final var keySerdes = MonoMapSerdesAdapter.serdesForVirtualKey(
                ScheduleEqualityVirtualKey.CURRENT_VERSION,
                ScheduleEqualityVirtualKey::new,
                new ScheduleEqualityVirtualKeySerializer());
        final var valueSerdes = MonoMapSerdesAdapter.serdesForSelfSerializable(
                ScheduleEqualityVirtualValue.CURRENT_VERSION, ScheduleEqualityVirtualValue::new);
        return StateDefinition.inMemory(SCHEDULES_BY_EQUALITY_KEY, keySerdes, valueSerdes);
    }
}
