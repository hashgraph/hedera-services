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

/** Gives the Services index order of Merkle node children. */
public final class StateChildIndices {
    public static final int UNIQUE_TOKENS = 0;
    public static final int TOKEN_ASSOCIATIONS = 1;
    public static final int TOPICS = 2;
    public static final int STORAGE = 3;
    public static final int ACCOUNTS = 4;
    public static final int TOKENS = 5;
    public static final int NETWORK_CTX = 6;
    public static final int SPECIAL_FILES = 7;
    public static final int SCHEDULE_TXS = 8;
    public static final int RECORD_STREAM_RUNNING_HASH = 9;
    public static final int ADDRESS_BOOK = 10;
    public static final int CONTRACT_STORAGE = 11;
    public static final int STAKING_INFO = 12;

    public static final int NUM_025X_CHILDREN = 12;
    public static final int NUM_POST_0260_CHILDREN = 13;

    private StateChildIndices() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
