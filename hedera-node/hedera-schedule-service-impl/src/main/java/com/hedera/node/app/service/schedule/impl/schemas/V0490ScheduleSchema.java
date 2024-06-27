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

// https://github.com/hashgraph/hedera-services/issues/13781
//import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_KEYVALUEVALUELEAF_SCHEDULESBYEQUALITY;
//import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_KEYVALUEVALUELEAF_SCHEDULESBYEXPIRYSEC;
//import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_KEYVALUEVALUELEAF_SCHEDULESBYID;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVSCHEDULESBYEQUALITY;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVSCHEDULESBYEXPIRYSEC;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVSCHEDULESBYID;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.schedule.impl.ScheduleStoreUtility;
import com.hedera.node.app.service.schedule.impl.codec.ScheduleServiceStateTranslator;
import com.hedera.pbj.runtime.ParseException;
import com.swirlds.platform.state.spi.WritableKVStateBase;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.block.procedure.primitive.LongProcedure;

/**
 * General schema for the schedule service.
 */
public final class V0490ScheduleSchema extends Schema {

    private static final Logger log = LogManager.getLogger(V0490ScheduleSchema.class);

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
     * Stores data during mono-service migration only.
     */
    private static MerkleScheduledTransactions fs;

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
        if (fs != null) {
            log.info("BBM: Starting schedule migration");

            log.info("BBM: Starting schedule by id migration");
            final WritableKVState<ScheduleID, Schedule> schedulesById =
                    ctx.newStates().get(SCHEDULES_BY_ID_KEY);
            fs.byId().forEachNode((entityNumVirtualKey, scheduleVirtualValue) -> {
                try {
                    var schedule =
                            ScheduleServiceStateTranslator.convertScheduleVirtualValueToSchedule(scheduleVirtualValue);
                    schedulesById.put(
                            ScheduleID.newBuilder()
                                    .scheduleNum(entityNumVirtualKey.getKeyAsLong())
                                    .build(),
                            schedule);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            });
            if (schedulesById.isModified()) ((WritableKVStateBase<?, ?>) schedulesById).commit();
            log.info("BBM: finished schedule by id migration");

            log.info("BBM: doing schedule by expiration migration");
            final WritableKVState<ProtoLong, ScheduleList> schedulesByExpiration =
                    ctx.newStates().get(SCHEDULES_BY_EXPIRY_SEC_KEY);
            fs.byExpirationSecond().forEachNode((secondSinceEpocVirtualKey, sVv) -> sVv.getIds()
                    .forEach((richInstant, scheduleIds) -> {
                        List<Schedule> schedules = new ArrayList<>();
                        scheduleIds.forEach((LongProcedure) scheduleId -> {
                            var schedule = schedulesById.get(ScheduleID.newBuilder()
                                    .scheduleNum(scheduleId)
                                    .build());
                            if (schedule != null) schedules.add(schedule);
                            else {
                                log.info("BBM: ERROR: no schedule for expiration->id "
                                        + richInstant
                                        + " -> "
                                        + scheduleId);
                            }
                        });

                        schedulesByExpiration.put(
                                ProtoLong.newBuilder()
                                        .value(secondSinceEpocVirtualKey.getKeyAsLong())
                                        .build(),
                                ScheduleList.newBuilder().schedules(schedules).build());
                    }));
            if (schedulesByExpiration.isModified()) ((WritableKVStateBase<?, ?>) schedulesByExpiration).commit();
            log.info("BBM: finished schedule by expiration migration");

            log.info("BBM: doing schedule by equality migration");
            final WritableKVState<ProtoBytes, ScheduleList> schedulesByEquality =
                    ctx.newStates().get(SCHEDULES_BY_EQUALITY_KEY);
            fs.byEquality().forEachNode((scheduleEqualityVirtualKey, sevv) -> sevv.getIds()
                    .forEach((scheduleObjHash, scheduleId) -> {
                        var schedule = schedulesById.get(
                                ScheduleID.newBuilder().scheduleNum(scheduleId).build());
                        if (schedule != null) {
                            final var equalityKey = new ProtoBytes(ScheduleStoreUtility.calculateBytesHash(schedule));
                            final var existingList = schedulesByEquality.get(equalityKey);
                            final List<Schedule> existingSchedules = existingList == null
                                    ? new ArrayList<>()
                                    : new ArrayList<>(existingList.schedules());
                            existingSchedules.add(schedule);
                            schedulesByEquality.put(
                                    equalityKey,
                                    ScheduleList.newBuilder()
                                            .schedules(existingSchedules)
                                            .build());
                        } else {
                            log.error("BBM: ERROR: no schedule for scheduleObjHash->id "
                                    + scheduleObjHash + " -> "
                                    + scheduleId);
                        }
                    }));
            if (schedulesByEquality.isModified()) ((WritableKVStateBase<?, ?>) schedulesByEquality).commit();
            log.info("BBM: finished schedule by equality migration");

            log.info("BBM: finished schedule service migration migration");
        } else {
            log.warn("BBM: no schedule 'from' state found");
        }

        fs = null;
    }

    private static StateDefinition<ScheduleID, Schedule> schedulesByIdDef() {
        return StateDefinition.onDisk(
                SCHEDULES_BY_ID_KEY,
                ScheduleID.PROTOBUF,
                Schedule.PROTOBUF,
                // https://github.com/hashgraph/hedera-services/issues/13781
                // FIELD_KEYVALUEVALUELEAF_SCHEDULESBYID,
                FIELD_STATENODE_KVSCHEDULESBYID,
                MAX_SCHEDULES_BY_ID_KEY);
    }

    private static StateDefinition<ProtoLong, ScheduleList> schedulesByExpirySec() {
        return StateDefinition.onDisk(
                SCHEDULES_BY_EXPIRY_SEC_KEY,
                ProtoLong.PROTOBUF,
                ScheduleList.PROTOBUF,
                // https://github.com/hashgraph/hedera-services/issues/13781
                // FIELD_KEYVALUEVALUELEAF_SCHEDULESBYEXPIRYSEC,
                FIELD_STATENODE_KVSCHEDULESBYEXPIRYSEC,
                MAX_SCHEDULES_BY_EXPIRY_SEC_KEY);
    }

    private static StateDefinition<ProtoBytes, ScheduleList> schedulesByEquality() {
        return StateDefinition.onDisk(
                SCHEDULES_BY_EQUALITY_KEY,
                ProtoBytes.PROTOBUF,
                ScheduleList.PROTOBUF,
                // https://github.com/hashgraph/hedera-services/issues/13781
                // FIELD_KEYVALUEVALUELEAF_SCHEDULESBYEQUALITY,
                FIELD_STATENODE_KVSCHEDULESBYEQUALITY,
                MAX_SCHEDULES_BY_EQUALITY);
    }

    /**
     * Used to migrate the state to the new schema. It is not thread safe and is set to null after migration.
     *
     * @param fs the state to migrate from
     */
    public static void setFs(@Nullable final MerkleScheduledTransactions fs) {
        V0490ScheduleSchema.fs = fs;
    }
}
