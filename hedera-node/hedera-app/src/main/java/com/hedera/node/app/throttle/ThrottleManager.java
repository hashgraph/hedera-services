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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNPARSEABLE_THROTTLE_DEFINITIONS;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.ThrottleBucket;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.node.app.hapi.utils.sysfiles.validation.ExpectedCustomThrottles;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
    public static final Set<HederaFunctionality> expectedOps = ExpectedCustomThrottles.ACTIVE_OPS.stream()
            .map(protoOp -> HederaFunctionality.fromProtobufOrdinal(protoOp.getNumber()))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(HederaFunctionality.class)));

    private ThrottleDefinitions throttleDefinitions;
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
    public ResponseCodeEnum update(@NonNull final Bytes bytes) {
        // Parse the throttle file. If we cannot parse it, we just continue with whatever our previous rate was.
        final ThrottleDefinitions tempThrottleDefinitions;
        try {
            tempThrottleDefinitions = ThrottleDefinitions.PROTOBUF.parse(bytes.toReadableSequentialData());
        } catch (IOException e) {
            throw new HandleException(UNPARSEABLE_THROTTLE_DEFINITIONS);
        }
        validate(tempThrottleDefinitions);
        throttleDefinitions = tempThrottleDefinitions;
        throttleBuckets = throttleDefinitions.throttleBuckets();

        return allExpectedOperations(throttleDefinitions) ? SUCCESS : SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
    }

    /**
     * Checks if the throttle definitions are valid.
     */
    private void validate(ThrottleDefinitions throttleDefinitions) {
        checkForZeroOpsPerSec(throttleDefinitions);
        checkForRepeatedOperations(throttleDefinitions);
    }

    /**
     * Checks if there are missing {@link HederaFunctionality} operations from the expected ones that should be throttled.
     */
    private boolean allExpectedOperations(ThrottleDefinitions throttleDefinitions) {
        final Set<HederaFunctionality> customizedOps = EnumSet.noneOf(HederaFunctionality.class);
        for (final var bucket : throttleDefinitions.throttleBuckets()) {
            for (final var group : bucket.throttleGroups()) {
                customizedOps.addAll(group.operations());
            }
        }
        return customizedOps.containsAll(expectedOps);
    }

    /**
     * Checks if there are throttle groups defined with zero operations per second.
     */
    private void checkForZeroOpsPerSec(ThrottleDefinitions throttleDefinitions) {
        final var buckets = throttleDefinitions.throttleBuckets() != null
                ? throttleDefinitions.throttleBuckets()
                : DEFAULT_THROTTLE_DEFINITIONS.throttleBuckets();
        for (var bucket : buckets) {
            for (var group : bucket.throttleGroups()) {
                if (group.milliOpsPerSec() == 0) {
                    throw new HandleException(ResponseCodeEnum.THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC);
                }
            }
        }
    }

    /**
     * Checks if an operation was assigned to more than one throttle group in a given bucket.
     */
    private void checkForRepeatedOperations(ThrottleDefinitions throttleDefinitions) {
        final var buckets = throttleDefinitions.throttleBuckets() != null
                ? throttleDefinitions.throttleBuckets()
                : DEFAULT_THROTTLE_DEFINITIONS.throttleBuckets();
        for (var bucket : buckets) {
            final Set<HederaFunctionality> seenSoFar = new HashSet<>();
            for (var group : bucket.throttleGroups()) {
                final var functions = group.operations();
                if (!Collections.disjoint(seenSoFar, functions)) {
                    throw new HandleException(ResponseCodeEnum.OPERATION_REPEATED_IN_BUCKET_GROUPS);
                }
                seenSoFar.addAll(functions);
            }
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
}
