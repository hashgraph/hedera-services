/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.Key;
import java.util.Optional;

public class RandomKey implements OpProvider {
    public static final String KEY_PREFIX = "Fuzz#";
    public static final int DEFAULT_CEILING_NUM = 100;
    private int ceilingNum = DEFAULT_CEILING_NUM;

    private final RegistrySourcedNameProvider<Key> keys;

    public RandomKey(RegistrySourcedNameProvider<Key> keys) {
        this.keys = keys;
    }

    public RandomKey ceiling(int n) {
        ceilingNum = n;
        return this;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        if (keys.numPresent() >= ceilingNum * 2) {
            return Optional.empty();
        }

        return Optional.ofNullable(newKeyNamed(KEY_PREFIX + keys.numPresent()).shape(SECP_256K1_SHAPE));
    }
}
