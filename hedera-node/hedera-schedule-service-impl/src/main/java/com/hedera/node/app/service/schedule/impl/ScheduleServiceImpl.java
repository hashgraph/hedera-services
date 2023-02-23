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
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleGetInfoHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleSignHandler;
import com.hedera.node.app.service.schedule.impl.serdes.MonoSchedulingStateAdapterSerdes;
import com.hedera.node.app.spi.service.Service;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.serdes.MonoMapSerdesAdapter;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Standard implementation of the {@link ScheduleService} {@link Service}.
 */
public final class ScheduleServiceImpl implements ScheduleService {

    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().setMinor(34).build();

    public static final String SCHEDULING_STATE_KEY = "SCHEDULING_STATE";
    public static final String SCHEDULES_BY_ID_KEY = "SCHEDULES_BY_ID";
    public static final String SCHEDULES_BY_EXPIRY_SEC_KEY = "SCHEDULES_BY_EXPIRY_SEC";
    public static final String SCHEDULES_BY_EQUALITY_KEY = "SCHEDULES_BY_EQUALITY";

    private final ScheduleCreateHandler scheduleCreateHandler;

    private final ScheduleDeleteHandler scheduleDeleteHandler;

    private final ScheduleGetInfoHandler scheduleGetInfoHandler;

    private final ScheduleSignHandler scheduleSignHandler;

    /**
     * Creates a new {@link ScheduleServiceImpl} instance.
     */
    public ScheduleServiceImpl() {
        this.scheduleCreateHandler = new ScheduleCreateHandler();
        this.scheduleDeleteHandler = new ScheduleDeleteHandler();
        this.scheduleGetInfoHandler = new ScheduleGetInfoHandler();
        this.scheduleSignHandler = new ScheduleSignHandler();
    }

    /**
     * Returns the {@link ScheduleCreateHandler} instance.
     *
     * @return the {@link ScheduleCreateHandler} instance.
     */
    @NonNull
    public ScheduleCreateHandler getScheduleCreateHandler() {
        return scheduleCreateHandler;
    }

    /**
     * Returns the {@link ScheduleDeleteHandler} instance.
     *
     * @return the {@link ScheduleDeleteHandler} instance.
     */
    @NonNull
    public ScheduleDeleteHandler getScheduleDeleteHandler() {
        return scheduleDeleteHandler;
    }

    /**
     * Returns the {@link ScheduleGetInfoHandler} instance.
     *
     * @return the {@link ScheduleGetInfoHandler} instance.
     */
    @NonNull
    public ScheduleGetInfoHandler getScheduleGetInfoHandler() {
        return scheduleGetInfoHandler;
    }

    /**
     * Returns the {@link ScheduleSignHandler} instance.
     *
     * @return the {@link ScheduleSignHandler} instance.
     */
    @NonNull
    public ScheduleSignHandler getScheduleSignHandler() {
        return scheduleSignHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandler() {
        return Set.of(scheduleCreateHandler, scheduleDeleteHandler, scheduleSignHandler);
    }

    @NonNull
    @Override
    public Set<QueryHandler> getQueryHandler() {
        return Set.of(scheduleGetInfoHandler);
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
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
