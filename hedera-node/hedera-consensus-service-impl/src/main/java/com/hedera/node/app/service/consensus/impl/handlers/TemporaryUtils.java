package com.hedera.node.app.service.consensus.impl.handlers;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.parser.KeyProtoParser;
import com.hedera.hapi.node.base.writer.KeyWriter;
import com.hedera.hashgraph.pbj.runtime.io.DataInputStream;
import com.hedera.hashgraph.pbj.runtime.io.DataOutputStream;
import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;

public class TemporaryUtils {
    private TemporaryUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Key fromGrpcKey(@NonNull final com.hederahashgraph.api.proto.java.Key grpcKey) {
        try (final var bais = new ByteArrayInputStream(grpcKey.toByteArray())) {
            return KeyProtoParser.parse(new DataInputStream(bais));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Optional<HederaKey> fromPbjKey(@NonNull final Key pbjKey) {
        try (final var baos = new ByteArrayOutputStream(); final var dos = new DataOutputStream(baos)) {
            KeyWriter.write(pbjKey, dos);
            dos.flush();
            final var grpcKey = com.hederahashgraph.api.proto.java.Key.parseFrom(baos.toByteArray());
            return asHederaKey(grpcKey);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
