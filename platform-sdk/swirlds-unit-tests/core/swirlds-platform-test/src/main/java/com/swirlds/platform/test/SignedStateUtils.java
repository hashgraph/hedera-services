// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.test;

import static com.swirlds.platform.test.PlatformStateUtils.randomPlatformState;
import static com.swirlds.platform.test.fixtures.state.FakeStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;

import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.BasicSoftwareVersion;
import java.util.Random;

public class SignedStateUtils {

    public static SignedState randomSignedState(long seed) {
        return randomSignedState(new Random(seed));
    }

    public static SignedState randomSignedState(Random random) {
        PlatformMerkleStateRoot root =
                new PlatformMerkleStateRoot(version -> new BasicSoftwareVersion(version.minor()));
        FAKE_MERKLE_STATE_LIFECYCLES.initPlatformState(root);
        randomPlatformState(random, root.getWritablePlatformState());
        boolean shouldSaveToDisk = random.nextBoolean();
        SignedState signedState = new SignedState(
                TestPlatformContextBuilder.create().build().getConfiguration(),
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
