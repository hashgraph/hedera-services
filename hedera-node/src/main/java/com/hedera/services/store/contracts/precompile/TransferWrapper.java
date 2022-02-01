package com.hedera.services.store.contracts.precompile;

import com.hederahashgraph.api.proto.java.AccountID;

public record TransferWrapper(AccountID from, AccountID to, long amount, long tokenId, String data) {
}
