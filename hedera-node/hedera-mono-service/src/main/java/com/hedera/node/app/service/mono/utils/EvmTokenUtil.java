/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.utils;

import static com.hedera.node.app.service.mono.context.primitives.StateView.tokenFreeStatusFor;
import static com.hedera.node.app.service.mono.context.primitives.StateView.tokenKycStatusFor;
import static com.hedera.node.app.service.mono.context.primitives.StateView.tokenPauseStatusOf;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmKey;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FixedFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FractionalFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.RoyaltyFee;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import java.util.ArrayList;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;

public class EvmTokenUtil {

    public static EvmTokenInfo asEvmTokenInfo(MerkleToken token, final ByteString ledgerId) {
        final var info =
                new EvmTokenInfo(
                        ledgerId.toByteArray(),
                        token.supplyType().ordinal(),
                        token.isDeleted(),
                        token.symbol(),
                        token.name(),
                        token.memo(),
                        EntityIdUtils.asTypedEvmAddress(token.treasury()),
                        token.totalSupply(),
                        token.maxSupply(),
                        token.decimals(),
                        token.expiry());

        final var adminCandidate = token.adminKey();
        adminCandidate.ifPresentOrElse(
                k -> {
                    final var key = asKeyUnchecked(k);
                    info.setAdminKey(convertToEvmKey(key));
                },
                () -> info.setAdminKey(new EvmKey()));

        final var freezeCandidate = token.freezeKey();
        freezeCandidate.ifPresentOrElse(
                k -> {
                    info.setDefaultFreezeStatus(
                            tokenFreeStatusFor(token.accountsAreFrozenByDefault()).getNumber()
                                    == 1);
                    final var key = asKeyUnchecked(k);
                    info.setFreezeKey(convertToEvmKey(key));
                },
                () -> {
                    info.setFreezeKey(new EvmKey());
                    info.setDefaultFreezeStatus(
                            TokenFreezeStatus.FreezeNotApplicable.getNumber() == 1);
                });

        final var kycCandidate = token.kycKey();
        kycCandidate.ifPresentOrElse(
                k -> {
                    info.setDefaultKycStatus(
                            tokenKycStatusFor(token.accountsKycGrantedByDefault()).getNumber()
                                    == 1);
                    final var key = asKeyUnchecked(k);
                    info.setKycKey(convertToEvmKey(key));
                },
                () -> {
                    info.setKycKey(new EvmKey());
                    info.setDefaultKycStatus(TokenKycStatus.KycNotApplicable.getNumber() == 1);
                });

        final var supplyCandidate = token.supplyKey();
        supplyCandidate.ifPresentOrElse(
                k -> {
                    final var key = asKeyUnchecked(k);
                    info.setSupplyKey(convertToEvmKey(key));
                },
                () -> info.setSupplyKey(new EvmKey()));

        final var wipeCandidate = token.wipeKey();
        wipeCandidate.ifPresentOrElse(
                k -> {
                    final var key = asKeyUnchecked(k);
                    info.setWipeKey(convertToEvmKey(key));
                },
                () -> info.setWipeKey(new EvmKey()));

        final var feeScheduleCandidate = token.feeScheduleKey();
        feeScheduleCandidate.ifPresentOrElse(
                k -> {
                    final var key = asKeyUnchecked(k);
                    info.setFeeScheduleKey(convertToEvmKey(key));
                },
                () -> info.setFeeScheduleKey(new EvmKey()));

        final var pauseCandidate = token.pauseKey();
        pauseCandidate.ifPresentOrElse(
                k -> {
                    final var key = asKeyUnchecked(k);
                    info.setPauseKey(convertToEvmKey(key));
                    info.setIsPaused(tokenPauseStatusOf(token.isPaused()).getNumber() == 1);
                },
                () -> {
                    info.setPauseKey(new EvmKey());
                    info.setIsPaused(TokenPauseStatus.PauseNotApplicable.getNumber() == 1);
                });

        if (token.hasAutoRenewAccount()) {
            info.setAutoRenewAccount(EntityIdUtils.asTypedEvmAddress(token.autoRenewAccount()));
            info.setAutoRenewPeriod(token.autoRenewPeriod());
        }

        info.setCustomFees(evmCustomFees(token.grpcFeeSchedule()));

        return info;
    }

