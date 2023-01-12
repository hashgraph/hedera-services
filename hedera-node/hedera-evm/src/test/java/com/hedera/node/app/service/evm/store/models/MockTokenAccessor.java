package com.hedera.node.app.service.evm.store.models;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmKey;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenKeyType;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;

public class MockTokenAccessor implements TokenAccessor {

  @Override
  public Optional<EvmTokenInfo> evmInfoForToken(Address token, ByteString ledgerId) {
    return Optional.empty();
  }

  @Override
  public Optional<EvmNftInfo> evmNftInfo(Address nft, long serialNo,
      ByteString ledgerId) {
    return Optional.empty();
  }

  @Override
  public boolean isTokenAddress(Address address) {
    return false;
  }

  @Override
  public boolean isFrozen(Address account, Address token) {
    return false;
  }

  @Override
  public boolean defaultFreezeStatus(Address token) {
    return false;
  }

  @Override
  public boolean defaultKycStatus(Address token) {
    return false;
  }

  @Override
  public boolean isKyc(Address account, Address token) {
    return false;
  }

  @Override
  public Optional<List<CustomFee>> infoForTokenCustomFees(Address token) {
    return Optional.empty();
  }

  @Override
  public TokenType typeOf(Address token) {
    return null;
  }

  @Override
  public EvmKey keyOf(Address tokenId, TokenKeyType keyType) {
    return null;
  }

  @Override
  public String nameOf(Address token) {
    return null;
  }

  @Override
  public String symbolOf(Address token) {
    return null;
  }

  @Override
  public long totalSupplyOf(Address token) {
    return 0;
  }

  @Override
  public int decimalsOf(Address token) {
    return 0;
  }

  @Override
  public long balanceOf(Address account, Address token) {
    return 0;
  }

  @Override
  public long staticAllowanceOf(Address owner, Address spender, Address token) {
    return 0;
  }

  @Override
  public Address staticApprovedSpenderOf(Address nft, long serialNo) {
    return null;
  }

  @Override
  public boolean staticIsOperator(Address owner, Address operator,
      Address token) {
    return false;
  }

  @Override
  public Address ownerOf(Address nft, long serialNo) {
    return null;
  }

  @Override
  public Address canonicalAddress(Address addressOrAlias) {
    return null;
  }

  @Override
  public String metadataOf(Address nft, long serialNo) {
    return null;
  }
}
