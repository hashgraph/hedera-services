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

package com.hedera.node.blocknode.core;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.blocknode.core.spi.DummyCoreSpi;
import com.hedera.node.blocknode.filesystem.api.DummyFileSystemApi;
import com.hedera.node.blocknode.filesystem.local.LocalFileSystem;
import com.hedera.node.blocknode.filesystem.s3.S3FileSystem;
import com.hedera.node.blocknode.grpc.api.DummyGrpcApi;
import com.hedera.node.blocknode.state.BlockNodeState;

public interface Example {

    AccountID accountIdFrom(byte[] bytes);

    DummyCoreSpi spi();

    DummyGrpcApi grpcApi();

    DummyFileSystemApi fileSystemApi();

    default BlockNodeState newState() {
        return new BlockNodeState();
    }

    default DummyFileSystemApi s3FileSystem() {
        return new S3FileSystem();
    }

    default DummyFileSystemApi localFileSystem() {
        return new LocalFileSystem();
    }
}
