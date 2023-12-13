package com.swirlds.platform.event.hashing;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Hashes events.
 */
public class EventHasher {
    private final Cryptography cryptography;

    /**
     * Constructs a new event hasher.
     *
     * @param platformContext the platform context
     */
    public EventHasher(@NonNull final PlatformContext platformContext) {
        this.cryptography = platformContext.getCryptography();
    }

    /**
     * Hashes the event and builds the event descriptor.
     *
     * @param event the event to hash
     * @return the hashed event
     */
    public GossipEvent hashEvent(@NonNull final GossipEvent event) {
        cryptography.digestSync(event.getHashedData());
        event.buildDescriptor();
        return event;
    }
}
