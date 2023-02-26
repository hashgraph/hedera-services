/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static com.swirlds.common.test.RandomUtils.randomHash;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.platform.internal.EventImpl;

@ConstructableIgnored
public class DummyEvent extends EventImpl {

    private boolean reachedConsensus = true;
    private Hash hash = randomHash();

    public DummyEvent(boolean isConsensusEvent) {
        super(new BaseEventHashedData(), new BaseEventUnhashedData());
        reachedConsensus = isConsensusEvent;
    }

    @Override
    public synchronized boolean isConsensus() {
        return reachedConsensus;
    }

    @Override
    public synchronized Hash getBaseHash() {
        return hash;
    }
}
