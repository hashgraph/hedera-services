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

package com.swirlds.platform.chatter.protocol.messages;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;

/**
 * A stripped down description of an event.
 */
public interface EventDescriptor extends SelfSerializable {

    /**
     * Get the hash of the event.
     *
     * @return the event's hash
     */
    Hash getHash();

    /**
     * Get the node ID of the event's creator.
     *
     * @return a node ID
     */
    long getCreator();

    /**
     * Get the generation of the event described
     *
     * @return the generation of the event described
     */
    long getGeneration();
}
