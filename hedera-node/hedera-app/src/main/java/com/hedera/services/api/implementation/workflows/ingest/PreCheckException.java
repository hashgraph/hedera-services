package com.hedera.services.api.implementation.workflows.ingest;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

/**
 * Thrown if the request itself is bad. The protobuf decoded correctly, but it failed one or more of
 * the ingestion pipeline pre-checks.
 */
public class PreCheckException extends Exception {
    private final ResponseCodeEnum responseCode;

    public PreCheckException(ResponseCodeEnum responseCode, String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public ResponseCodeEnum responseCode() {
        return responseCode;
    }
}
