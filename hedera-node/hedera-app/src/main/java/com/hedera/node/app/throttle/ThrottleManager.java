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

package com.hedera.node.app.throttle;

import com.hedera.hapi.node.transaction.ThrottleBucket;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Parses the throttle definition file and makes it available to the workflows.
 *
 * The throttle implementation will use the {@link ThrottleBucket} list, found in the throttle definition file,
 * to throttle the different Hedera operations(CRYPTO_TRANSFER, FILE_CREATE, TOKEN_CREATE, etc.).
 */
public class ThrottleManager {

    private static final Logger logger = LogManager.getLogger(ThrottleManager.class);
    private static final ThrottleDefinitions DEFAULT_THROTTLE_DEFINITIONS = ThrottleDefinitions.DEFAULT;

    private ThrottleDefinitions throttleDefinitions;
    private com.hederahashgraph.api.proto.java.ThrottleDefinitions throttleDefinitionsProto;
    private List<ThrottleBucket> throttleBuckets;

    public ThrottleManager() {
        // Initialize the throttle definitions. The default is not particularly useful, but isn't null.
        throttleDefinitions = DEFAULT_THROTTLE_DEFINITIONS;
        throttleBuckets = DEFAULT_THROTTLE_DEFINITIONS.throttleBuckets();
    }

    /**
     * Updates the throttle definition information. MUST BE CALLED on the handle thread!
     *
     * @param bytes The protobuf encoded {@link ThrottleDefinitions}.
     */
    public void update(@NonNull final Bytes bytes) {
        // Parse the throttle file. If we cannot parse it, we just continue with whatever our previous rate was.
        try {
            throttleDefinitions = ThrottleDefinitions.PROTOBUF.parse(bytes.toReadableSequentialData());
            throttleDefinitionsProto =
                    com.hederahashgraph.api.proto.java.ThrottleDefinitions.parseFrom(bytes.toByteArray());
        } catch (final Exception e) {
            // Not being able to parse the throttle file is not fatal, and may happen if the throttle file
            // was too big for a single file update for example.
            logger.warn("Unable to parse the throttle file", e);
        }

        final var rawThrottleBuckets = throttleDefinitions.throttleBuckets();
        if (rawThrottleBuckets != null) {
            throttleBuckets = rawThrottleBuckets;
        } else {
            logger.warn("Throttle definition file did not contain throttle buckets!");
            throttleBuckets = DEFAULT_THROTTLE_DEFINITIONS.throttleBuckets();
        }
    }

    /**
     * Gets the current {@link List<ThrottleBucket>}
     * @return The current {@link List<ThrottleBucket>}.
     */
    @NonNull
    public List<ThrottleBucket> throttleBuckets() {
        return throttleBuckets;
    }

    /**
     * Gets the current {@link ThrottleDefinitions}
     * @return The current {@link ThrottleDefinitions}.
     */
    @NonNull
    public ThrottleDefinitions throttleDefinitions() {
        return throttleDefinitions;
    }

    public com.hederahashgraph.api.proto.java.ThrottleDefinitions throttleDefinitionsProto() {
        return throttleDefinitionsProto;
    }
}
