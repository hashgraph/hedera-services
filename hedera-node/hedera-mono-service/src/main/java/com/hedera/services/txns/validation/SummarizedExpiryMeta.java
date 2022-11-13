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
package com.hedera.services.txns.validation;

import static com.hedera.services.txns.validation.ExpiryMeta.INVALID_EXPIRY_META;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public record SummarizedExpiryMeta(ResponseCodeEnum status, ExpiryMeta meta) {
    public static final SummarizedExpiryMeta INVALID_PERIOD_SUMMARY =
            new SummarizedExpiryMeta(AUTORENEW_DURATION_NOT_IN_RANGE, INVALID_EXPIRY_META);
    public static final SummarizedExpiryMeta INVALID_EXPIRY_SUMMARY =
            new SummarizedExpiryMeta(INVALID_EXPIRATION_TIME, INVALID_EXPIRY_META);
    public static final SummarizedExpiryMeta EXPIRY_REDUCTION_SUMMARY =
            new SummarizedExpiryMeta(EXPIRATION_REDUCTION_NOT_ALLOWED, INVALID_EXPIRY_META);

    public static SummarizedExpiryMeta forValid(ExpiryMeta expiryMeta) {
        return new SummarizedExpiryMeta(OK, expiryMeta);
    }

    public boolean isValid() {
        return status == OK;
    }

    public ExpiryMeta knownValidMeta() {
        if (!isValid()) {
            throw new IllegalStateException("Meta not known valid");
        }
        return meta;
    }
}
