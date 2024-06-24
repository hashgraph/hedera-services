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

package com.hedera.node.blocknode.core.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.blocknode.core.Example;
import com.hedera.node.blocknode.core.spi.DummyCoreSpi;
import com.hedera.node.blocknode.filesystem.api.DummyFileSystemApi;
import com.hedera.node.blocknode.grpc.api.DummyGrpcApi;
import org.junit.jupiter.api.Test;

class ExampleTest {

    @Test
    void exampleNullCheck() {
        final Example example = null;
        assertNull(example);
    }

    @Test
    void exampleSpiNullCheck() {
        final Example example = new Example() {
            @Override
            public AccountID accountIdFrom(byte[] bytes) {
                return null;
            }

            @Override
            public DummyCoreSpi spi() {
                return null;
            }

            @Override
            public DummyGrpcApi grpcApi() {
                return null;
            }

            @Override
            public DummyFileSystemApi fileSystemApi() {
                return localFileSystem();
            }
        };

        assertNotNull(example);
        assertNull(example.spi());
        assertNull(example.grpcApi());
        assertNotNull(example.fileSystemApi());
        assertNotNull(example.localFileSystem());
        assertNotNull(example.s3FileSystem());
        assertNotNull(example.newState());

        assertNull(example.newState().applicationState());
        assertDoesNotThrow(example.fileSystemApi()::doSomething);
        assertDoesNotThrow(example.s3FileSystem()::doSomething);
        assertDoesNotThrow(example.localFileSystem()::doSomething);
    }
}
