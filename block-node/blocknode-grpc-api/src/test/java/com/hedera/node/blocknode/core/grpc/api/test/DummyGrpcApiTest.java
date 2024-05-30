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

package com.hedera.node.blocknode.core.grpc.api.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.node.blocknode.grpc.api.DummyGrpcApi;
import org.junit.jupiter.api.Test;

class DummyGrpcApiTest {

    @Test
    void dummyApiNullCheck() {
        final DummyGrpcApi dummyGrpcApi = null;
        assertNull(dummyGrpcApi);
    }

    @Test
    void dummyApiDoSomethingCheck() {
        final DummyGrpcApi dummyGrpcApi = new DummyGrpcApi() {
            @Override
            public void doSomething() {
                // Do nothing.
            }
        };

        assertDoesNotThrow(dummyGrpcApi::doSomething);
    }
}
