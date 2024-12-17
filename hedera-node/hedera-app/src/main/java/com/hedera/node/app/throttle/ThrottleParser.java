/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.OPERATION_REPEATED_IN_BUCKET_GROUPS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNPARSEABLE_THROTTLE_DEFINITIONS;
import static java.util.Collections.disjoint;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.node.app.hapi.utils.sysfiles.validation.ExpectedCustomThrottles;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Infrastructure component to parse and validate bytes from a throttle definition
 * system file.
 */
@Singleton
public class ThrottleParser {
    public static final Set<HederaFunctionality> EXPECTED_OPS = ExpectedCustomThrottles.ACTIVE_OPS.stream()
            .map(protoOp -> HederaFunctionality.fromProtobufOrdinal(protoOp.getNumber()))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(HederaFunctionality.class)));

    @Inject
    public ThrottleParser() {
        // Dagger2
    }

    public record ValidatedThrottles(
            @NonNull ThrottleDefinitions throttleDefinitions, @NonNull ResponseCodeEnum successStatus) {
        public ValidatedThrottles {
            requireNonNull(successStatus);
            requireNonNull(throttleDefinitions);
        }
    }

    /**
     * Parses the throttle definitions from the given bytes and validates them.
     * Returns a {@link ValidatedThrottles} object containing the parsed and
     * validated throttle definitions and the success status to use if these
     * definitions came from a HAPI transaction.
     *
     * @param bytes the protobuf encoded {@link ThrottleDefinitions}.
     * @return the {@link ValidatedThrottles}
     * @throws HandleException if the throttle definitions are invalid
     */
    public ValidatedThrottles parse(@NonNull final Bytes bytes) {
        try {
            final var throttleDefinitions = ThrottleDefinitions.PROTOBUF.parse(bytes.toReadableSequentialData());
            validate(throttleDefinitions);
            final var successStatus =
                    allExpectedOperations(throttleDefinitions) ? SUCCESS : SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
            return new ValidatedThrottles(throttleDefinitions, successStatus);
        } catch (ParseException e) {
            throw new HandleException(UNPARSEABLE_THROTTLE_DEFINITIONS);
        }
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
        return customizedOps.containsAll(EXPECTED_OPS);
    }

    /**
     * Checks if there are throttle groups defined with zero operations per second.
     */
    private void checkForZeroOpsPerSec(ThrottleDefinitions throttleDefinitions) {
        for (var bucket : throttleDefinitions.throttleBuckets()) {
            for (var group : bucket.throttleGroups()) {
                if (group.milliOpsPerSec() == 0) {
                    throw new HandleException(THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC);
                }
            }
        }
    }

    /**
     * Checks if an operation was assigned to more than one throttle group in a given bucket.
     */
    private void checkForRepeatedOperations(ThrottleDefinitions throttleDefinitions) {
        for (var bucket : throttleDefinitions.throttleBuckets()) {
            final Set<HederaFunctionality> seenSoFar = new HashSet<>();
            for (var group : bucket.throttleGroups()) {
                final var functions = group.operations();
                if (!disjoint(seenSoFar, functions)) {
                    throw new HandleException(OPERATION_REPEATED_IN_BUCKET_GROUPS);
                }
                seenSoFar.addAll(functions);
            }
        }
    }
}
