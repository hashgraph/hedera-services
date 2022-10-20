package com.hedera.services.state.virtual.entities;

import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OnDiskTokenRelCompatabilityTest {
    @Test
    void canMigrateFromInMemory() {
        final var inMemory = new MerkleTokenRelStatus(
                1_234L, true, false, true, 666_666L);
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
}