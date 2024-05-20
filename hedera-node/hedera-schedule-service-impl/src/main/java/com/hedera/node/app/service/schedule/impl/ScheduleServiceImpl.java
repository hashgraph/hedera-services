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

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.schemas.InitialModServiceScheduleSchema;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Standard implementation of the {@link ScheduleService} {@link Service}.
 */
public final class ScheduleServiceImpl implements ScheduleService {
    public static final String SCHEDULES_BY_ID_KEY = "SCHEDULES_BY_ID";
    public static final String SCHEDULES_BY_EXPIRY_SEC_KEY = "SCHEDULES_BY_EXPIRY_SEC";
    public static final String SCHEDULES_BY_EQUALITY_KEY = "SCHEDULES_BY_EQUALITY";

    private InitialModServiceScheduleSchema scheduleSchema;

    public void setFs(@Nullable final MerkleScheduledTransactions fs) {
        scheduleSchema.setFs(fs);
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry, @NonNull final SemanticVersion version) {
        scheduleSchema = new InitialModServiceScheduleSchema(version);
        registry.register(scheduleSchema);
    }
}
