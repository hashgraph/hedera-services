package com.hedera.services.txns.validation;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import static com.hedera.services.txns.validation.ExpiryMeta.INVALID_EXPIRY_META;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

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
