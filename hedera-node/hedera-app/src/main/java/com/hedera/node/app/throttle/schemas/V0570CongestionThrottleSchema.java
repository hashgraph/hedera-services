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

package com.hedera.node.app.throttle.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class V0570CongestionThrottleSchema extends Schema {

    private static final Logger log = LogManager.getLogger(V0490CongestionThrottleSchema.class);

    // todo check this value
    private static final long MAX_SCHEDULE_IDS_BY_EXPIRY_SEC_KEY = 50_000_000L;
    public static final String SCHEDULE_THROTTLE_USAGE_PER_SECOND_STATE_KEY = "SCHEDULE_THROTTLE_USAGE_PER_SECOND";

    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(57).patch(0).build();

    public V0570CongestionThrottleSchema() {
        super(VERSION);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.onDisk(
                SCHEDULE_THROTTLE_USAGE_PER_SECOND_STATE_KEY,
                ProtoLong.PROTOBUF,
                ThrottleUsageSnapshots.PROTOBUF,
                MAX_SCHEDULE_IDS_BY_EXPIRY_SEC_KEY));
    }

    /** {@inheritDoc} */
    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        if (ctx.previousVersion() == null) {
            log.info("Creating genesis throttle snapshots and congestion level starts for schedules");
            // At genesis we put empty throttle usage snapshots into their singleton
            // state just to ensure they exist
            final var scheduleThrottleSnapshot = ctx.newStates().get(SCHEDULE_THROTTLE_USAGE_PER_SECOND_STATE_KEY);
            scheduleThrottleSnapshot.put(ProtoLong.DEFAULT, ThrottleUsageSnapshots.DEFAULT);
        }
    }
}
