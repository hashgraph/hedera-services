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
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO: add documentations and comments
@Singleton
public class ThrottleManager {

    private static final Logger logger = LogManager.getLogger(ThrottleManager.class);

    private ThrottleDefinitions throttleDefinitions;
    private List<ThrottleBucket> throttleBuckets;

    @Inject
    public ThrottleManager() {
        // Dagger2
    }

    public void update(@NonNull final Bytes bytes) {
        // Parse the throttle file. If we cannot parse it, we just continue with whatever our previous rate was.
        try {
            throttleDefinitions = ThrottleDefinitions.PROTOBUF.parse(bytes.toReadableSequentialData());
        } catch (final Exception e) {
            // Not being able to parse the exchange rate file is not fatal, and may happen if the exchange rate file
            // was too big for a single file update for example.
            logger.warn("Unable to parse exchange rate file", e);
        }
        throttleBuckets = throttleDefinitions.throttleBuckets();
    }

    /**
     * Gets the current {@link List<ThrottleBucket>}
     * @return The current {@link List<ThrottleBucket>}.
     */
    @NonNull
    public List<ThrottleBucket> throttleBuckets() {
        return throttleBuckets;
    }
}
