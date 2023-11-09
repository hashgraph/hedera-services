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

package com.hedera.node.app.spi.workflows;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@link HandleException} specialization that indicates that a resource limit has been exceeded.
 */
public class ResourceExhaustedException extends HandleException {
    public ResourceExhaustedException(@NonNull final ResponseCodeEnum status) {
        super(requireNonNull(status));
    }

    /**
     * Asserts that a resource is not exhausted.
     *
     * @param flag the flag to check
     * @param code the status to throw if the flag is false
     */
    public static void validateResource(final boolean flag, @NonNull final ResponseCodeEnum code) {
        requireNonNull(code);
        if (!flag) {
            throw new ResourceExhaustedException(code);
        }
    }
}
