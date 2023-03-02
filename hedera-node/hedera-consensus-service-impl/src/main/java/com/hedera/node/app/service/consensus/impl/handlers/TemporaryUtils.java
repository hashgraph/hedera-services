/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl.handlers;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.parser.KeyProtoParser;
import com.hedera.hapi.node.base.writer.KeyWriter;
import com.hedera.hashgraph.pbj.runtime.io.Bytes;
import com.hedera.hashgraph.pbj.runtime.io.DataInputStream;
import com.hedera.hashgraph.pbj.runtime.io.DataOutputStream;
import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

public class TemporaryUtils {
    private TemporaryUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static byte[] unwrapPbj(final Bytes bytes) {
        final var ret = new byte[bytes.getLength()];
        try {
            bytes.getBytes(0, ret);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        return ret;
    }

    public static Key fromGrpcKey(@NonNull final com.hederahashgraph.api.proto.java.Key grpcKey) {
        try (final var bais = new ByteArrayInputStream(grpcKey.toByteArray())) {
            return KeyProtoParser.parse(new DataInputStream(bais));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Optional<HederaKey> fromPbjKey(@NonNull final Key pbjKey) {
        try (final var baos = new ByteArrayOutputStream();
                final var dos = new DataOutputStream(baos)) {
            KeyWriter.write(pbjKey, dos);
            dos.flush();
            final var grpcKey = com.hederahashgraph.api.proto.java.Key.parseFrom(baos.toByteArray());
            return asHederaKey(grpcKey);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
