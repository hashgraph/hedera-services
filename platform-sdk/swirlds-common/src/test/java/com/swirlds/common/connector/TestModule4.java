/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.common.connector;

import java.time.LocalDateTime;

public class TestModule4 {

    private Input<LocalDateTime> localDateTimeInput;

    private Input<Long> longInput;

    private Input<String> stringInput;

    public TestModule4(ConnectionFactory connectionFactory) {
        this.localDateTimeInput = connectionFactory.createAsyncInput(d -> System.out.println("4 received " + d));
        this.longInput = connectionFactory.createAsyncInput(d -> System.out.println("4 received " + d));
        this.stringInput = connectionFactory.createAsyncInput(d -> System.out.println("4 received " + d));
    }

    public Input<LocalDateTime> getLocalDateTimeInput() {
        return localDateTimeInput;
    }

    public Input<Long> getLongInput() {
        return longInput;
    }

    public Input<String> getStringInput() {
        return stringInput;
    }
}
