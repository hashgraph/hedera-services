/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.chatter.communication;

/**
 * Constants used by {@link ChatterProtocol}
 */
public class Constants {
    /** sent when there are no chatter messages to send */
    public static final byte KEEPALIVE = 1;
    /** sent to indicate that a payload follows */
    public static final byte PAYLOAD = 2;
    /** sent to indicate that a chatter session is ending */
    public static final byte END = 3;

    /** the amount of time to sleep if there is no payload to send */
    public static final int NO_PAYLOAD_SLEEP_MS = 10;
}
