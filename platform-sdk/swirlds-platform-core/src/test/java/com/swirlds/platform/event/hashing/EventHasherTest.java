package com.swirlds.platform.event.hashing;

import static org.junit.jupiter.api.Assertions.*;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.util.Random;
import org.junit.jupiter.api.Test;

class EventHasherTest {
    /**
     * Tests that different hashing methods produce the same hash.
     */
    @Test
    void objHashingEquivalenceTest(){
        final Random random = Randotron.create();

        final PlatformEvent event = new TestingEventBuilder(random)
                .setAppTransactionCount(2)
                .setSystemTransactionCount(2)
                .setSelfParent(new TestingEventBuilder(random).build())
                .setOtherParent(new TestingEventBuilder(random).build())
                .build();

        new PbjBytesHasher().hashEvent(event);
        final Hash bytesHash = event.getHash();
        event.invalidateHash();
        new PbjStreamHasher().hashEvent(event);

        assertEquals(bytesHash, event.getHash(), "PBJ bytes hasher and PBJ stream hasher should produce the same hash");
    }

}