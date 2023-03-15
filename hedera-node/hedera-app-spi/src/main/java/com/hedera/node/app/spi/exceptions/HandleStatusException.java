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

package com.hedera.node.app.spi.exceptions;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

/**
 * A runtime exception that wraps a {@link ResponseCodeEnum} status. Thrown by
 * components in the {@code handle} workflow to signal a transaction has reached
 * an unsuccessful outcome.
 *
 * <p>In general, this exception is <i>not</i> appropriate to throw when code
 * detects an internal error. Instead, use {@link IllegalStateException} or
 * {@link IllegalArgumentException} as appropriate.
 */
public class HandleStatusException extends RuntimeException {
    private final ResponseCodeEnum status;

    public HandleStatusException(final ResponseCodeEnum status) {
        this.status = status;
    }

    public ResponseCodeEnum getStatus() {
        return status;
    }

    public static void validateTrue(final boolean flag, final ResponseCodeEnum errorStatus) {
        if (!flag) {
            throw new HandleStatusException(errorStatus);
        }
    }

    public static void validateFalse(final boolean flag, final ResponseCodeEnum errorStatus) {
        validateTrue(!flag, errorStatus);
    }
}
