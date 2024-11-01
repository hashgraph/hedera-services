/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.schemas;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.swirlds.state.merkle.MigrationContext;
import com.swirlds.state.merkle.Schema;
import com.swirlds.state.merkle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * General schema for the schedule service.
 */
public final class V0490ScheduleSchema extends Schema {
    private static final long MAX_SCHEDULES_BY_ID_KEY = 50_000_000L;
    private static final long MAX_SCHEDULES_BY_EXPIRY_SEC_KEY = 50_000_000L;
    private static final long MAX_SCHEDULES_BY_EQUALITY = 50_000_000L;
    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(49).patch(0).build();

    public static final String SCHEDULES_BY_ID_KEY = "SCHEDULES_BY_ID";
    public static final String SCHEDULES_BY_EXPIRY_SEC_KEY = "SCHEDULES_BY_EXPIRY_SEC";
    public static final String SCHEDULES_BY_EQUALITY_KEY = "SCHEDULES_BY_EQUALITY";

    /**
     * Instantiates a new V0490 (version 0.49.0) schedule schema.
     */
    public V0490ScheduleSchema() {
        super(VERSION);
    }

    @SuppressWarnings("rawtypes")
    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(schedulesByIdDef(), schedulesByExpirySec(), schedulesByEquality());
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        // There are no scheduled transactions at genesis
    }

    private static StateDefinition<ScheduleID, Schedule> schedulesByIdDef() {
        return StateDefinition.onDisk(
                SCHEDULES_BY_ID_KEY, ScheduleID.PROTOBUF, Schedule.PROTOBUF, MAX_SCHEDULES_BY_ID_KEY);
    }

    private static StateDefinition<ProtoLong, ScheduleList> schedulesByExpirySec() {
        return StateDefinition.onDisk(
                SCHEDULES_BY_EXPIRY_SEC_KEY,
                ProtoLong.PROTOBUF,
                ScheduleList.PROTOBUF,
                MAX_SCHEDULES_BY_EXPIRY_SEC_KEY);
    }

    private static StateDefinition<ProtoBytes, ScheduleList> schedulesByEquality() {
        return StateDefinition.onDisk(
                SCHEDULES_BY_EQUALITY_KEY, ProtoBytes.PROTOBUF, ScheduleList.PROTOBUF, MAX_SCHEDULES_BY_EQUALITY);
    }
}
