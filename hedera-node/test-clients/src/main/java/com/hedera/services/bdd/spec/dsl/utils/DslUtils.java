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

package com.hedera.services.bdd.spec.dsl.utils;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.spec.dsl.EvmAddressableEntity;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;

public class DslUtils {
    private DslUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static final Key PBJ_IMMUTABILITY_SENTINEL_KEY =
            Key.newBuilder().keyList(KeyList.DEFAULT).build();
    public static final com.hederahashgraph.api.proto.java.Key PROTO_IMMUTABILITY_SENTINEL_KEY =
            PbjConverter.fromPbj(PBJ_IMMUTABILITY_SENTINEL_KEY);

    /**
     * Substitutes the addresses of EVM addressable entities in the given arguments with their addresses on the given
     * network.
     *
     * @param network the network
     * @param args the arguments
     * @return the arguments with substituted addresses
     */
    public static Object[] withSubstitutedAddresses(
            @NonNull final HederaNetwork network, @NonNull final Object... args) {
        return Arrays.stream(args)
                .map(arg -> {
                    if (arg instanceof EvmAddressableEntity evmAddressableEntity) {
                        return evmAddressableEntity.addressOn(network);
                    } else {
                        return arg;
                    }
                })
                .toArray(Object[]::new);
    }
}
