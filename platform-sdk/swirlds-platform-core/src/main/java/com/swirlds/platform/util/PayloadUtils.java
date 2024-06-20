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

package com.swirlds.platform.util;

import com.hedera.hapi.platform.event.EventPayload.PayloadOneOfType;
import com.hedera.hapi.platform.event.StateSignaturePayload;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Utility class for handling PJB payloads.
 */
public final class PayloadUtils {
    private PayloadUtils() {}

    /**
     * Get the size of a payload.
     *
     * @param payload the payload to get the size of
     * @return the size of the payload
     */
    public static long getPayloadSize(@NonNull final OneOf<PayloadOneOfType> payload) {
        if (PayloadOneOfType.APPLICATION_PAYLOAD.equals(payload.kind())) {
            return Integer.BYTES // add the the size of array length field
                    + ((Bytes) payload.as()).length(); // add the size of the array
        } else if (PayloadOneOfType.STATE_SIGNATURE_PAYLOAD.equals(payload.kind())) {
            final StateSignaturePayload stateSignaturePayload = payload.as();
            return Long.BYTES // round
                    + Integer.BYTES // signature array length
                    + stateSignaturePayload.signature().length()
                    + Integer.BYTES // hash array length
                    + stateSignaturePayload.hash().length()
                    + Integer.BYTES; // epochHash, always null, which is SerializableStreamConstants.NULL_VERSION
        } else {
            throw new IllegalArgumentException("Unknown payload type: " + payload.kind());
        }
    }

    /**
     * Check if a payload is a system payload.
     *
     * @param payload the payload to check
     * @return {@code true} if the payload is a system payload, {@code false} otherwise
     */
    public static boolean isSystemPayload(@NonNull final OneOf<PayloadOneOfType> payload) {
        return !PayloadOneOfType.APPLICATION_PAYLOAD.equals(payload.kind());
    }
}
