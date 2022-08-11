package com.hedera.services.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.TokenID;

public record GetTokenKeyWrapper(TokenID tokenID, long keyType) {
}
