/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.evm.exception;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.tuweni.bytes.Bytes;

public class InvalidTransactionException extends RuntimeException {
    private final ResponseCodeEnum responseCode;
    private final boolean reverting;

    public InvalidTransactionException(final ResponseCodeEnum responseCode) {
        this(responseCode.name(), responseCode, false);
    }

    public InvalidTransactionException(
            final String detailMessage, final ResponseCodeEnum responseCode, boolean reverting) {
        super(detailMessage);
        this.responseCode = responseCode;
        this.reverting = reverting;
    }

    public Bytes messageBytes() {
        final var detail = getMessage();
        return Bytes.of(detail.getBytes());
    }
}
