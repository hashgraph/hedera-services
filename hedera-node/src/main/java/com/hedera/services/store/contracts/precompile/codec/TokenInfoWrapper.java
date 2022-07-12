package com.hedera.services.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.TokenID;

public record TokenInfoWrapper(TokenID tokenID, long serialNumber) {
  private static final long INVALID_SERIAL_NUMBER = -1;

  public static TokenInfoWrapper forNonFungibleToken(final TokenID tokenId, final long serialNumber) {
    return new TokenInfoWrapper(tokenId, serialNumber);
  }

  public static TokenInfoWrapper forToken(final TokenID tokenId) {
    return new TokenInfoWrapper(tokenId, INVALID_SERIAL_NUMBER);
  }
}
