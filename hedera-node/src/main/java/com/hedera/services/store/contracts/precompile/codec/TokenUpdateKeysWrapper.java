package com.hedera.services.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.TokenID;

import java.util.List;

public record TokenUpdateKeysWrapper(TokenID tokenID, List<TokenKeyWrapper> tokenKeys) {
}
