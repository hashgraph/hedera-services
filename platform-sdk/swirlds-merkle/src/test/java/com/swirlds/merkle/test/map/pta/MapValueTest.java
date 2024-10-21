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

package com.swirlds.merkle.test.map.pta;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.merkle.test.fixtures.map.pta.MapValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MapValueTest {

    @Test
    void getUid() {
        final MapValue value = new MapValue() {
            @Override
            public Hash calculateHash() {
                return null;
            }

            @Override
            public boolean isLeaf() {
                return false;
            }

            @Override
            public MerkleRoute getRoute() {
                return null;
            }

            @Override
            public void setRoute(final MerkleRoute route) {}

            @Override
            public MerkleNode copy() {
                return null;
            }

            @Override
            public void reserve() {}

            @Override
            public boolean tryReserve() {
                return false;
            }

            @Override
            public boolean release() {
                return false;
            }

            @Override
            public boolean isDestroyed() {
                return false;
            }

            @Override
            public int getReservationCount() {
                return 0;
            }

            @Override
            public long getClassId() {
                return 0;
            }

            @Override
            public Hash getHash() {
                return null;
            }

            @Override
            public void setHash(final Hash hash) {}

            @Override
            public int getClassVersion() {
                return 0;
            }
        };

        Assertions.assertEquals(0, value.getUid(), "Should match against default uid");
    }
}
