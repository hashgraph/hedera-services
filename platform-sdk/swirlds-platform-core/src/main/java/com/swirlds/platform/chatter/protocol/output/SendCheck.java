/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.chatter.protocol.output;

/**
 * Called before sending a message to determine the appropriate action to take
 *
 * @param <T>
 * 		the type of message of the inquiry
 */
public interface SendCheck<T> {
    /**
     * Check what the appropriate action to take is for the provided message
     *
     * @param message
     * 		the message queried
     * @return what action to take, see {@link SendAction}
     */
    SendAction shouldSend(T message);
}
