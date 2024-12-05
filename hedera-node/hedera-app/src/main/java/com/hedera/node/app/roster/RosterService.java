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

package com.hedera.node.app.roster;

import static com.swirlds.platform.state.service.PlatformStateService.PLATFORM_STATE_SERVICE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.roster.schemas.V057RosterSchema;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.platform.state.service.schemas.V0540RosterSchema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.lifecycle.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Predicate;

/**
 * A {@link com.hedera.hapi.node.state.roster.Roster} implementation of the {@link Service} interface.
 * Registers the roster schemas with the {@link SchemaRegistry}.
 * Not exposed outside `hedera-app`.
 */
public class RosterService implements Service {
    /**
     * During migration to the roster lifecycle, the platform state service may need
     * to set its legacy address books based on the current roster. To do this, we
     * need to ensure the roster service is migrated before the platform state service.
     */
    public static final int MIGRATION_ORDER = PLATFORM_STATE_SERVICE.migrationOrder() - 1;

    public static final String NAME = "RosterService";

    /**
     * The test to use to determine if a candidate roster may be
     * adopted at an upgrade boundary.
     */
    private final Predicate<Roster> canAdopt;

    public RosterService(@NonNull final Predicate<Roster> canAdopt) {
        this.canAdopt = requireNonNull(canAdopt);
    }

    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    @Override
    public int migrationOrder() {
        return MIGRATION_ORDER;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
        registry.register(new V0540RosterSchema());
        registry.register(new V057RosterSchema(canAdopt, WritableRosterStore::new, ReadablePlatformStateStore::new));
    }
}
