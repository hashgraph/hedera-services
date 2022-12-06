/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.test.utils;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import java.util.function.Function;

public class KeyUtils {

    private static final Function<String, Key.Builder> KEY_BUILDER =
            value -> Key.newBuilder().setEd25519(ByteString.copyFrom(value.getBytes()));
    private static final String A_NAME = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static final String B_NAME = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private static final String C_NAME = "cccccccccccccccccccccccccccccccc";

    public static final Key A_THRESHOLD_KEY =
            Key.newBuilder()
                    .setThresholdKey(
                            ThresholdKey.newBuilder()
                                    .setThreshold(2)
                                    .setKeys(
                                            KeyList.newBuilder()
                                                    .addKeys(KEY_BUILDER.apply(A_NAME))
                                                    .addKeys(KEY_BUILDER.apply(B_NAME))
                                                    .addKeys(KEY_BUILDER.apply(C_NAME))))
                    .build();
    public static final Key A_KEY_LIST =
            Key.newBuilder()
                    .setKeyList(
                            KeyList.newBuilder()
                                    .addKeys(KEY_BUILDER.apply(A_NAME))
                                    .addKeys(KEY_BUILDER.apply(B_NAME))
                                    .addKeys(KEY_BUILDER.apply(C_NAME)))
                    .build();
    public static final Key A_COMPLEX_KEY =
            Key.newBuilder()
                    .setThresholdKey(
                            ThresholdKey.newBuilder()
                                    .setThreshold(2)
                                    .setKeys(
                                            KeyList.newBuilder()
                                                    .addKeys(KEY_BUILDER.apply(A_NAME))
                                                    .addKeys(KEY_BUILDER.apply(B_NAME))
                                                    .addKeys(A_THRESHOLD_KEY)))
                    .build();
    public static final Key B_COMPLEX_KEY =
            Key.newBuilder()
                    .setThresholdKey(
                            ThresholdKey.newBuilder()
                                    .setThreshold(2)
                                    .setKeys(
                                            KeyList.newBuilder()
                                                    .addKeys(KEY_BUILDER.apply(A_NAME))
                                                    .addKeys(KEY_BUILDER.apply(B_NAME))
                                                    .addKeys(A_COMPLEX_KEY)))
                    .build();
    public static final Key C_COMPLEX_KEY =
            Key.newBuilder()
                    .setThresholdKey(
                            ThresholdKey.newBuilder()
                                    .setThreshold(2)
                                    .setKeys(
                                            KeyList.newBuilder()
                                                    .addKeys(KEY_BUILDER.apply(A_NAME))
                                                    .addKeys(KEY_BUILDER.apply(B_NAME))
                                                    .addKeys(B_COMPLEX_KEY)))
                    .build();
}
