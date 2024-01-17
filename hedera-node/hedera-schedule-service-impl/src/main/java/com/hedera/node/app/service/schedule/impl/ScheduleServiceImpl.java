/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.primitives.ProtoString;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.codec.ScheduleServiceStateTranslator;
import com.hedera.node.app.service.schedule.impl.schemas.InitialModServiceScheduleSchema;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.eclipse.collections.api.block.procedure.primitive.LongProcedure;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;

/**
 * Standard implementation of the {@link ScheduleService} {@link com.hedera.node.app.spi.Service}.
 */
public final class ScheduleServiceImpl implements ScheduleService {
    public static final String SCHEDULES_BY_ID_KEY = "SCHEDULES_BY_ID";
    public static final String SCHEDULES_BY_EXPIRY_SEC_KEY = "SCHEDULES_BY_EXPIRY_SEC";
    public static final String SCHEDULES_BY_EQUALITY_KEY = "SCHEDULES_BY_EQUALITY";

    private MerkleScheduledTransactions fs;

    public void setFs(MerkleScheduledTransactions fs) {
        this.fs = fs;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry, final SemanticVersion version) {
        registry.register(scheduleSchema(RELEASE_045_VERSION));

        registry.register(new Schema(RELEASE_MIGRATION_VERSION) {
            @Override
            public void migrate(@NonNull MigrationContext ctx) {
                if (fs != null) {
                    System.out.println("BBM: doing schedule migration");

                    System.out.println("BBM: doing schedule by id");
                    final WritableKVState<ScheduleID, Schedule> schedulesById =
                            ctx.newStates().get(SCHEDULES_BY_ID_KEY);
                    fs.byId().forEachNode(new BiConsumer<EntityNumVirtualKey, ScheduleVirtualValue>() {
                        @Override
                        public void accept(
                                EntityNumVirtualKey entityNumVirtualKey, ScheduleVirtualValue scheduleVirtualValue) {
                            try {
                                var schedule = ScheduleServiceStateTranslator.convertScheduleVirtualValueToSchedule(
                                        scheduleVirtualValue);
                                schedulesById.put(
                                        ScheduleID.newBuilder()
                                                .scheduleNum(entityNumVirtualKey.getKeyAsLong())
                                                .build(),
                                        schedule);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
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
                                        SecondSinceEpocVirtualKey secondSinceEpocVirtualKey,
                                        ScheduleSecondVirtualValue sVv) {
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
                    fs.byEquality()
                            .forEachNode(new BiConsumer<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue>() {
                                @Override
                                public void accept(
                                        ScheduleEqualityVirtualKey scheduleEqualityVirtualKey,
                                        ScheduleEqualityVirtualValue sevv) {

                                    List<Schedule> schedules = new ArrayList<>();
                                    sevv.getIds().forEach(new BiConsumer<String, Long>() {
                                        @Override
                                        public void accept(String scheduleObjHash, Long scheduleId) {
                                            var schedule = schedulesById.get(ScheduleID.newBuilder()
                                                    .scheduleNum(scheduleId)
                                                    .build());
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
                                            ScheduleList.newBuilder()
                                                    .schedules(schedules)
                                                    .build());
                                }
                            });
                    if (schedulesByEquality.isModified()) ((WritableKVStateBase) schedulesByEquality).commit();
                    System.out.println("BBM: finished schedule by equality");

                    System.out.println("BBM: finished schedule migration");
                }
            }
        });
    }

    private Schema scheduleSchema(final SemanticVersion version) {
        // Everything in memory for now
        return new InitialModServiceScheduleSchema(version);
    }
}
