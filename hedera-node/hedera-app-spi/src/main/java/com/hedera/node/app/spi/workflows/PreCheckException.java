/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
 * Thrown if the request itself is bad. The protobuf decoded correctly, but it failed one or more of
 * the ingestion pipeline pre-checks.
 */
public class PreCheckException extends Exception {
    private final ResponseCodeEnum responseCode;

    /**
     * Constructor of {@code PreCheckException}
     *
     * @param responseCode the {@link ResponseCodeEnum responseCode}
     * @throws NullPointerException if {@code responseCode} is {@code null}
     */
    public PreCheckException(@NonNull final ResponseCodeEnum responseCode) {
        super();
        this.responseCode = requireNonNull(responseCode);
    }

    /**
     * Returns the {@code responseCode} of this {@code PreCheckException}
     *
     * @return the {@link ResponseCodeEnum responseCode}
     */
    @NonNull
    public ResponseCodeEnum responseCode() {
        return responseCode;
    }

    @Override
    public String toString() {
        return "PreCheckException{" + "responseCode=" + responseCode + '}';
    }
}
