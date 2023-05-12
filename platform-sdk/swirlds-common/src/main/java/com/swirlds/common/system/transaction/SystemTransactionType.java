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

package com.swirlds.common.system.transaction;

/**
 * Define currently supported system transaction types
 */
public enum SystemTransactionType {

    /** first byte of a system transaction giving a signed state's (round number, signature) */
    SYS_TRANS_STATE_SIG,

    /** first byte of a system transaction giving all avgPingMilliseconds stats (sent as ping time in microseconds) */
    SYS_TRANS_PING_MICROSECONDS,

    /** first byte of a system transaction giving all avgBytePerSecSent stats (sent as bits per second) */
    SYS_TRANS_BITS_PER_SECOND
}
