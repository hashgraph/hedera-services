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

package com.hedera.node.app.statedumpers.utils;

import static com.hedera.node.app.statedumpers.legacy.JKey.mapKey;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.Optional;

public class LegacyTypeUtils {
    public static @NonNull Optional<HederaKey> fromPbjKey(@Nullable final Key pbjKey) {
        if (pbjKey == null) {
            return Optional.empty();
        }
        try (final var baos = new ByteArrayOutputStream();
                final var dos = new WritableStreamingData(baos)) {
            Key.PROTOBUF.write(pbjKey, dos);
            final var grpcKey = com.hederahashgraph.api.proto.java.Key.parseFrom(baos.toByteArray());
            return asHederaKey(grpcKey);
        } catch (final IOException e) {
            // Should be impossible, so just propagate an exception
            throw new IllegalStateException("Invalid conversion from PBJ for Key", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<HederaKey> asHederaKey(final com.hederahashgraph.api.proto.java.Key key) {
        try {
            // Need to move JKey after refactoring, adding equals & hashcode into this package
            final var fcKey = mapKey(key);
            if (!fcKey.isValid()) {
                return Optional.empty();
            }
            return Optional.of(fcKey);
        } catch (InvalidKeyException ignore) {
            return Optional.empty();
        }
    }
}
