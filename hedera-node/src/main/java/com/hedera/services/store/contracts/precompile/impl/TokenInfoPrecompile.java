package com.hedera.services.store.contracts.precompile.impl;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.services.store.contracts.precompile.KeyTypeConstants.ADMIN_KEY;
import static com.hedera.services.store.contracts.precompile.KeyTypeConstants.FEE_SCHEDULE_KEY;
import static com.hedera.services.store.contracts.precompile.KeyTypeConstants.FREEZE_KEY;
import static com.hedera.services.store.contracts.precompile.KeyTypeConstants.KYC_KEY;
import static com.hedera.services.store.contracts.precompile.KeyTypeConstants.PAUSE_KEY;
import static com.hedera.services.store.contracts.precompile.KeyTypeConstants.SUPPLY_KEY;
import static com.hedera.services.store.contracts.precompile.KeyTypeConstants.WIPE_KEY;

import com.hedera.services.config.NetworkInfo;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.Expiry;
import com.hedera.services.store.contracts.precompile.codec.HederaToken;
import com.hedera.services.store.contracts.precompile.codec.KeyValue;
import com.hedera.services.store.contracts.precompile.codec.TokenInfo;
import com.hedera.services.store.contracts.precompile.codec.TokenKey;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class TokenInfoPrecompile extends AbstractReadOnlyPrecompile {

  private final NetworkInfo networkInfo;

  public TokenInfoPrecompile(
      final TokenID tokenId,
      final SyntheticTxnFactory syntheticTxnFactory,
      final WorldLedgers ledgers,
      final EncodingFacade encoder,
      final DecodingFacade decoder,
      final PrecompilePricingUtils pricingUtils,
      final NetworkInfo networkInfo) {
    super(tokenId, syntheticTxnFactory, ledgers, encoder, decoder, pricingUtils);
    this.networkInfo = networkInfo;
  }

  @Override
  public Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
    final var tokenInfoWrapper = decoder.decodeGetTokenInfo(input);
    tokenId = tokenInfoWrapper.tokenID();
    return super.body(input, aliasResolver);
  }

  @Override
  public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
    final var token = ledgers.tokens().getImmutableRef(tokenId);

    final var name = token.name();
    final var symbol = token.symbol();
    final var treasury = EntityIdUtils.asTypedEvmAddress(token.treasury());
    final var memo = token.memo();
    final var tokenSupplyType = token.supplyType() == TokenSupplyType.FINITE;
    final var maxSupply = token.maxSupply();
    final var freezeDefault = token.hasFreezeKey();
    final var tokenKeys = getTokenKeys(token);
    final var expiry =
        new Expiry(
            token.expiry(),
            EntityIdUtils.asTypedEvmAddress(token.autoRenewAccount()),
            token.autoRenewPeriod());
    final var hederaToken = new HederaToken(name, symbol, treasury, memo, tokenSupplyType, maxSupply, freezeDefault, tokenKeys, expiry);

    final var totalSupply = token.totalSupply();
    final var deleted = token.isDeleted();
    final var defaultKycStatus = token.accountsKycGrantedByDefault();
    final var pauseStatus = token.isPaused();
    final var ledgerId = networkInfo.ledgerId().toString();
    final var tokenInfo = new TokenInfo(hederaToken, totalSupply, deleted, defaultKycStatus, pauseStatus, ledgerId);
    return encoder.encodeGetTokenInfo(tokenInfo);
  }

  private List<TokenKey> getTokenKeys(final MerkleToken token) {
    final List<TokenKey> tokenKeys = new ArrayList<>(7);

    if (token.getAdminKey() != null) {
      tokenKeys.add(getTokenKey(token.getAdminKey(), ADMIN_KEY));
    }
    if (token.getKycKey() != null) {
      tokenKeys.add(getTokenKey(token.getKycKey(), KYC_KEY));
    }
    if (token.getFreezeKey() != null) {
      tokenKeys.add(getTokenKey(token.getFreezeKey(), FREEZE_KEY));
    }
    if (token.getWipeKey() != null) {
      tokenKeys.add(getTokenKey(token.getWipeKey(), WIPE_KEY));
    }
    if (token.getSupplyKey() != null) {
      tokenKeys.add(getTokenKey(token.getSupplyKey(), SUPPLY_KEY));
    }
    if (token.getFeeScheduleKey() != null) {
      tokenKeys.add(getTokenKey(token.getFeeScheduleKey(), FEE_SCHEDULE_KEY));
    }
    if (token.getPauseKey() != null) {
      tokenKeys.add(getTokenKey(token.getPauseKey(), PAUSE_KEY));
    }

    return tokenKeys;
  }

  private TokenKey getTokenKey(final JKey key, final int keyType) {
    final var keyValue = getKeyValue(key);
    return new TokenKey(keyType, keyValue);
  }

  private KeyValue getKeyValue(final JKey key) {
    return new KeyValue(
        false,
        EntityIdUtils.asTypedEvmAddress(key.getContractIDKey().getContractID()),
        key.getEd25519(),
        key.getECDSASecp256k1Key(),
        EntityIdUtils.asTypedEvmAddress(key.getDelegatableContractIdKey().getContractID()));
  }
}
