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
package com.hedera.services.exceptions;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.tuweni.bytes.Bytes;

/**
 * Captures a failure in transaction processing to be captured by the {@link
 * com.hedera.services.txns.TransitionRunner} and used to set the final resolved status of the
 * transaction.
 *
 * <p>Unless the contained {@link ResponseCodeEnum} is exactly {@code FAIL_INVALID}, this represents
 * some form of user error. The {@code FAIL_INVALID} code indicates an internal system error; and it
 * is usually desirable in that case to include a detail message in the constructor.
 */
public class InvalidTransactionException extends RuntimeException {

    private final ResponseCodeEnum responseCode;
    private final boolean reverting;

    public InvalidTransactionException(final ResponseCodeEnum responseCode) {
        this(responseCode.name(), responseCode, false);
    }

    public InvalidTransactionException(final ResponseCodeEnum responseCode, boolean reverting) {
        this(responseCode.name(), responseCode, reverting);
    }

    public InvalidTransactionException(
            final String detailMessage, final ResponseCodeEnum responseCode) {
        this(detailMessage, responseCode, false);
    }

    public InvalidTransactionException(
            final String detailMessage, final ResponseCodeEnum responseCode, boolean reverting) {
        super(detailMessage);
        this.responseCode = responseCode;
        this.reverting = reverting;
    }

    public ResponseCodeEnum getResponseCode() {
        return responseCode;
    }

    public boolean isReverting() {
        return reverting;
    }

    public Bytes getRevertReason() {
        if (!isReverting()) {
            throw new IllegalStateException();
        }
        return messageBytes();
    }

    public Bytes messageBytes() {
        final var detail = getMessage();
        return Bytes.of(detail.getBytes());
    }
}
