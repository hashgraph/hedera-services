package com.hedera.services.exceptions;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.tuweni.bytes.Bytes;

/**
 * Captures a failure in transaction processing to be captured by
 * the {@link com.hedera.services.txns.TransitionRunner} and used
 * to set the final resolved status of the transaction.
 *
 * Unless the contained {@link ResponseCodeEnum} is exactly {@code FAIL_INVALID},
 * this represents some form of user error. The {@code FAIL_INVALID} code indicates
 * an internal system error; and it is usually desirable in that case to include a
 * detail message in the constructor.
 */
public class InvalidTransactionException extends RuntimeException {
    private static final String REVERT_REASON_TAG_START = "{{";
    private static final String REVERT_REASON_TAG_END = "}}";

    private final ResponseCodeEnum responseCode;

    public InvalidTransactionException(final ResponseCodeEnum responseCode) {
        super(responseCode.name());
        this.responseCode = responseCode;
    }

    public InvalidTransactionException(final String detailMessage, final ResponseCodeEnum responseCode) {
        super(detailMessage);
        this.responseCode = responseCode;
    }

    public static InvalidTransactionException fromReverting(final ResponseCodeEnum code) {
        return new InvalidTransactionException(revertingDetail(code.name()), code);
    }

    public static InvalidTransactionException fromReverting(final ResponseCodeEnum code, final String reason) {
        return new InvalidTransactionException(revertingDetail(reason), code);
    }

    public ResponseCodeEnum getResponseCode() {
        return responseCode;
    }

    public boolean isReverting() {
        return getMessage().startsWith(REVERT_REASON_TAG_START);
    }

    public Bytes getRevertReason() {
        if (!isReverting()) {
            throw new IllegalStateException();
        }
        final var detail = getMessage();
        return Bytes.of(
                detail.substring(REVERT_REASON_TAG_START.length(), detail.indexOf(REVERT_REASON_TAG_END)).getBytes());
    }

    private static String revertingDetail(final String revertReason) {
        return REVERT_REASON_TAG_START + revertReason + REVERT_REASON_TAG_END;
    }
}
