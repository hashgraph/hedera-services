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

import static com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema.SCHEDULES_BY_EQUALITY_KEY;
import static com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema.SCHEDULES_BY_EXPIRY_SEC_KEY;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.DISTINCT;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleIdList;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterators;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * General schema for the schedule service.
 */
public final class V0570ScheduleSchema extends Schema {
    private static final Logger log = LogManager.getLogger(V0570ScheduleSchema.class);
    private static final long MAX_SCHEDULE_IDS_BY_EXPIRY_SEC_KEY = 50_000_000L;
    private static final long MAX_SCHEDULE_ID_BY_EQUALITY = 50_000_000L;
    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(57).patch(0).build();

    public static final String SCHEDULE_IDS_BY_EXPIRY_SEC_KEY = "SCHEDULE_IDS_BY_EXPIRY_SEC";
    public static final String SCHEDULE_ID_BY_EQUALITY_KEY = "SCHEDULE_ID_BY_EQUALITY";

    /**
     * Instantiates a new V0570 (version 0.57.0) schedule schema.
     */
    public V0570ScheduleSchema() {

        super(VERSION);
    }

    @SuppressWarnings("rawtypes")
    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(scheduleIdsByExpirySec(), schedulesByEquality());
    }

    //    @NonNull
    //    @Override
    //    public Set<String> statesToRemove() {
    //        return Set.of(SCHEDULES_BY_EXPIRY_SEC_KEY, SCHEDULES_BY_EQUALITY_KEY);
    //    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);

        log.info("Started migrating Schedule Schema from 0.49.0 to 0.57.0");
        final WritableKVState<ProtoLong, ScheduleIdList> writableScheduleIdsByExpirySec =
                ctx.newStates().get(SCHEDULE_IDS_BY_EXPIRY_SEC_KEY);
        final ReadableKVState<ProtoLong, ScheduleList> readableSchedulesByExpirySec =
                ctx.previousStates().get(SCHEDULES_BY_EXPIRY_SEC_KEY);
        StreamSupport.stream(
                        Spliterators.spliterator(
                                readableSchedulesByExpirySec.keys(), readableSchedulesByExpirySec.size(), DISTINCT),
                        false)
                .forEach(key -> {
                    final var scheduleList = readableSchedulesByExpirySec.get(key);
                    if (scheduleList != null) {
                        writableScheduleIdsByExpirySec.put(key, convertToScheduleIdList(scheduleList));
                    }
                });
        log.info("Migrated {} Schedules from SCHEDULES_BY_EXPIRY_SEC_KEY", readableSchedulesByExpirySec.size());

        final WritableKVState<ProtoBytes, ScheduleID> writableScheduleByEquality =
                ctx.newStates().get(SCHEDULE_ID_BY_EQUALITY_KEY);
        final ReadableKVState<ProtoBytes, ScheduleList> readableSchedulesByEquality =
                ctx.previousStates().get(SCHEDULES_BY_EQUALITY_KEY);
        StreamSupport.stream(
                        Spliterators.spliterator(
                                readableSchedulesByEquality.keys(), readableSchedulesByEquality.size(), DISTINCT),
                        false)
                .forEach(key -> {
                    final var scheduleList = readableSchedulesByEquality.get(key);
                    if (scheduleList != null) {
                        final var schedulerId = requireNonNull(
                                scheduleList.schedules().getFirst().scheduleId());
                        final var newScheduleId = schedulerId.copyBuilder().build();
                        writableScheduleByEquality.put(key, newScheduleId);
                    }
                });
        log.info("Migrated {} Schedules from SCHEDULES_BY_EQUALITY_KEY", readableSchedulesByEquality.size());
    }

    private ScheduleIdList convertToScheduleIdList(@NonNull final ScheduleList scheduleList) {
        return ScheduleIdList.newBuilder()
                .scheduleIds(scheduleList.schedules().stream()
                        .map(Schedule::scheduleId)
                        .filter(Objects::nonNull)
                        .map(id -> id.copyBuilder().build())
                        .toList())
                .build();
    }

    private static StateDefinition<ProtoLong, ScheduleIdList> scheduleIdsByExpirySec() {
        return StateDefinition.onDisk(
                SCHEDULE_IDS_BY_EXPIRY_SEC_KEY,
                ProtoLong.PROTOBUF,
                ScheduleIdList.PROTOBUF,
                MAX_SCHEDULE_IDS_BY_EXPIRY_SEC_KEY);
    }

    private static StateDefinition<ProtoBytes, ScheduleID> schedulesByEquality() {
        return StateDefinition.onDisk(
                SCHEDULE_ID_BY_EQUALITY_KEY, ProtoBytes.PROTOBUF, ScheduleID.PROTOBUF, MAX_SCHEDULE_ID_BY_EQUALITY);
    }
}
