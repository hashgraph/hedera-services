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

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.node.app.service.mono.state.codec.MonoMapCodecAdapter;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactionsState;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.codec.ListCodec;
import com.hedera.node.app.spi.state.codec.LongCodec;
import com.hedera.node.app.spi.state.codec.StringCodec;
import com.hedera.pbj.runtime.Codec;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;

/**
 * Standard implementation of the {@link ScheduleService} {@link com.hedera.node.app.spi.Service}.
 */
public final class ScheduleServiceImpl implements ScheduleService {
    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().minor(34).build();
    private static final Codec<List<Schedule>> LIST_OF_SCHEDULE_CODEC = ListCodec.forItems(Schedule.PROTOBUF);

    public static final String SCHEDULING_STATE_KEY = "SCHEDULING_STATE";
    public static final String SCHEDULES_BY_ID_KEY = "SCHEDULES_BY_ID";
    public static final String SCHEDULES_BY_EXPIRY_SEC_KEY = "SCHEDULES_BY_EXPIRY_SEC";
    public static final String SCHEDULES_BY_EQUALITY_KEY = "SCHEDULES_BY_EQUALITY";

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        registry.register(scheduleSchema());
    }

    private Schema scheduleSchema() {
        // Everything in memory for now
        return new Schema(CURRENT_VERSION) {
            @SuppressWarnings("rawtypes")
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                final Codec<MerkleScheduledTransactionsState> migrationCodec =
                        MonoMapCodecAdapter.codecForSelfSerializable(
                                MerkleScheduledTransactionsState.CURRENT_VERSION,
                                MerkleScheduledTransactionsState::new);
                return Set.of(
                        StateDefinition.singleton(SCHEDULING_STATE_KEY, migrationCodec),
                        schedulesByIdDef(),
                        schedulesByExpirySec(),
                        schedulesByEquality());
            }

            @Override
            public void migrate(@NonNull MigrationContext ctx) {
                // Prepopulate the ScheduleServices state with default values for genesis
                final var s = ctx.newStates().getSingleton(SCHEDULING_STATE_KEY);
                s.put(new MerkleScheduledTransactionsState()); // never scheduled
            }
        };
    }

    private StateDefinition<ScheduleID, Schedule> schedulesByIdDef() {
        return StateDefinition.inMemory(SCHEDULES_BY_ID_KEY, ScheduleID.PROTOBUF, Schedule.PROTOBUF);
    }

    private StateDefinition<Long, List<Schedule>> schedulesByExpirySec() {
        return StateDefinition.inMemory(SCHEDULES_BY_EXPIRY_SEC_KEY, LongCodec.SINGLETON, LIST_OF_SCHEDULE_CODEC);
    }

    private StateDefinition<String, List<Schedule>> schedulesByEquality() {
        return StateDefinition.inMemory(SCHEDULES_BY_EQUALITY_KEY, StringCodec.SINGLETON, LIST_OF_SCHEDULE_CODEC);
    }
}
