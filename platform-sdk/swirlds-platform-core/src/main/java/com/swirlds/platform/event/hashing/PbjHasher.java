package com.swirlds.platform.event.hashing;

import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.platform.event.EventPayload;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.event.PlatformEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;


public class PbjHasher implements EventHasher {

    private final HashingOutputStream hashingOutputStream = new HashingOutputStream(DigestType.SHA_384.buildDigest());
    private final SerializableDataOutputStream outputStream = new SerializableDataOutputStream(hashingOutputStream);

    private final HashingOutputStream payloadHashStream = new HashingOutputStream(DigestType.SHA_384.buildDigest());
    private final SerializableDataOutputStream payloadOutput = new SerializableDataOutputStream(payloadHashStream);

    @Override
    public PlatformEvent hashEvent(@NonNull final PlatformEvent event) {
        EventCore.PROTOBUF.toBytes(event.getUnsignedEvent().getEventCore()).writeTo(outputStream);
        event.getUnsignedEvent().getPayloads().forEach(payload -> {
            EventPayload.PROTOBUF.toBytes(payload).writeTo(payloadOutput);
            try {
                hashingOutputStream.write(payloadHashStream.getDigest());
            } catch (IOException e) {
                throw new RuntimeException("An exception occurred while trying to hash an event!", e);
            }
        });

        event.setHash(new Hash(hashingOutputStream.getDigest(), DigestType.SHA_384));

        return event;
    }
}
