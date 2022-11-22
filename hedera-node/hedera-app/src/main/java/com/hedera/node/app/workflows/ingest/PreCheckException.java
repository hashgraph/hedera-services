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
package com.hedera.node.app.workflows.ingest;

import static java.util.Objects.requireNonNull;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Thrown if the request itself is bad. The protobuf decoded correctly, but it failed one or more of
 * the ingestion pipeline pre-checks.
 */
public class PreCheckException extends Exception {
    private final ResponseCodeEnum responseCode;

    /**
     * Constructor of {@code PreCheckException}
     *
     * @param responseCode the {@link ResponseCodeEnum responseCode}
     * @param message an error message with further details
     * @throws NullPointerException if {@code responseCode} is {@code null}
     */
    public PreCheckException(
            @Nonnull final ResponseCodeEnum responseCode, @Nullable final String message) {
        super(message);
        this.responseCode = requireNonNull(responseCode);
    }

    /**
     * Returns the {@code responseCode} of this {@code PreCheckException}
     *
     * @return the {@link ResponseCodeEnum responseCode}
     */
    @Nonnull
    public ResponseCodeEnum responseCode() {
        return responseCode;
    }
}
