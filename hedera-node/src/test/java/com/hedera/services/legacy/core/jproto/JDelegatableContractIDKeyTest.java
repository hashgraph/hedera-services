/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.legacy.core.jproto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import org.junit.jupiter.api.Test;

class JDelegatableContractIDKeyTest {
    private final ContractID id = IdUtils.asContract("0.0.1234");

    @Test
    void constructorsWork() {
        final var subject1 = new JDelegatableContractIDKey(id);
        final var subject2 =
                new JDelegatableContractIDKey(
                        id.getShardNum(), id.getRealmNum(), id.getContractNum());

        assertEquals(id, subject1.getContractID());
        assertEquals(id, subject2.getContractID());
        assertEquals(0, subject1.getShardNum());
        assertEquals(0, subject2.getShardNum());
        assertEquals(0, subject1.getRealmNum());
        assertEquals(0, subject2.getRealmNum());
        assertEquals(1234, subject1.getContractNum());
        assertEquals(1234, subject2.getContractNum());

        assertTrue(subject1.hasDelegatableContractId());
        assertTrue(subject2.hasDelegatableContractId());
    }

    @Test
    void toStringWorks() {
        final var desired = "<JDelegatableContractId: 0.0.1234>";
        final var subject = new JDelegatableContractIDKey(id);

        assertEquals(desired, subject.toString());
    }

    @Test
    void isNotAPureContractIdKey() {
        final var subject = new JDelegatableContractIDKey(id);
        assertFalse(subject.hasContractID());
        assertNull(subject.getContractIDKey());
    }

    @Test
    void isEmptyAndValidAreOpposites() {
        final var nonEmptySubject = new JDelegatableContractIDKey(0, 0, 1);
        final var emptySubject = new JDelegatableContractIDKey(0, 0, 0);
        assertTrue(emptySubject.isEmpty());
        assertFalse(nonEmptySubject.isEmpty());
        assertFalse(emptySubject.isValid());
        assertTrue(nonEmptySubject.isValid());
    }
}
