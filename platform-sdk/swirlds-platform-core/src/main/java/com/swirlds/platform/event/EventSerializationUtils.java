// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.event;

import com.hedera.hapi.platform.event.GossipEvent;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * A utility class for serializing and deserializing events using legacy serialization. This will be removed as soon as
 * event serialization is completely migrated to protobuf.
 */
public final class EventSerializationUtils {
    private EventSerializationUtils() {
        // Utility class
    }
    /**
     * Serialize and then deserialize the given {@link PlatformEvent}.
     *
     * @param original the original event
     * @return the deserialized event
     * @throws IOException if an I/O error occurs
     */
    @NonNull
    public static PlatformEvent serializeDeserializePlatformEvent(@NonNull final PlatformEvent original)
            throws IOException {
        try (final ByteArrayOutputStream io = new ByteArrayOutputStream()) {
            final SerializableDataOutputStream out = new SerializableDataOutputStream(io);
            out.writePbjRecord(original.getGossipEvent(), GossipEvent.PROTOBUF);
            out.flush();
            final SerializableDataInputStream in =
                    new SerializableDataInputStream(new ByteArrayInputStream(io.toByteArray()));
            return new PlatformEvent(in.readPbjRecord(GossipEvent.PROTOBUF));
        }
    }
}
