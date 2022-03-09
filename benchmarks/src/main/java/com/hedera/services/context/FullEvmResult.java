package com.hedera.services.context;

import com.hedera.services.contracts.execution.TransactionProcessingResult;

import javax.annotation.Nullable;

public record FullEvmResult(TransactionProcessingResult result, @Nullable byte[] evmAddress) {
}
