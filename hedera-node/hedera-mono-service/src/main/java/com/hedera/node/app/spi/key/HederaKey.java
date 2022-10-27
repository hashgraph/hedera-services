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
package com.hedera.node.app.spi.key;

import static com.hedera.services.legacy.core.jproto.JKey.mapKey;

import com.hederahashgraph.api.proto.java.Key;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;

/** Placeholder implementation for moving JKey */
public interface HederaKey {
    static Optional<HederaKey> asHederaKey(final Key key) {
        try {
            // TODO: Need to move JKey after refactoring, adding equals & hashcode into this package
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
