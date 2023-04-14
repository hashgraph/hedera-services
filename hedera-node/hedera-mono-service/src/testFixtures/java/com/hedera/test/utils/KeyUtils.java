/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class KeyUtils {

    private static final Function<String, Key.Builder> KEY_BUILDER =
            value -> Key.newBuilder().ed25519(Bytes.wrap(value.getBytes()));
    private static final String A_NAME = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static final String B_NAME = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private static final String C_NAME = "cccccccccccccccccccccccccccccccc";

    public static final Key A_THRESHOLD_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    KEY_BUILDER.apply(A_NAME).build(),
                                    KEY_BUILDER.apply(B_NAME).build(),
                                    KEY_BUILDER.apply(C_NAME).build())
                            .build()))
            .build();
    public static final Key A_KEY_LIST = Key.newBuilder()
            .keyList(KeyList.newBuilder()
                    .keys(
                            KEY_BUILDER.apply(A_NAME).build(),
                            KEY_BUILDER.apply(B_NAME).build(),
                            KEY_BUILDER.apply(C_NAME).build()))
            .build();
    public static final Key A_COMPLEX_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    KEY_BUILDER.apply(A_NAME).build(),
                                    KEY_BUILDER.apply(B_NAME).build(),
                                    A_THRESHOLD_KEY)))
            .build();
    public static final Key B_COMPLEX_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    KEY_BUILDER.apply(A_NAME).build(),
                                    KEY_BUILDER.apply(B_NAME).build(),
                                    A_COMPLEX_KEY)))
            .build();
    public static final Key C_COMPLEX_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    KEY_BUILDER.apply(A_NAME).build(),
                                    KEY_BUILDER.apply(B_NAME).build(),
                                    B_COMPLEX_KEY)))
            .build();

    public static List<com.hederahashgraph.api.proto.java.Key> sanityRestored(List<? extends HederaKey> jKeys) {
        return jKeys.stream()
                .map(
                        jKey -> {
                            try {
                                return JKey.mapJKey((JKey) jKey);
                            } catch (Exception ignore) {
                                throw new AssertionError("All keys should be mappable!");
                            }
                        })
                .toList();
    }

    public static List<com.hederahashgraph.api.proto.java.Key> sanityRestored(
            Set<? extends HederaKey> jKeys) {
        return jKeys.stream()
                .map(
                        jKey -> {
                            try {
                                return JKey.mapJKey((JKey) jKey);
                            } catch (Exception ignore) {
                                throw new AssertionError("All keys should be mappable!");
                            }
                        })
                .toList();
    }

    public static List<com.hederahashgraph.api.proto.java.Key> sanityRestored(
            Set<? extends HederaKey> jKeys) {
        return jKeys.stream()
                .map(
                        jKey -> {
                            try {
                                return JKey.mapJKey((JKey) jKey);
                            } catch (Exception ignore) {
                                throw new AssertionError("All keys should be mappable!");
                            }
                        })
                .toList();
    }

    public static com.hederahashgraph.api.proto.java.Key sanityRestored(HederaKey jKey) {
        try {
            return JKey.mapJKey((JKey) jKey);
        } catch (Exception ignore) {
            throw new AssertionError("All keys should be mappable!");
        }
    }

    public static com.hedera.hapi.node.base.Key sanityRestoredToPbj(@NonNull HederaKey jKey) {
        requireNonNull(jKey);
        try {
            return toPbj(JKey.mapJKey((JKey) jKey));
        } catch (Exception ignore) {
            throw new AssertionError("All keys should be mappable! But failed for " + jKey);
        }
    }

    public static List<com.hedera.hapi.node.base.Key> sanityRestoredToPbj(
            @NonNull List<? extends HederaKey> jKeys) {
        requireNonNull(jKeys);
        return jKeys.stream()
                .map(
                        jKey -> {
                            try {
                                return toPbj(JKey.mapJKey((JKey) jKey));
                            } catch (Exception ignore) {
                                throw new AssertionError(
                                        "All keys should be mappable! But failed for " + jKey);
                            }
                        })
                .toList();
    }

    public static List<com.hedera.hapi.node.base.Key> sanityRestoredToPbj(
            @NonNull Set<? extends HederaKey> jKeys) {
        requireNonNull(jKeys);
        return jKeys.stream()
                .map(
                        jKey -> {
                            try {
                                return toPbj(JKey.mapJKey((JKey) jKey));
                            } catch (Exception ignore) {
                                throw new AssertionError(
                                        "All keys should be mappable! But failed for " + jKey);
                            }
                        })
                .toList();
    }

    public static List<com.hedera.hapi.node.base.Key> sanityRestoredToPbj(
            @NonNull Set<? extends HederaKey> jKeys) {
        requireNonNull(jKeys);
        return jKeys.stream()
                .map(
                        jKey -> {
                            try {
                                return toPbj(JKey.mapJKey((JKey) jKey));
                            } catch (Exception ignore) {
                                throw new AssertionError(
                                        "All keys should be mappable! But failed for " + jKey);
                            }
                        })
                .toList();
    }
}
