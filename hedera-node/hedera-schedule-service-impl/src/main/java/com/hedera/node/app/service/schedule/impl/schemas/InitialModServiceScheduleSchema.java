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
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.primitives.ProtoString;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * General schema for the schedule service
 * (FUTURE) When mod-service release is finalized, rename this class to e.g.
 * {@code Release47ScheduleSchema} as it will no longer be appropriate to assume
 * this schema is always correct for the current version of the software.
 */
public final class InitialModServiceScheduleSchema extends Schema {
    public InitialModServiceScheduleSchema(final SemanticVersion version) {
        super(version);
    }

    @SuppressWarnings("rawtypes")
    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(schedulesByIdDef(), schedulesByExpirySec(), schedulesByEquality());
    }

    private static StateDefinition<ScheduleID, Schedule> schedulesByIdDef() {
        return StateDefinition.inMemory(
                ScheduleServiceImpl.SCHEDULES_BY_ID_KEY, ScheduleID.PROTOBUF, Schedule.PROTOBUF);
    }

    private static StateDefinition<ProtoLong, ScheduleList> schedulesByExpirySec() {
        return StateDefinition.inMemory(
                ScheduleServiceImpl.SCHEDULES_BY_EXPIRY_SEC_KEY, ProtoLong.PROTOBUF, ScheduleList.PROTOBUF);
    }

    private static StateDefinition<ProtoString, ScheduleList> schedulesByEquality() {
        return StateDefinition.inMemory(
                ScheduleServiceImpl.SCHEDULES_BY_EQUALITY_KEY, ProtoString.PROTOBUF, ScheduleList.PROTOBUF);
    }
}
