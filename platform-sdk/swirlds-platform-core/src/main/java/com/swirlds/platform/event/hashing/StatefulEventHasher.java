package com.swirlds.platform.event.hashing;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * An implementation of {@link EventHasher} that is stateful and thus not safe to use by multiple threads concurrently.
 */
public class StatefulEventHasher implements EventHasher {
    private final HashingOutputStream hashingOutputStream = new HashingOutputStream(
            DigestType.SHA_384.buildDigest());
    private final SerializableDataOutputStream outputStream = new SerializableDataOutputStream(hashingOutputStream);

    @NonNull
    @Override
    public GossipEvent hashEvent(@NonNull final GossipEvent event) {
        try {
            event.serializeLegacyHashBytes(outputStream);
            event.setHash(new Hash(hashingOutputStream.getDigest(), DigestType.SHA_384));
            return event;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