    public static List<com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee>
            evmCustomFees(List<CustomFee> customFees) {
        List<com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee>
                evmCustomFees = new ArrayList<>();
        for (final var customFee : customFees) {
            extractFees(customFee, evmCustomFees);
        }

        return evmCustomFees;
    }

    public static void extractFees(
            CustomFee customFee,
            List<com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee>
                    evmCustomFees) {
        final var feeCollector =
                EntityIdUtils.asTypedEvmAddress(customFee.getFeeCollectorAccountId());
        var evmCustomFee =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();

        if (customFee.getFixedFee().getAmount() > 0) {
            var fixedFee = getFixedFee(customFee.getFixedFee(), feeCollector);

            evmCustomFee.setFixedFee(fixedFee);
            evmCustomFees.add(evmCustomFee);
        } else if (customFee.getFractionalFee().getMinimumAmount() > 0) {
            var fractionalFee = getFractionalFee(customFee.getFractionalFee(), feeCollector);

            evmCustomFee.setFractionalFee(fractionalFee);
            evmCustomFees.add(evmCustomFee);
        } else if (customFee.getRoyaltyFee().getExchangeValueFraction().getNumerator() > 0) {
            var royaltyFee = getRoyaltyFee(customFee.getRoyaltyFee(), feeCollector);

            evmCustomFee.setRoyaltyFee(royaltyFee);
            evmCustomFees.add(evmCustomFee);
        }
    }

    public static RoyaltyFee getRoyaltyFee(
            com.hederahashgraph.api.proto.java.RoyaltyFee royaltyFee, Address feeCollector) {
        return new RoyaltyFee(
                royaltyFee.getExchangeValueFraction().getNumerator(),
                royaltyFee.getExchangeValueFraction().getDenominator(),
                royaltyFee.getFallbackFee().getAmount(),
                EntityIdUtils.asTypedEvmAddress(
                        royaltyFee.getFallbackFee().getDenominatingTokenId()),
                royaltyFee.getFallbackFee().getDenominatingTokenId().getTokenNum() == 0,
                feeCollector);
    }

    public static FractionalFee getFractionalFee(
            com.hederahashgraph.api.proto.java.FractionalFee fractionalFee, Address feeCollector) {
        return new FractionalFee(
                fractionalFee.getFractionalAmount().getNumerator(),
                fractionalFee.getFractionalAmount().getDenominator(),
                fractionalFee.getMinimumAmount(),
                fractionalFee.getMaximumAmount(),
                fractionalFee.getNetOfTransfers(),
                feeCollector);
    }

    public static FixedFee getFixedFee(
            com.hederahashgraph.api.proto.java.FixedFee fixedFee, Address feeCollector) {
        return new FixedFee(
                fixedFee.getAmount(),
                EntityIdUtils.asTypedEvmAddress(fixedFee.getDenominatingTokenId()),
                fixedFee.getDenominatingTokenId().getTokenNum() == 0,
                false,
                feeCollector);
    }

    public static EvmKey convertToEvmKey(Key key) {
        final var contractId =
                key.getContractID().getContractNum() > 0
                        ? EntityIdUtils.asTypedEvmAddress(key.getContractID())
                        : EntityIdUtils.asTypedEvmAddress(
                                ContractID.newBuilder()
                                        .setShardNum(0L)
                                        .setRealmNum(0L)
                                        .setContractNum(0L)
                                        .build());
        final var ed25519 = key.getEd25519().toByteArray();
        final var ecdsaSecp256K1 = key.getECDSASecp256K1().toByteArray();
        final var delegatableContractId =
                key.getDelegatableContractId().getContractNum() > 0
                        ? EntityIdUtils.asTypedEvmAddress(key.getDelegatableContractId())
                        : EntityIdUtils.asTypedEvmAddress(
                                ContractID.newBuilder()
                                        .setShardNum(0L)
                                        .setRealmNum(0L)
                                        .setContractNum(0L)
                                        .build());

        return new EvmKey(contractId, ed25519, ecdsaSecp256K1, delegatableContractId);
    }
}
