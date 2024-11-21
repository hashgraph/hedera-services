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

package com.swirlds.common;

/**
 * A class with constants identifying Roster entities in state.
 */
public final class RosterStateId {
    private RosterStateId() {}

    /** The name of a service that owns Roster entities in state. */
    public static final String NAME = "RosterService";
    /** The name of the RosterMap. */
    public static final String ROSTER_KEY = "ROSTERS";
    /** The name of the RosterState. */
    public static final String ROSTER_STATES_KEY = "ROSTER_STATE";
}