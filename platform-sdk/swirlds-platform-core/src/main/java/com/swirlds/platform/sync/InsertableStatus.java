/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.sync;

/**
 * A status representing the ability of the {@link ShadowGraph} to insert an event.
 */
public enum InsertableStatus {
    /** The event can be inserted into the shadow graph. */
    INSERTABLE,
    /** The event cannot be inserted into the shadow graph because it is null. */
    NULL_EVENT,
    /** The event cannot be inserted into the shadow graph because it is already in the shadow graph. */
    DUPLICATE_SHADOW_EVENT,
    /** The event cannot be inserted into the shadow graph because it belongs to an expired generation. */
    EXPIRED_EVENT
}
