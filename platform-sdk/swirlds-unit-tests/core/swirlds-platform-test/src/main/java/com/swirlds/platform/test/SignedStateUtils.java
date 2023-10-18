/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.system.SwirldState;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.fixtures.state.DummySwirldState;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.util.Random;

public class SignedStateUtils {

    public static SignedState randomSignedState(long seed) {
        return randomSignedState(new Random(seed));
    }

    public static SignedState randomSignedState(Random random) {
        SwirldState state = new DummySwirldState();
        State root = new State();
        root.setSwirldState(state);
        root.setPlatformState(randomPlatformState(random));
        boolean shouldSaveToDisk = random.nextBoolean();
        SignedState signedState =
                new SignedState(TestPlatformContextBuilder.create().build(), root, "test", shouldSaveToDisk);
        signedState.getState().setHash(RandomUtils.randomHash(random));
        return signedState;
    }
}
