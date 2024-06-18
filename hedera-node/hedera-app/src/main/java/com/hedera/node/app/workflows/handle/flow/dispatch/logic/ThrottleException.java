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

package com.hedera.node.app.workflows.handle.flow.dispatch.logic;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class is used to throw a {@link ThrottleException} when a transaction is gas throttled.
 */
public class ThrottleException extends Exception {
    private final ResponseCodeEnum status;

    public ThrottleException(@NonNull final ResponseCodeEnum status) {
        this.status = requireNonNull(status);
    }

    /**
     * Gets the status of the exception.
     *
     * @return the status
     */
    public ResponseCodeEnum getStatus() {
        return status;
    }
}
