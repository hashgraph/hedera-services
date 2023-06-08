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

package com.hedera.node.app.hapi.fees.test;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;

public class KeyUtils {
    public static Key A_CONTRACT_KEY =
            Key.newBuilder().setContractID(IdUtils.asContract("1.2.3")).build();
    public static Key A_THRESHOLD_KEY = Key.newBuilder()
            .setThresholdKey(ThresholdKey.newBuilder()
                    .setThreshold(2)
                    .setKeys(KeyList.newBuilder()
                            .addKeys(Key.newBuilder()
                                    .setEd25519(ByteString.copyFrom("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes())))
                            .addKeys(Key.newBuilder()
                                    .setEd25519(ByteString.copyFrom("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes())))
                            .addKeys(Key.newBuilder()
                                    .setEd25519(ByteString.copyFrom("cccccccccccccccccccccccccccccccc".getBytes())))))
            .build();
    public static Key A_KEY_LIST = Key.newBuilder()
            .setKeyList(KeyList.newBuilder()
                    .addKeys(Key.newBuilder()
                            .setEd25519(ByteString.copyFrom("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes())))
                    .addKeys(Key.newBuilder()
                            .setEd25519(ByteString.copyFrom("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes())))
                    .addKeys(Key.newBuilder()
                            .setEd25519(ByteString.copyFrom("cccccccccccccccccccccccccccccccc".getBytes()))))
            .build();
    public static Key A_COMPLEX_KEY = Key.newBuilder()
            .setThresholdKey(ThresholdKey.newBuilder()
                    .setThreshold(2)
                    .setKeys(KeyList.newBuilder()
                            .addKeys(Key.newBuilder()
                                    .setEd25519(ByteString.copyFrom("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes())))
                            .addKeys(Key.newBuilder()
                                    .setEd25519(ByteString.copyFrom("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes())))
                            .addKeys(A_THRESHOLD_KEY)))
            .build();
    public static Key B_COMPLEX_KEY = Key.newBuilder()
            .setThresholdKey(ThresholdKey.newBuilder()
                    .setThreshold(2)
                    .setKeys(KeyList.newBuilder()
                            .addKeys(Key.newBuilder()
                                    .setEd25519(ByteString.copyFrom("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes())))
                            .addKeys(Key.newBuilder()
                                    .setEd25519(ByteString.copyFrom("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes())))
                            .addKeys(A_COMPLEX_KEY)))
            .build();
    public static Key C_COMPLEX_KEY = Key.newBuilder()
            .setThresholdKey(ThresholdKey.newBuilder()
                    .setThreshold(2)
                    .setKeys(KeyList.newBuilder()
                            .addKeys(Key.newBuilder()
                                    .setEd25519(ByteString.copyFrom("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes())))
                            .addKeys(Key.newBuilder()
                                    .setEd25519(ByteString.copyFrom("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes())))
                            .addKeys(B_COMPLEX_KEY)))
            .build();
}
