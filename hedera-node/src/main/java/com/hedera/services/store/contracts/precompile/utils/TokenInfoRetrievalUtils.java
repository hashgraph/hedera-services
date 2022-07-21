/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.store.contracts.precompile.utils;

import static com.hedera.services.store.contracts.precompile.TokenKeyType.ADMIN_KEY;
import static com.hedera.services.store.contracts.precompile.TokenKeyType.FEE_SCHEDULE_KEY;
import static com.hedera.services.store.contracts.precompile.TokenKeyType.FREEZE_KEY;
import static com.hedera.services.store.contracts.precompile.TokenKeyType.KYC_KEY;
import static com.hedera.services.store.contracts.precompile.TokenKeyType.PAUSE_KEY;
import static com.hedera.services.store.contracts.precompile.TokenKeyType.SUPPLY_KEY;
import static com.hedera.services.store.contracts.precompile.TokenKeyType.WIPE_KEY;

import com.hedera.services.config.NetworkInfo;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.TokenKeyType;
import com.hedera.services.store.contracts.precompile.codec.Expiry;
import com.hedera.services.store.contracts.precompile.codec.FixedFee;
import com.hedera.services.store.contracts.precompile.codec.FractionalFee;
import com.hedera.services.store.contracts.precompile.codec.FungibleTokenInfo;
import com.hedera.services.store.contracts.precompile.codec.HederaToken;
import com.hedera.services.store.contracts.precompile.codec.KeyValue;
import com.hedera.services.store.contracts.precompile.codec.NonFungibleTokenInfo;
import com.hedera.services.store.contracts.precompile.codec.RoyaltyFee;
import com.hedera.services.store.contracts.precompile.codec.TokenInfo;
import com.hedera.services.store.contracts.precompile.codec.TokenKey;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class TokenInfoRetrievalUtils {

  private TokenInfoRetrievalUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static NonFungibleTokenInfo getNonFungibleTokenInfo(final TokenID tokenId, final long serialNumber, final WorldLedgers ledgers, final NetworkInfo networkInfo) {
    final var tokenInfo = getTokenInfo(tokenId, ledgers, networkInfo);
    final var nftId = NftId.fromGrpc(tokenId, serialNumber);

    final var ownerId = ledgers.ownerOf(nftId);
    final var creationTime = ledgers.packedCreationTimeOf(nftId);
    final var metadata = ledgers.metadataOf(nftId);
    final var spenderId = EntityIdUtils.asTypedEvmAddress(ledgers.spenderOf(nftId));
    return new NonFungibleTokenInfo(
        tokenInfo, serialNumber, ownerId, creationTime, metadata, spenderId);
  }

  public static FungibleTokenInfo getFungibleTokenInfo(final TokenID tokenId, final WorldLedgers ledgers, final NetworkInfo networkInfo) {
    final var tokenInfo = getTokenInfo(tokenId, ledgers, networkInfo);
    final var decimals = ledgers.decimalsOf(tokenId);
    return new FungibleTokenInfo(tokenInfo, decimals);
  }

  public static TokenInfo getTokenInfo(final TokenID tokenId, final WorldLedgers ledgers, final
      NetworkInfo networkInfo) {
    final var name = ledgers.nameOf(tokenId);
    final var symbol = ledgers.symbolOf(tokenId);
    final var treasury = EntityIdUtils.asTypedEvmAddress(ledgers.treasury(tokenId));
    final var memo = ledgers.memo(tokenId);
    final var tokenSupplyType = ledgers.supplyType(tokenId) == TokenSupplyType.FINITE;
    final var maxSupply = ledgers.maxSupply(tokenId);
    final var freezeDefault = ledgers.accountsFrozenByDefault(tokenId);
    final var tokenKeys = getTokenKeys(tokenId, ledgers);
    final var expiry =
        new Expiry(
            ledgers.expiry(tokenId),
            EntityIdUtils.asTypedEvmAddress(ledgers.autoRenewAccount(tokenId)),
            ledgers.autoRenewPeriod(tokenId));
    final var hederaToken =
        new HederaToken(
            name,
            symbol,
            treasury,
            memo,
            tokenSupplyType,
            maxSupply,
            freezeDefault,
            tokenKeys,
            expiry);

    final var totalSupply = ledgers.totalSupplyOf(tokenId);
    final var deleted = ledgers.isDeleted(tokenId);
    final var defaultKycStatus = ledgers.accountsKycGrantedByDefault(tokenId);
    final var pauseStatus = ledgers.isPaused(tokenId);
    final var ledgerId = Bytes.wrap(networkInfo.ledgerId().toByteArray()).toString();

    final var fixedFees = new ArrayList<FixedFee>();
    final var fractionalFees = new ArrayList<FractionalFee>();
    final var royaltyFees = new ArrayList<RoyaltyFee>();

    for (final var fee : ledgers.feeSchedule(tokenId)) {
      final var feeCollector = EntityIdUtils.asTypedEvmAddress(fee.getFeeCollector());
      switch (fee.getFeeType()) {
        case FIXED_FEE -> fixedFees.add(getFixedFee(fee, feeCollector));
        case FRACTIONAL_FEE -> fractionalFees.add(getFractionalFee(fee, feeCollector));
        case ROYALTY_FEE -> royaltyFees.add(getRoyaltyFee(fee, feeCollector));
      }
    }

    return new TokenInfo(
        hederaToken,
        totalSupply,
        deleted,
        defaultKycStatus,
        pauseStatus,
        fixedFees,
        fractionalFees,
        royaltyFees,
        ledgerId);
  }

  private static FixedFee getFixedFee(final FcCustomFee fee, final Address feeCollector) {
    final var fixedFeeSpec = fee.getFixedFeeSpec();
    return new FixedFee(
        fixedFeeSpec.getUnitsToCollect(),
        fixedFeeSpec.getTokenDenomination() != null
            ? EntityIdUtils.asTypedEvmAddress(fixedFeeSpec.getTokenDenomination())
            : Address.wrap(Bytes.wrap(new byte[20])),
        fixedFeeSpec.getTokenDenomination() == null,
        false,
        feeCollector);
  }

  private static FractionalFee getFractionalFee(final FcCustomFee fee, final Address feeCollector) {
    final var fractionalFeeSpec = fee.getFractionalFeeSpec();
    return new FractionalFee(
        fractionalFeeSpec.getNumerator(),
        fractionalFeeSpec.getDenominator(),
        fractionalFeeSpec.getMinimumAmount(),
        fractionalFeeSpec.getMaximumUnitsToCollect(),
        fractionalFeeSpec.isNetOfTransfers(),
        feeCollector);
  }

  private static RoyaltyFee getRoyaltyFee(final FcCustomFee fee, final Address feeCollector) {
    final var royaltyFeeSpec = fee.getRoyaltyFeeSpec();
    RoyaltyFee royaltyFee;
    if (royaltyFeeSpec.hasFallbackFee()) {
      final var fallbackFee = royaltyFeeSpec.fallbackFee();
      royaltyFee =
          new RoyaltyFee(
              royaltyFeeSpec.numerator(),
              royaltyFeeSpec.denominator(),
              fallbackFee.getUnitsToCollect(),
              EntityIdUtils.asTypedEvmAddress(fallbackFee.getTokenDenomination()),
              fallbackFee.getTokenDenomination() == null,
              feeCollector);
    } else {
      royaltyFee =
          new RoyaltyFee(
              royaltyFeeSpec.numerator(),
              royaltyFeeSpec.denominator(),
              0L,
              null,
              false,
              feeCollector);
    }
    return royaltyFee;
  }

  private static List<TokenKey> getTokenKeys(final TokenID tokenId, final WorldLedgers ledgers) {
    final List<TokenKey> tokenKeys = new ArrayList<>(TokenKeyType.values().length);

    if (ledgers.adminKey(tokenId).isPresent()) {
      tokenKeys.add(getTokenKey(ledgers.adminKey(tokenId).orElse(null), ADMIN_KEY.value()));
    }
    if (ledgers.kycKey(tokenId).isPresent()) {
      tokenKeys.add(getTokenKey(ledgers.kycKey(tokenId).orElse(null), KYC_KEY.value()));
    }
    if (ledgers.freezeKey(tokenId).isPresent()) {
      tokenKeys.add(getTokenKey(ledgers.freezeKey(tokenId).orElse(null), FREEZE_KEY.value()));
    }
    if (ledgers.wipeKey(tokenId).isPresent()) {
      tokenKeys.add(getTokenKey(ledgers.wipeKey(tokenId).orElse(null), WIPE_KEY.value()));
    }
    if (ledgers.supplyKey(tokenId).isPresent()) {
      tokenKeys.add(getTokenKey(ledgers.supplyKey(tokenId).orElse(null), SUPPLY_KEY.value()));
    }
    if (ledgers.feeScheduleKey(tokenId).isPresent()) {
      tokenKeys.add(getTokenKey(ledgers.feeScheduleKey(tokenId).orElse(null), FEE_SCHEDULE_KEY.value()));
    }
    if (ledgers.pauseKey(tokenId).isPresent()) {
      tokenKeys.add(getTokenKey(ledgers.pauseKey(tokenId).get(), PAUSE_KEY.value()));
    }

    return tokenKeys;
  }

  private static TokenKey getTokenKey(final JKey key, final int keyType) {
    final var keyValue = getKeyValue(key);
    return new TokenKey(keyType, keyValue);
  }

  private static KeyValue getKeyValue(final JKey key) {
    return new KeyValue(
        false,
        key.getContractIDKey() != null
            ? EntityIdUtils.asTypedEvmAddress(key.getContractIDKey().getContractID())
            : null,
        key.getEd25519(),
        key.getECDSASecp256k1Key(),
        key.getDelegatableContractIdKey() != null
            ? EntityIdUtils.asTypedEvmAddress(
            key.getDelegatableContractIdKey().getContractID())
            : null);
  }

}
