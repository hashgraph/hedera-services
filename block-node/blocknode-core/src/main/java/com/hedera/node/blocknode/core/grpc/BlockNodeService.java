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

package com.hedera.node.blocknode.core.grpc;

// import com.hedera.block.node.api.proto.java.BlockServiceGrpc;
// import com.hedera.block.node.api.proto.java.BlocksPutIfAbsentRequest;
// import com.hedera.block.node.api.proto.java.BlocksPutIfAbsentResponse;
// import com.hedera.block.node.api.proto.java.BlocksPutIfAbsentResponseCode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockNodeService // extends BlockServiceGrpc.BlockServiceImplBase
 {
    //    @Override
    //    public StreamObserver<BlocksPutIfAbsentRequest> blocksPutIfAbsent(
    //            StreamObserver<BlocksPutIfAbsentResponse> responseObserver) {
    //        return new StreamObserver<BlocksPutIfAbsentRequest>() {
    //            @Override
    //            public void onNext(BlocksPutIfAbsentRequest request) {
    //                logger.info("Received request: " + request);
    //                BlocksPutIfAbsentResponseCode responseCode = Math.random() > 0.5
    //                        ? BlocksPutIfAbsentResponseCode.BLOCKS_PUT_IF_ABSENT_SUCCESS
    //                        : BlocksPutIfAbsentResponseCode.BLOCKS_PUT_IF_ABSENT_BLOCK_ALREADY_EXISTS;
    //                BlocksPutIfAbsentResponse response = BlocksPutIfAbsentResponse.newBuilder()
    //                        .setStatus(responseCode)
    //                        .build();
    //                responseObserver.onNext(response);
    //            }
    //
    //            @Override
    //            public void onError(Throwable throwable) {
    //                logger.warn("Warning " + throwable.getMessage());
    //            }
    //
    //            @Override
    //            public void onCompleted() {
    //                responseObserver.onCompleted();
    //            }
    //        };
    //    }

}
