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

package com.hedera.node.app.tss;
/**
 * An enum representing the key either active roster or candidate roster.
 * This value will be to key active roster if it is genesis stage.
 */
public enum RosterToKey {
    /**
     * Key the active roster. This is true when we are keying roster on genesis stage.
     */
    ACTIVE_ROSTER,

    /**
     * Key the candidate roster. This is true when we are keying roster on non-genesis stage.
     */
    CANDIDATE_ROSTER,

    /**
     * Key none of the roster. This is true when we are not keying any roster.
     */
    NONE
}
