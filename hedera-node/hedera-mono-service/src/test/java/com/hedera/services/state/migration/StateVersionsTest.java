/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.migration;

import static com.hedera.services.state.migration.StateVersions.LAST_025X_VERSION;
import static com.hedera.services.state.migration.StateVersions.LAST_026X_VERSION;
import static com.hedera.services.state.migration.StateVersions.LAST_027X_VERSION;
import static com.hedera.services.state.migration.StateVersions.RELEASE_025X_VERSION;
import static com.hedera.services.state.migration.StateVersions.RELEASE_0260_VERSION;
import static com.hedera.services.state.migration.StateVersions.RELEASE_0270_VERSION;
import static com.hedera.services.state.migration.StateVersions.lastSoftwareVersionOf;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StateVersionsTest {
    @Test
    void getsExpectedLastVersionsForSupportedMigrationsOnly() {
        assertSame(LAST_025X_VERSION, lastSoftwareVersionOf(RELEASE_025X_VERSION));
        assertSame(LAST_026X_VERSION, lastSoftwareVersionOf(RELEASE_0260_VERSION));
        assertSame(LAST_027X_VERSION, lastSoftwareVersionOf(RELEASE_0270_VERSION));
        assertNull(lastSoftwareVersionOf(RELEASE_0270_VERSION + 1));
    }
}
