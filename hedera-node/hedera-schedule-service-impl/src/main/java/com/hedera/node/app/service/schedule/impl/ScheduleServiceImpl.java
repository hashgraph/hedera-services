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
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;

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
        registry.register(scheduleSchema(version));
    }

    private Schema scheduleSchema(final SemanticVersion version) {
        // Everything in memory for now
        final var scheduleSchema = new InitialModServiceScheduleSchema(version, fs);

        // Once the 'from' state is passed in to the schema class, we don't need that reference in this class anymore.
        // We don't want to keep these references around because, in the case of migrating from mono to mod service, we
        // want the old mono state routes to disappear
        fs = null;

        return scheduleSchema;
    }
}
