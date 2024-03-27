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

package com.hedera.node.blocknode.filesystem.s3;

import com.hedera.node.blocknode.core.spi.DummyCoreSpi;
import com.hedera.node.blocknode.filesystem.api.FileSystemApi;
import com.hedera.services.stream.v7.proto.Block;

public class S3FileSystem implements FileSystemApi {
    @Override
    public void doSomething() {
        final DummyCoreSpi dummyCoreSpi = () -> {
            // Do nothing.
        };

        dummyCoreSpi.doSomething();
    }

    @Override
    public void writeBlock(Block block) {}

    @Override
    public Block readBlock(long number) {
        return null;
    }
}
