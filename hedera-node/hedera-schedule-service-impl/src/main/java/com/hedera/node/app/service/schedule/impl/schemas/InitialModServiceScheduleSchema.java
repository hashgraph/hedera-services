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

import static com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl.SCHEDULES_BY_EQUALITY_KEY;
import static com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl.SCHEDULES_BY_EXPIRY_SEC_KEY;
import static com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl.SCHEDULES_BY_ID_KEY;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.primitives.ProtoString;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.hedera.node.app.service.schedule.impl.codec.ScheduleServiceStateTranslator;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import org.eclipse.collections.api.block.procedure.primitive.LongProcedure;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;

/**
 * General schema for the schedule service
 * (FUTURE) When mod-service release is finalized, rename this class to e.g.
 * {@code Release47ScheduleSchema} as it will no longer be appropriate to assume
 * this schema is always correct for the current version of the software.
 */
public final class InitialModServiceScheduleSchema extends Schema {
    private MerkleScheduledTransactions fs;

    public InitialModServiceScheduleSchema(final SemanticVersion version, final MerkleScheduledTransactions fs) {
        super(version);
        this.fs = fs;
    }

    @SuppressWarnings("rawtypes")
    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(schedulesByIdDef(), schedulesByExpirySec(), schedulesByEquality());
    }

    private static StateDefinition<ScheduleID, Schedule> schedulesByIdDef() {
        return StateDefinition.inMemory(SCHEDULES_BY_ID_KEY, ScheduleID.PROTOBUF, Schedule.PROTOBUF);
    }

    private static StateDefinition<ProtoLong, ScheduleList> schedulesByExpirySec() {
        return StateDefinition.inMemory(SCHEDULES_BY_EXPIRY_SEC_KEY, ProtoLong.PROTOBUF, ScheduleList.PROTOBUF);
    }

    private static StateDefinition<ProtoString, ScheduleList> schedulesByEquality() {
        return StateDefinition.inMemory(SCHEDULES_BY_EQUALITY_KEY, ProtoString.PROTOBUF, ScheduleList.PROTOBUF);
    }

    @Override
    public void migrate(@NonNull MigrationContext ctx) {
        if (fs != null) {
            System.out.println("BBM: doing schedule migration");

            System.out.println("BBM: doing schedule by id");
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
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            if (schedulesById.isModified()) ((WritableKVStateBase) schedulesById).commit();
            System.out.println("BBM: finished schedule by id");

            System.out.println("BBM: doing schedule by expiration");
            final WritableKVState<ProtoLong, ScheduleList> schedulesByExpiration =
                    ctx.newStates().get(SCHEDULES_BY_EXPIRY_SEC_KEY);
            fs.byExpirationSecond()
                    .forEachNode(new BiConsumer<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue>() {
                        @Override
                        public void accept(
                                SecondSinceEpocVirtualKey secondSinceEpocVirtualKey, ScheduleSecondVirtualValue sVv) {
                            sVv.getIds().forEach(new BiConsumer<RichInstant, ImmutableLongList>() {
                                @Override
                                public void accept(RichInstant richInstant, ImmutableLongList scheduleIds) {

                                    List<Schedule> schedules = new ArrayList<>();
                                    scheduleIds.forEach(new LongProcedure() {
                                        @Override
                                        public void value(long scheduleId) {
                                            var schedule = schedulesById.get(ScheduleID.newBuilder()
                                                    .scheduleNum(scheduleId)
                                                    .build());
                                            if (schedule != null) schedules.add(schedule);
                                            else {
                                                System.out.println("BBM: ERROR: no schedule for expiration->id "
                                                        + richInstant
                                                        + " -> "
                                                        + scheduleId);
                                            }
                                        }
                                    });

                                    schedulesByExpiration.put(
                                            ProtoLong.newBuilder()
                                                    .value(secondSinceEpocVirtualKey.getKeyAsLong())
                                                    .build(),
                                            ScheduleList.newBuilder()
                                                    .schedules(schedules)
                                                    .build());
                                }
                            });
                        }
                    });
            if (schedulesByExpiration.isModified()) ((WritableKVStateBase) schedulesByExpiration).commit();
            System.out.println("BBM: finished schedule by expiration");

            System.out.println("BBM: doing schedule by equality");
            final WritableKVState<ProtoString, ScheduleList> schedulesByEquality =
                    ctx.newStates().get(SCHEDULES_BY_EQUALITY_KEY);
            fs.byEquality().forEachNode((scheduleEqualityVirtualKey, sevv) -> {
                List<Schedule> schedules = new ArrayList<>();
                sevv.getIds().forEach(new BiConsumer<String, Long>() {
                    @Override
                    public void accept(String scheduleObjHash, Long scheduleId) {
                        var schedule = schedulesById.get(
                                ScheduleID.newBuilder().scheduleNum(scheduleId).build());
                        if (schedule != null) schedules.add(schedule);
                        else {
                            System.out.println("BBM: ERROR: no schedule for scheduleObjHash->id "
                                    + scheduleObjHash + " -> "
                                    + scheduleId);
                        }
                    }
                });

                schedulesByEquality.put(
                        ProtoString.newBuilder()
                                .value(String.valueOf(scheduleEqualityVirtualKey.getKeyAsLong()))
                                .build(),
                        ScheduleList.newBuilder().schedules(schedules).build());
            });
            if (schedulesByEquality.isModified()) ((WritableKVStateBase) schedulesByEquality).commit();
            System.out.println("BBM: finished schedule by equality");

            System.out.println("BBM: finished schedule migration");
        }
    }
}
