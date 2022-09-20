package com.hedera.evm.usage;

public record BaseTransactionMeta(int memoUtf8Bytes, int numExplicitTransfers) {}