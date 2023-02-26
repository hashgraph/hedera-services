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

package com.swirlds.platform.chatter.protocol;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.utility.Clearable;

/**
 * Provides chatter messages on request
 */
public interface MessageProvider extends Clearable {
    /**
     * Provide a message to send over the network.
     *
     * @return a message to send, or null if no message should be sent right now
     */
    SelfSerializable getMessage();
}
