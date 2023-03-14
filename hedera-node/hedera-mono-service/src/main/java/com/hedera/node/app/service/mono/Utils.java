/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono;

import static com.hedera.node.app.service.mono.legacy.core.jproto.JKey.mapKey;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.Key;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;

// This class should not exist. Right now we have code that needs to map from a JKey to a
// HederaKey, but that should be an implementation detail handled somewhere in the token
// service. It shouldn't be on HederaKey (which shouldn't know anything about JKey).
// So some utilities exist here that we can use for now, but these need to be removed.
// They're just scaffolding while we refactor.
public class Utils {

    /** Prohibit creation of this instance */
    private Utils() {}

    // This method shouldn't be here. It needs to find a new home.
    public static Optional<HederaKey> asHederaKey(final Key key) {
        try {
            // Need to move JKey after refactoring, adding equals & hashcode into this package
            final var fcKey = mapKey(key);
            if (!fcKey.isValid()) {
                return Optional.empty();
            }
            return Optional.of(fcKey);
        } catch (DecoderException ignore) {
            return Optional.empty();
        }
    }

    public static Optional<HederaKey> asHederaKey(@NonNull final com.hedera.hapi.node.base.Key key) {
        requireNonNull(key);
        try {
            // Need to move JKey after refactoring, adding equals & hashcode into this package
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
