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

package com.swirlds.platform.event;

import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType;
import com.hedera.hapi.platform.event.GossipEvent;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.SoftwareVersion;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.event.EventDescriptorWrapper;
import com.swirlds.common.event.PlatformEvent;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.system.event.UnsignedEvent;
import com.swirlds.platform.system.StaticSoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A utility class for serializing and deserializing events using legacy serialization. This will be removed as soon as
 * event serialization is completely migrated to protobuf.
 */
public final class EventSerializationUtils {
    private static final int MAX_ARRAY_LENGTH = 1_000_000;
    private static final long APPLICATION_TRANSACTION_CLASS_ID = 0x9ff79186f4c4db97L;
    private static final int APPLICATION_TRANSACTION_VERSION = 1;
    private static final long STATE_SIGNATURE_CLASS_ID = 0xaf7024c653caabf4L;
    private static final int STATE_SIGNATURE_VERSION = 3;
    private static final int UNSIGNED_EVENT_VERSION = 4;
    private static final int PLATFORM_EVENT_VERSION = 3;

    private EventSerializationUtils() {
        // Utility class
    }

    /**
     * Deserialize the event as {@link UnsignedEvent}.
     *
     * @param in the stream from which this object is to be read
     * @return the deserialized event
     * @throws IOException if unsupported transaction types are encountered
     */
    @NonNull
    private static UnsignedEvent deserializeUnsignedEvent(@NonNull final SerializableDataInputStream in)
            throws IOException {
        Objects.requireNonNull(in, "The input stream must not be null");
        final int version = in.readInt();
        if (version != UNSIGNED_EVENT_VERSION) {
            throw new IOException("Unsupported version: " + version);
        }

        final SoftwareVersion softwareVersion =
                in.readSerializable(StaticSoftwareVersion.getSoftwareVersionClassIdSet());

        final NodeId creatorId = in.readSerializable(false, NodeId::new);
        if (creatorId == null) {
            throw new IOException("creatorId is null");
        }
        final EventDescriptorWrapper selfParent = EventDescriptorWrapper.deserialize(in);
        final List<EventDescriptorWrapper> otherParents = EventDescriptorWrapper.deserializeList(in);
        final long birthRound = in.readLong();

        final Instant timeCreated = in.readInstant();
        in.readInt(); // read serialized length
        final List<EventTransaction> transactionList = new ArrayList<>();
        final int transactionSize = in.readInt();
        if (transactionSize > 0) {
            in.readBoolean(); // allSameClass
        }
        for (int i = 0; i < transactionSize; i++) {
            final long classId = in.readLong();
            final int classVersion = in.readInt();
            if (classId == APPLICATION_TRANSACTION_CLASS_ID) {
                final OneOf<TransactionOneOfType> oneOf = new OneOf<>(
                        TransactionOneOfType.APPLICATION_TRANSACTION,
                        deserializeApplicationTransaction(in, classVersion));
                transactionList.add(new EventTransaction(oneOf));
            } else if (classId == STATE_SIGNATURE_CLASS_ID) {
                final OneOf<TransactionOneOfType> oneOf = new OneOf<>(
                        TransactionOneOfType.STATE_SIGNATURE_TRANSACTION,
                        deserializeStateSignatureTransaction(in, classVersion));
                transactionList.add(new EventTransaction(oneOf));
            } else {
                throw new IOException("Unknown classId: " + classId);
            }
        }

        return new UnsignedEvent(
                softwareVersion, creatorId, selfParent, otherParents, birthRound, timeCreated, transactionList);
    }

    @NonNull
    private static Bytes deserializeApplicationTransaction(
            @NonNull final SerializableDataInputStream in, final int classVersion) throws IOException {
        if (classVersion != APPLICATION_TRANSACTION_VERSION) {
            throw new IOException("Unsupported application class version: " + classVersion);
        }
        return Bytes.wrap(Objects.requireNonNull(in.readByteArray(MAX_ARRAY_LENGTH)));
    }

    @NonNull
    private static StateSignatureTransaction deserializeStateSignatureTransaction(
            @NonNull final SerializableDataInputStream in, final int classVersion) throws IOException {
        if (classVersion != STATE_SIGNATURE_VERSION) {
            throw new IOException("Unsupported state signature class version: " + classVersion);
        }
        final byte[] sigBytes = in.readByteArray(MAX_ARRAY_LENGTH);
        final byte[] hashBytes = in.readByteArray(MAX_ARRAY_LENGTH);
        final long round = in.readLong();
        in.readInt(); // epochHash is always null
        return new StateSignatureTransaction(round, Bytes.wrap(sigBytes), Bytes.wrap(hashBytes));
    }

    /**
     * Deserialize the event as {@link PlatformEvent}.
     *
     * @param in          the stream from which this object is to be read
     * @param readVersion if true, the event version number will be read from the stream
     * @return the deserialized event
     * @throws IOException if unsupported transaction types are encountered
     */
    @NonNull
    public static PlatformEvent deserializePlatformEvent(
            @NonNull final SerializableDataInputStream in, final boolean readVersion) throws IOException {
        if (readVersion) {
            final int eventVersion = in.readInt();
            if (eventVersion != PLATFORM_EVENT_VERSION) {
                throw new IOException("Unsupported event version: " + eventVersion);
            }
        }
        final UnsignedEvent unsignedEvent = EventSerializationUtils.deserializeUnsignedEvent(in);
        final byte[] signature = in.readByteArray(SignatureType.RSA.signatureLength());

        return new PlatformEvent(unsignedEvent, signature);
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
