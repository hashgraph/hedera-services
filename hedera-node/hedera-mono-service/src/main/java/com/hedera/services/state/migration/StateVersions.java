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

import static com.hedera.services.context.properties.SerializableSemVers.forHapiAndHedera;

import com.hedera.services.context.properties.SerializableSemVers;
import javax.annotation.Nullable;

/** Gives the versions of the current and previous world states. */
public final class StateVersions {
    private static final String BASE_0260_VERSION = "0.26.0";
    private static final String BASE_0270_VERSION = "0.27.0";

    // For the record,
    //   - Release 0.7.x was state version 1
    //   - Release 0.8.x was state version 2
    //   - Release 0.9.x was state version 3
    //   - Release 0.10.x was state version 4
    //   - Release 0.11.x was state version 5
    //   - Release 0.12.x was state version 6
    //   - Release 0.13.x was state version 7
    //   - Release 0.14.x was state version 8
    //   - Release 0.15.x was state version 9
    //   - Release 0.16.x was state version 10
    //   - Release 0.17.x was state version 11
    //   - Release 0.18.x was state version 12
    //   - Release 0.19.x and 0.20.x were state version 13
    //   - Release 0.21.x was state version 14
    //   - Release 0.22.x and 0.23.x were state version 15
    //   - Release 0.24.x was state version 17
    //   - Release 0.25.x was state version 18
    //   - Release 0.26.x was state version 19
    //   - Release 0.27.x was state version 20
    //   - Release 0.28.x was state version 21
    //   - Release 0.29.x was state version 22
    //   - Release 0.30.x was state version 23
    //   - Release 0.31.x was state version 24
    //   - Release 0.32.x was state version 25
    public static final int RELEASE_025X_VERSION = 18;
    public static final int RELEASE_0260_VERSION = 19;
    public static final int RELEASE_0270_VERSION = 20;
    public static final int RELEASE_0320_VERSION = 25;

    public static final SerializableSemVers LAST_025X_VERSION =
            forHapiAndHedera("0.25.1", "0.25.4");
    public static final SerializableSemVers FIRST_026X_VERSION =
            forHapiAndHedera(BASE_0260_VERSION, BASE_0260_VERSION);
    public static final SerializableSemVers LAST_026X_VERSION =
            forHapiAndHedera(BASE_0260_VERSION, "0.26.3");
    public static final SerializableSemVers FIRST_027X_VERSION =
            forHapiAndHedera(BASE_0270_VERSION, BASE_0270_VERSION);
    public static final SerializableSemVers LAST_027X_VERSION =
            forHapiAndHedera(BASE_0270_VERSION, "0.27.3");
    public static final SerializableSemVers FIRST_028X_VERSION =
            forHapiAndHedera("0.28.0", "0.28.0");
    public static final SerializableSemVers FIRST_030X_VERSION =
            forHapiAndHedera("0.30.0", "0.30.0");

    public static final int MINIMUM_SUPPORTED_VERSION = RELEASE_0270_VERSION;
    public static final int CURRENT_VERSION = RELEASE_0320_VERSION;

    @Nullable
    public static SerializableSemVers lastSoftwareVersionOf(final int stateVersion) {
        return switch (stateVersion) {
            case RELEASE_025X_VERSION -> LAST_025X_VERSION;
            case RELEASE_0260_VERSION -> LAST_026X_VERSION;
            case RELEASE_0270_VERSION -> LAST_027X_VERSION;
            default -> null;
        };
    }

    private StateVersions() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
