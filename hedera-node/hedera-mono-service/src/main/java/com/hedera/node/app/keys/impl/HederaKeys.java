/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.keys.impl;

import static com.hedera.services.legacy.core.jproto.JKey.mapKey;

import com.hedera.node.app.spi.keys.HederaKey;
import com.hederahashgraph.api.proto.java.Key;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.NotImplementedException;

/** Utility class for {@link HederaKey}. */
public class HederaKeys {
    private HederaKeys() {
        throw new UnsupportedOperationException("Utility Class");
    }
    /**
     * Converts a {@link HederaKey} to {@link Key} .
     *
     * @param key given HederaKey
     * @return protobuf Key for the given HederaKey
     */
    public static Optional<Key> toProto(final HederaKey key) {
        throw new NotImplementedException();
    }
    /**
     * Converts a {@link Key} to {@link HederaKey} .
     *
     * @param key given protobuf Key
     * @return HederaKey format for the given protobuf key
     */
    public static Optional<HederaKey> fromProto(final Key key, int depth) {
        throw new NotImplementedException();
    }

    /**
     * Converts a {@link Key} to {@link HederaKey}.
     *
     * @param key given protobuf Key
     * @return HederaKey
     */
    public static Optional<HederaKey> asHederaKey(final Key key) {
        try {
            // TODO : Move the mapKey method to be used in fromProto method
            final var fcKey = mapKey(key);
            if (!fcKey.isValid()) {
                return Optional.empty();
            }
            return Optional.of(fcKey);
        } catch (DecoderException ignore) {
            return Optional.empty();
        }
    }
}
