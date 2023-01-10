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
package com.hedera.node.app.service.mono.state.virtual.entities;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import org.junit.jupiter.api.Test;

class OnDiskTokenRelCompatabilityTest {
    @Test
    void canMigrateFromInMemory() {
        final var inMemory = new MerkleTokenRelStatus(1_234L, true, false, true, 666_666L);
        final var onDisk = OnDiskTokenRel.from(inMemory);
        assertEquals(inMemory.getKey(), onDisk.getKey());
        assertEquals(inMemory.getPrev(), onDisk.getPrev());
        assertEquals(inMemory.getNext(), onDisk.getNext());
        assertEquals(inMemory.getBalance(), onDisk.getBalance());
        assertEquals(inMemory.isFrozen(), onDisk.isFrozen());
        assertEquals(inMemory.isKycGranted(), onDisk.isKycGranted());
        assertEquals(inMemory.isAutomaticAssociation(), onDisk.isAutomaticAssociation());
        assertEquals(inMemory.getRelatedTokenNum(), onDisk.getRelatedTokenNum());
    }

    @Test
    void flagsWork() {
        final var subject = new OnDiskTokenRel();
        subject.setFrozen(true);
        subject.setKycGranted(true);
        subject.setAutomaticAssociation(true);
        assertTrue(subject.isFrozen());
        assertTrue(subject.isKycGranted());
        assertTrue(subject.isAutomaticAssociation());

        subject.setFrozen(false);
        subject.setKycGranted(false);
        subject.setAutomaticAssociation(false);
        assertFalse(subject.isFrozen());
        assertFalse(subject.isKycGranted());
        assertFalse(subject.isAutomaticAssociation());
    }
}
