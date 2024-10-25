/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test;

import static com.swirlds.platform.test.PlatformStateUtils.randomPlatformState;
import static com.swirlds.platform.test.fixtures.state.FakeMerkleStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;

import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.BasicSoftwareVersion;
import java.util.Random;

public class SignedStateUtils {

    public static SignedState randomSignedState(long seed) {
        return randomSignedState(new Random(seed));
    }

    public static SignedState randomSignedState(Random random) {
        MerkleStateRoot root =
                new MerkleStateRoot(FAKE_MERKLE_STATE_LIFECYCLES, version -> new BasicSoftwareVersion(version.minor()));
        final Configuration configuration =
                ConfigurationBuilder.create().autoDiscoverExtensions().build();
        randomPlatformState(random, root.getWritablePlatformState(configuration));
        boolean shouldSaveToDisk = random.nextBoolean();
        SignedState signedState = new SignedState(
                TestPlatformContextBuilder.create().build(),
                CryptoStatic::verifySignature,
                root,
                "test",
                shouldSaveToDisk,
                false,
                false);
        signedState.getState().setHash(RandomUtils.randomHash(random));
        return signedState;
    }
}
