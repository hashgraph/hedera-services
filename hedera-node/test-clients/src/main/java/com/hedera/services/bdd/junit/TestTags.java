/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit;

public class TestTags {

    private TestTags() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String CRYPTO = "CRYPTO";
    public static final String SMART_CONTRACT = "SMART_CONTRACT";
    public static final String LONG_RUNNING = "LONG_RUNNING";
    public static final String TOKEN = "TOKEN";
    public static final String RESTART = "RESTART";
    public static final String ND_RECONNECT = "ND_RECONNECT";
}
