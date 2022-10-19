/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.ledger.BalanceChange.changingNftOwnership;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.account;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createFungibleTokenUpdateWrapperWithKeys;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createNonFungibleTokenCreateWrapperWithKeys;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createNonFungibleTokenUpdateWrapperWithKeys;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createTokenCreateWrapperWithKeys;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fixedFee;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fractionalFee;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.payer;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.receiver;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.royaltyFee;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.token;
import static com.hedera.services.store.contracts.precompile.SyntheticTxnFactory.MOCK_INITCODE;
import static com.hedera.services.store.contracts.precompile.SyntheticTxnFactory.WEIBARS_TO_TINYBARS;
import static com.hedera.services.txns.crypto.AutoCreationLogic.AUTO_MEMO;
import static com.hedera.services.txns.crypto.AutoCreationLogic.LAZY_MEMO;
import static com.hedera.services.txns.crypto.AutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static com.hedera.test.utils.IdUtils.asAliasAccount;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.ledger.accounts.ContractCustomizer;
import com.hedera.services.state.expiry.removal.CryptoGcOutcome;
import com.hedera.services.state.expiry.removal.FungibleTreasuryReturns;
import com.hedera.services.state.expiry.removal.NonFungibleTreasuryReturns;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.NftAdjustments;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.DeleteWrapper;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper;
import com.hedera.services.store.contracts.precompile.codec.MintWrapper;
import com.hedera.services.store.contracts.precompile.codec.PauseWrapper;
import com.hedera.services.store.contracts.precompile.codec.SetApprovalForAllWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenFreezeUnfreezeWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenTransferWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateExpiryInfoWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateKeysWrapper;
import com.hedera.services.store.contracts.precompile.codec.UnpauseWrapper;
import com.hedera.services.store.contracts.precompile.codec.WipeWrapper;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyntheticTxnFactoryTest {
    @Mock private EthTxData ethTxData;
    @Mock private ContractCustomizer customizer;
    private GlobalDynamicProperties dynamicProperties;
    private BootstrapProperties propertySource;

    private SyntheticTxnFactory subject;

    @BeforeEach
    void setUp() {
        propertySource = new BootstrapProperties();
        propertySource.ensureProps();
        dynamicProperties =
                new GlobalDynamicProperties(new HederaNumbers(propertySource), propertySource);

        subject = new SyntheticTxnFactory(dynamicProperties);
    }

    @Test
    void synthesizesExpectedTreasuryReturns() {
        final var ftId = EntityId.fromIdentityCode(666);
        final var nftId = EntityId.fromIdentityCode(777);
        final var nftAdjusts =
                new NftAdjustments(
                        new long[] {1},
                        List.of(EntityId.fromIdentityCode(2)),
                        List.of(EntityId.fromIdentityCode(98)));
        final var fungibleAdjusts =
                new CurrencyAdjustments(new long[] {-123, 123}, new long[] {2, 98});
        final var fungibleReturns =
                new FungibleTreasuryReturns(List.of(ftId), List.of(fungibleAdjusts), true);
        final var nonFungibleReturns =
                new NonFungibleTreasuryReturns(List.of(nftId), List.of(nftAdjusts), true);
        final var returns = new CryptoGcOutcome(fungibleReturns, nonFungibleReturns, false);

        final var expected =
                CryptoTransferTransactionBody.newBuilder()
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(ftId.asId().asGrpcToken())
                                        .addTransfers(aaWith(2, -123))
                                        .addTransfers(aaWith(98, +123))
                                        .build())
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(nftId.asId().asGrpcToken())
                                        .addNftTransfers(nftFromTo(1, 2, 98))
                                        .build())
                        .build();

        final var txn = subject.synthTokenTransfer(returns).build();
        final var op = txn.getCryptoTransfer();

        assertEquals(op, expected);
    }

    @Test
    void synthesizesExpectedBurn() {
        final var ftId = EntityId.fromIdentityCode(666);
        final var nftId = EntityId.fromIdentityCode(777);
        final var nftAdjusts =
                new NftAdjustments(
                        new long[] {1},
                        List.of(EntityId.fromIdentityCode(2)),
                        List.of(EntityId.fromIdentityCode(98)));
        final var fungibleAdjusts = new CurrencyAdjustments(new long[] {-123}, new long[] {2});
        final var fungibleReturns =
                new FungibleTreasuryReturns(List.of(ftId), List.of(fungibleAdjusts), true);
        final var nonFungibleReturns =
                new NonFungibleTreasuryReturns(List.of(nftId), List.of(nftAdjusts), true);
        final var returns = new CryptoGcOutcome(fungibleReturns, nonFungibleReturns, false);

        final var expected =
                CryptoTransferTransactionBody.newBuilder()
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(ftId.asId().asGrpcToken())
                                        .addTransfers(aaWith(2, -123))
                                        .build())
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(nftId.asId().asGrpcToken())
                                        .addNftTransfers(nftFromTo(1, 2, 98))
                                        .build())
                        .build();

        final var txn = subject.synthTokenTransfer(returns).build();
        final var op = txn.getCryptoTransfer();

        assertEquals(op, expected);
    }

    @Test
    void synthesizesExpectedCryptoTransfer() {
        final var adjustments = new CurrencyAdjustments(new long[] {-123, 123}, new long[] {2, 98});
        final var expected =
                CryptoTransferTransactionBody.newBuilder()
                        .setTransfers(
                                TransferList.newBuilder()
                                        .addAccountAmounts(aaWith(2, -123))
                                        .addAccountAmounts(aaWith(98, +123))
                                        .build())
                        .build();

        final var txn = subject.synthHbarTransfer(adjustments).build();
        final var op = txn.getCryptoTransfer();

        assertEquals(op, expected);
    }

    @Test
    void synthesizesPrecheckCallFromEthDataWithFnParams() {
        given(ethTxData.hasToAddress()).willReturn(true);
        given(ethTxData.hasCallData()).willReturn(true);
        given(ethTxData.callData()).willReturn(callData);
        given(ethTxData.gasLimit()).willReturn(gasLimit);
        given(ethTxData.value()).willReturn(value);
        given(ethTxData.to()).willReturn(addressTo);
        final var expectedId =
                ContractID.newBuilder().setEvmAddress(ByteString.copyFrom(ethTxData.to())).build();

        final var synthBody = subject.synthPrecheckContractOpFromEth(ethTxData);

        assertTrue(synthBody.hasContractCall());
        final var op = synthBody.getContractCall();
        assertArrayEquals(callData, op.getFunctionParameters().toByteArray());
        assertEquals(gasLimit, op.getGas());
        assertEquals(valueInTinyBars, op.getAmount());
        assertEquals(expectedId, op.getContractID());
    }

    @Test
    void synthesizesCallFromEthDataWithFnParams() {
        given(ethTxData.hasToAddress()).willReturn(true);
        given(ethTxData.hasCallData()).willReturn(true);
        given(ethTxData.callData()).willReturn(callData);
        given(ethTxData.gasLimit()).willReturn(gasLimit);
        given(ethTxData.value()).willReturn(value);
        given(ethTxData.to()).willReturn(addressTo);
        final var expectedId =
                ContractID.newBuilder().setEvmAddress(ByteString.copyFrom(ethTxData.to())).build();

        final var optSynthBody = subject.synthContractOpFromEth(ethTxData);
        assertTrue(optSynthBody.isPresent());
        final var synthBody = optSynthBody.get().build();

        assertTrue(synthBody.hasContractCall());
        final var op = synthBody.getContractCall();
        assertArrayEquals(callData, op.getFunctionParameters().toByteArray());
        assertEquals(gasLimit, op.getGas());
        assertEquals(valueInTinyBars, op.getAmount());
        assertEquals(expectedId, op.getContractID());
    }

    @Test
    void synthesizesCallFromEthDataWithNoFnParams() {
        given(ethTxData.hasToAddress()).willReturn(true);
        given(ethTxData.gasLimit()).willReturn(gasLimit);
        given(ethTxData.value()).willReturn(value);
        given(ethTxData.to()).willReturn(addressTo);
        final var expectedId =
                ContractID.newBuilder().setEvmAddress(ByteString.copyFrom(ethTxData.to())).build();

        final var optSynthBody = subject.synthContractOpFromEth(ethTxData);
        assertTrue(optSynthBody.isPresent());
        final var synthBody = optSynthBody.get().build();

        assertTrue(synthBody.hasContractCall());
        final var op = synthBody.getContractCall();
        assertTrue(op.getFunctionParameters().isEmpty());
        assertEquals(gasLimit, op.getGas());
        assertEquals(valueInTinyBars, op.getAmount());
        assertEquals(expectedId, op.getContractID());
    }

    @Test
    void requiresCreateFromEthDataToHaveInitcode() {
        final var optSynthBody = subject.synthContractOpFromEth(ethTxData);

        assertTrue(optSynthBody.isEmpty());
    }

    @Test
    void synthesizesCreateFromEthDataWithInitcode() {
        given(ethTxData.hasCallData()).willReturn(true);
        given(ethTxData.callData()).willReturn(callData);
        given(ethTxData.gasLimit()).willReturn(gasLimit);
        given(ethTxData.value()).willReturn(value);

        final var optSynthBody = subject.synthContractOpFromEth(ethTxData);

        assertTrue(optSynthBody.isPresent());
        final var synthBody = optSynthBody.get().build();

        assertTrue(synthBody.hasContractCreateInstance());
        final var op = synthBody.getContractCreateInstance();
        assertArrayEquals(callData, op.getInitcode().toByteArray());
        assertEquals(gasLimit, op.getGas());
        assertEquals(valueInTinyBars, op.getInitialBalance());
        assertEquals(autoRenewPeriod, op.getAutoRenewPeriod());
    }

    @Test
    void synthesizesPrecheckCreateFromEthDataWithInitcode() {
        given(ethTxData.hasCallData()).willReturn(true);
        given(ethTxData.callData()).willReturn(callData);
        given(ethTxData.gasLimit()).willReturn(gasLimit);
        given(ethTxData.value()).willReturn(value);

        final var synthBody = subject.synthPrecheckContractOpFromEth(ethTxData);

        assertTrue(synthBody.hasContractCreateInstance());
        final var op = synthBody.getContractCreateInstance();
        assertArrayEquals(callData, op.getInitcode().toByteArray());
        assertEquals(gasLimit, op.getGas());
        assertEquals(valueInTinyBars, op.getInitialBalance());
        assertEquals(autoRenewPeriod, op.getAutoRenewPeriod());
    }

    @Test
    void synthesizesPrecheckCreateFromEthDataWithoutInitcode() {
        given(ethTxData.gasLimit()).willReturn(gasLimit);
        given(ethTxData.value()).willReturn(value);
        given(ethTxData.replaceCallData(MOCK_INITCODE)).willReturn(ethTxData);
        given(ethTxData.callData()).willReturn(MOCK_INITCODE);

        final var synthBody = subject.synthPrecheckContractOpFromEth(ethTxData);

        assertTrue(synthBody.hasContractCreateInstance());
        final var op = synthBody.getContractCreateInstance();
        assertArrayEquals(SyntheticTxnFactory.MOCK_INITCODE, op.getInitcode().toByteArray());
        assertEquals(gasLimit, op.getGas());
        assertEquals(valueInTinyBars, op.getInitialBalance());
        assertEquals(autoRenewPeriod, op.getAutoRenewPeriod());
    }

    @Test
    void synthesizesExpectedContractAutoRenew() {
        final var result = subject.synthContractAutoRenew(contractNum, newExpiry);
        final var synthBody = result.build();

        assertTrue(result.hasContractUpdateInstance());
        final var op = synthBody.getContractUpdateInstance();
        assertEquals(contractNum.toGrpcContractID(), op.getContractID());
        assertEquals(newExpiry, op.getExpirationTime().getSeconds());
    }

    @Test
    void synthesizesExpectedNoopContractUpdate() {
        final var result = subject.synthNoopContractUpdate(contractNum);
        final var synthBody = result.build();

        assertTrue(result.hasContractUpdateInstance());
        final var op = synthBody.getContractUpdateInstance();
        assertEquals(contractNum.toGrpcContractID(), op.getContractID());
    }

    @Test
    void synthesizesExpectedContractAutoRemove() {
        final var result = subject.synthContractAutoRemove(contractNum);
        final var synthBody = result.build();

        assertTrue(result.hasContractDeleteInstance());
        final var op = synthBody.getContractDeleteInstance();
        assertEquals(contractNum.toGrpcContractID(), op.getContractID());
        assertTrue(op.getPermanentRemoval());
    }

    @Test
    void synthesizesExpectedAccountAutoRemove() {
        final var result = subject.synthAccountAutoRemove(accountNum);
        final var synthBody = result.build();

        assertTrue(result.hasCryptoDelete());
        final var op = synthBody.getCryptoDelete();
        assertEquals(accountNum.toGrpcAccountId(), op.getDeleteAccountID());
    }

    @Test
    void synthesizesExpectedAccountAutoRenew() {
        final var result = subject.synthAccountAutoRenew(accountNum, newExpiry);
        final var synthBody = result.build();

        assertTrue(result.hasCryptoUpdateAccount());
        final var op = synthBody.getCryptoUpdateAccount();
        final var grpcId = accountNum.toGrpcAccountId();
        assertEquals(grpcId, op.getAccountIDToUpdate());
        assertEquals(grpcId, synthBody.getTransactionID().getAccountID());
        assertEquals(newExpiry, op.getExpirationTime().getSeconds());
    }

    @Test
    void createsExpectedContractSkeleton() {
        final var result = subject.contractCreation(customizer);
        verify(customizer).customizeSynthetic(any());
        assertTrue(result.hasContractCreateInstance());
        assertFalse(result.getContractCreateInstance().hasAutoRenewAccountId());
    }

    @Test
    void createsExpectedTransactionCall() {
        final var result = subject.createTransactionCall(1, Bytes.of(1));
        final var txnBody = result.build();

        assertTrue(result.hasContractCall());
        assertEquals(1, txnBody.getContractCall().getGas());
        assertEquals(
                EntityIdUtils.contractIdFromEvmAddress(
                        Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()),
                txnBody.getContractCall().getContractID());
        assertEquals(
                ByteString.copyFrom(Bytes.of(1).toArray()),
                txnBody.getContractCall().getFunctionParameters());
    }

    @Test
    void createsExpectedFreezeTokenCall() {
        final var freezeWrapper = new TokenFreezeUnfreezeWrapper(fungible, a);
        final var result = subject.createFreeze(freezeWrapper);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenFreeze().getToken());
        assertEquals(a, txnBody.getTokenFreeze().getAccount());
    }

    @Test
    void createsExpectedUnfreezeTokenCall() {
        final var unfreezeWrapper = new TokenFreezeUnfreezeWrapper(fungible, a);
        final var result = subject.createUnFreeze(unfreezeWrapper);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenUnfreeze().getToken());
        assertEquals(a, txnBody.getTokenUnfreeze().getAccount());
    }

    @Test
    void createsExpectedCryptoCreate() {
        final var balance = 10L;
        final var alias = KeyFactory.getDefaultInstance().newEd25519();
        final var result = subject.createAccount(alias, balance, 0);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(0L, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
        assertEquals(
                alias.toByteString(), txnBody.getCryptoCreateAccount().getKey().toByteString());
    }

    @Test
    void createsExpectedHollowAccountCreate() {
        final var balance = 10L;
        final var evmAddressAlias =
                ByteString.copyFrom(Hex.decode("a94f5374fce5edbc8e2a8697c15331677e6ebf0b"));
        final var result = subject.createHollowAccount(evmAddressAlias, balance);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(Key.getDefaultInstance(), txnBody.getCryptoCreateAccount().getKey());
        assertEquals(
                EntityIdUtils.EVM_ADDRESS_SIZE, txnBody.getCryptoCreateAccount().getAlias().size());
        assertEquals(LAZY_MEMO, txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(0L, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
    }

    @Test
    void fungibleTokenChangeAddsAutoAssociations() {
        final var balance = 10L;
        final var alias = KeyFactory.getDefaultInstance().newEd25519();
        final var result = subject.createAccount(alias, balance, 1);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(1, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
        assertEquals(
                alias.toByteString(), txnBody.getCryptoCreateAccount().getKey().toByteString());
    }

    @Test
    void nftOwnershipChangeAddsAutoAssociations() {
        final var balance = 10L;
        final var alias = KeyFactory.getDefaultInstance().newEd25519();
        final var xfer =
                NftTransfer.newBuilder()
                        .setSenderAccountID(asAliasAccount(ByteString.copyFromUtf8("somebody")))
                        .setReceiverAccountID(a)
                        .setSerialNumber(serialNo)
                        .setIsApproval(true)
                        .build();

        final var nftChange = changingNftOwnership(Id.fromGrpcToken(token), token, xfer, payer);

        final var result = subject.createAccount(alias, balance, 1);

        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(1L, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
        assertEquals(
                alias.toByteString(), txnBody.getCryptoCreateAccount().getKey().toByteString());
    }

    @Test
    void createsExpectedNodeStakeUpdate() {
        final var now = Instant.now();
        final var timestamp =
                Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .setNanos(now.getNano())
                        .build();
        final var nodeStakes =
                List.of(
                        NodeStake.newBuilder()
                                .setStake(123_456_789L)
                                .setStakeRewarded(1_234_567L)
                                .build(),
                        NodeStake.newBuilder()
                                .setStake(987_654_321L)
                                .setStakeRewarded(54_321L)
                                .build());
        propertySource.ensureProps();
        dynamicProperties =
                new GlobalDynamicProperties(new HederaNumbers(propertySource), propertySource);
        subject = new SyntheticTxnFactory(dynamicProperties);

        final var txnBody = subject.nodeStakeUpdate(timestamp, nodeStakes, propertySource);

        assertTrue(txnBody.hasNodeStakeUpdate());
        assertEquals(timestamp, txnBody.getNodeStakeUpdate().getEndOfStakingPeriod());
        assertEquals(123_456_789L, txnBody.getNodeStakeUpdate().getNodeStake(0).getStake());
        assertEquals(1_234_567L, txnBody.getNodeStakeUpdate().getNodeStake(0).getStakeRewarded());
        assertEquals(987_654_321L, txnBody.getNodeStakeUpdate().getNodeStake(1).getStake());
        assertEquals(54_321L, txnBody.getNodeStakeUpdate().getNodeStake(1).getStakeRewarded());
        assertEquals(17_808L, txnBody.getNodeStakeUpdate().getMaxStakingRewardRatePerHbar());
        assertEquals(0L, txnBody.getNodeStakeUpdate().getNodeRewardFeeFraction().getNumerator());
        assertEquals(
                100L, txnBody.getNodeStakeUpdate().getNodeRewardFeeFraction().getDenominator());
        assertEquals(365, txnBody.getNodeStakeUpdate().getStakingPeriodsStored());
        assertEquals(1L, txnBody.getNodeStakeUpdate().getStakingPeriod());
        assertEquals(
                100L, txnBody.getNodeStakeUpdate().getStakingRewardFeeFraction().getNumerator());
        assertEquals(
                100L, txnBody.getNodeStakeUpdate().getStakingRewardFeeFraction().getDenominator());
        assertEquals(
                25_000_000_000_000_000L, txnBody.getNodeStakeUpdate().getStakingStartThreshold());
        assertEquals(0L, txnBody.getNodeStakeUpdate().getStakingRewardRate());
    }

    @Test
    void createsExpectedAssociations() {
        final var tokens = List.of(fungible, nonFungible);
        final var associations = Association.multiAssociation(a, tokens);

        final var result = subject.createAssociate(associations);
        final var txnBody = result.build();

        assertEquals(a, txnBody.getTokenAssociate().getAccount());
        assertEquals(tokens, txnBody.getTokenAssociate().getTokensList());
    }

    @Test
    void createsExpectedDissociations() {
        final var tokens = List.of(fungible, nonFungible);
        final var associations = Dissociation.multiDissociation(a, tokens);

        final var result = subject.createDissociate(associations);
        final var txnBody = result.build();

        assertEquals(a, txnBody.getTokenDissociate().getAccount());
        assertEquals(tokens, txnBody.getTokenDissociate().getTokensList());
    }

    @Test
    void createsExpectedNftMint() {
        final var nftMints = MintWrapper.forNonFungible(nonFungible, newMetadata);

        final var result = subject.createMint(nftMints);
        final var txnBody = result.build();

        assertEquals(nonFungible, txnBody.getTokenMint().getToken());
        assertEquals(newMetadata, txnBody.getTokenMint().getMetadataList());
    }

    @Test
    void createsExpectedNftBurn() {
        final var nftBurns = BurnWrapper.forNonFungible(nonFungible, targetSerialNos);

        final var result = subject.createBurn(nftBurns);
        final var txnBody = result.build();

        assertEquals(nonFungible, txnBody.getTokenBurn().getToken());
        assertEquals(targetSerialNos, txnBody.getTokenBurn().getSerialNumbersList());
    }

    @Test
    void createsExpectedFungibleMint() {
        final var amount = 1234L;
        final var funMints = MintWrapper.forFungible(fungible, amount);

        final var result = subject.createMint(funMints);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenMint().getToken());
        assertEquals(amount, txnBody.getTokenMint().getAmount());
    }

    @Test
    void createsExpectedFungibleApproveAllowance() {
        final var amount = BigInteger.ONE;
        var allowances = new ApproveWrapper(token, receiver, amount, BigInteger.ZERO, true);

        final var result = subject.createFungibleApproval(allowances);
        final var txnBody = result.build();

        assertEquals(
                amount.longValue(),
                txnBody.getCryptoApproveAllowance().getTokenAllowances(0).getAmount());
        assertEquals(token, txnBody.getCryptoApproveAllowance().getTokenAllowances(0).getTokenId());
        assertEquals(
                receiver, txnBody.getCryptoApproveAllowance().getTokenAllowances(0).getSpender());
    }

    @Test
    void createsExpectedNonfungibleApproveAllowanceWithOwnerAsOperator() {
        var allowances =
                new ApproveWrapper(token, receiver, BigInteger.ZERO, BigInteger.ONE, false);
        final var ownerId = new EntityId(0, 0, 666);

        final var result = subject.createNonfungibleApproval(allowances, ownerId, ownerId);
        final var txnBody = result.build();

        final var allowance = txnBody.getCryptoApproveAllowance().getNftAllowances(0);
        assertEquals(token, allowance.getTokenId());
        assertEquals(receiver, allowance.getSpender());
        assertEquals(ownerId.toGrpcAccountId(), allowance.getOwner());
        assertEquals(AccountID.getDefaultInstance(), allowance.getDelegatingSpender());
        assertEquals(1L, allowance.getSerialNumbers(0));
    }

    @Test
    void createsExpectedNonfungibleApproveAllowanceWithNonOwnerOperator() {
        var allowances =
                new ApproveWrapper(token, receiver, BigInteger.ZERO, BigInteger.ONE, false);
        final var ownerId = new EntityId(0, 0, 666);
        final var operatorId = new EntityId(0, 0, 777);

        final var result = subject.createNonfungibleApproval(allowances, ownerId, operatorId);
        final var txnBody = result.build();

        final var allowance = txnBody.getCryptoApproveAllowance().getNftAllowances(0);
        assertEquals(token, allowance.getTokenId());
        assertEquals(receiver, allowance.getSpender());
        assertEquals(ownerId.toGrpcAccountId(), allowance.getOwner());
        assertEquals(operatorId.toGrpcAccountId(), allowance.getDelegatingSpender());
        assertEquals(1L, allowance.getSerialNumbers(0));
    }

    @Test
    void createsExpectedNonfungibleApproveAllowanceWithoutOwner() {
        var allowances =
                new ApproveWrapper(token, receiver, BigInteger.ZERO, BigInteger.ONE, false);
        final var operatorId = new EntityId(0, 0, 666);

        final var result = subject.createNonfungibleApproval(allowances, null, operatorId);
        final var txnBody = result.build();

        final var allowance = txnBody.getCryptoApproveAllowance().getNftAllowances(0);
        assertEquals(token, allowance.getTokenId());
        assertEquals(receiver, allowance.getSpender());
        assertEquals(AccountID.getDefaultInstance(), allowance.getOwner());
        assertEquals(1L, allowance.getSerialNumbers(0));
    }

    @Test
    void createsAdjustAllowanceForAllNFT() {
        var allowances = new SetApprovalForAllWrapper(nonFungible, receiver, true);

        final var result = subject.createApproveAllowanceForAllNFT(allowances);
        final var txnBody = result.build();

        assertEquals(
                receiver, txnBody.getCryptoApproveAllowance().getNftAllowances(0).getSpender());
        assertEquals(
                nonFungible, txnBody.getCryptoApproveAllowance().getNftAllowances(0).getTokenId());
        assertEquals(
                BoolValue.of(true),
                txnBody.getCryptoApproveAllowance().getNftAllowances(0).getApprovedForAll());
    }

    @Test
    void createsDeleteAllowance() {
        var allowances =
                new ApproveWrapper(token, receiver, BigInteger.ZERO, BigInteger.ONE, false);

        final var result = subject.createDeleteAllowance(allowances, senderId);
        final var txnBody = result.build();

        assertEquals(token, txnBody.getCryptoDeleteAllowance().getNftAllowances(0).getTokenId());
        assertEquals(
                1L, txnBody.getCryptoDeleteAllowance().getNftAllowances(0).getSerialNumbers(0));
        assertEquals(sender, txnBody.getCryptoDeleteAllowance().getNftAllowances(0).getOwner());
    }

    @Test
    void createsExpectedDeleteFungibleTokenCall() {
        final var deleteWrapper = new DeleteWrapper(fungible);
        final var result = subject.createDelete(deleteWrapper);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenDeletion().getToken());
    }

    @Test
    void createsExpectedDeleteNonFungibleTokenCall() {
        final var deleteWrapper = new DeleteWrapper(nonFungible);
        final var result = subject.createDelete(deleteWrapper);
        final var txnBody = result.build();

        assertEquals(nonFungible, txnBody.getTokenDeletion().getToken());
    }

    @Test
    void createsExpectedFungibleBurn() {
        final var amount = 1234L;
        final var funBurns = BurnWrapper.forFungible(fungible, amount);

        final var result = subject.createBurn(funBurns);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenBurn().getToken());
        assertEquals(amount, txnBody.getTokenBurn().getAmount());
    }

    @Test
    void createsExpectedFungibleTokenCreate() {
        // given
        final var adminKey =
                new KeyValueWrapper(
                        false,
                        null,
                        new byte[] {},
                        new byte[] {},
                        EntityIdUtils.contractIdFromEvmAddress(contractAddress));
        final var multiKey =
                new KeyValueWrapper(
                        false,
                        EntityIdUtils.contractIdFromEvmAddress(contractAddress),
                        new byte[] {},
                        new byte[] {},
                        null);
        final var wrapper =
                createTokenCreateWrapperWithKeys(
                        List.of(
                                new TokenKeyWrapper(254, multiKey),
                                new TokenKeyWrapper(1, adminKey)));
        wrapper.setFixedFees(List.of(fixedFee));
        wrapper.setFractionalFees(List.of(fractionalFee));

        // when
        final var result = subject.createTokenCreate(wrapper);
        final var txnBody = result.build().getTokenCreation();

        // then
        assertTrue(result.hasTokenCreation());

        assertEquals(TokenType.FUNGIBLE_COMMON, txnBody.getTokenType());
        assertEquals("token", txnBody.getName());
        assertEquals("symbol", txnBody.getSymbol());
        assertEquals(account, txnBody.getTreasury());
        assertEquals("memo", txnBody.getMemo());
        assertEquals(TokenSupplyType.INFINITE, txnBody.getSupplyType());
        assertEquals(Long.MAX_VALUE, txnBody.getInitialSupply());
        assertEquals(Integer.MAX_VALUE, txnBody.getDecimals());
        assertEquals(5054L, txnBody.getMaxSupply());
        assertFalse(txnBody.getFreezeDefault());
        assertEquals(442L, txnBody.getExpiry().getSeconds());
        assertEquals(555L, txnBody.getAutoRenewPeriod().getSeconds());
        assertEquals(payer, txnBody.getAutoRenewAccount());

        // keys assertions
        assertTrue(txnBody.hasAdminKey());
        assertEquals(adminKey.asGrpc(), txnBody.getAdminKey());
        assertTrue(txnBody.hasKycKey());
        assertEquals(multiKey.asGrpc(), txnBody.getKycKey());
        assertTrue(txnBody.hasFreezeKey());
        assertEquals(multiKey.asGrpc(), txnBody.getFreezeKey());
        assertTrue(txnBody.hasWipeKey());
        assertEquals(multiKey.asGrpc(), txnBody.getWipeKey());

        // assert custom fees
        assertEquals(2, txnBody.getCustomFeesCount());
        assertEquals(fixedFee.asGrpc(), txnBody.getCustomFees(0));
        assertEquals(fractionalFee.asGrpc(), txnBody.getCustomFees(1));
    }

    @Test
    void createsExpectedNonFungibleTokenCreate() {
        // given
        final var multiKey =
                new KeyValueWrapper(
                        false,
                        EntityIdUtils.contractIdFromEvmAddress(contractAddress),
                        new byte[] {},
                        new byte[] {},
                        null);
        final var wrapper =
                createNonFungibleTokenCreateWrapperWithKeys(
                        List.of(new TokenKeyWrapper(112, multiKey)));
        wrapper.setFixedFees(List.of(fixedFee));
        wrapper.setRoyaltyFees(List.of(royaltyFee));

        // when
        final var result = subject.createTokenCreate(wrapper);
        final var txnBody = result.build().getTokenCreation();

        // then
        assertTrue(result.hasTokenCreation());

        assertEquals(TokenType.NON_FUNGIBLE_UNIQUE, txnBody.getTokenType());
        assertEquals("nft", txnBody.getName());
        assertEquals("NFT", txnBody.getSymbol());
        assertEquals(account, txnBody.getTreasury());
        assertEquals("nftMemo", txnBody.getMemo());
        assertEquals(TokenSupplyType.FINITE, txnBody.getSupplyType());
        assertEquals(0L, txnBody.getInitialSupply());
        assertEquals(0, txnBody.getDecimals());
        assertEquals(5054L, txnBody.getMaxSupply());
        assertTrue(txnBody.getFreezeDefault());
        assertEquals(0, txnBody.getExpiry().getSeconds());
        assertEquals(0, txnBody.getAutoRenewPeriod().getSeconds());
        assertFalse(txnBody.hasAutoRenewAccount());

        // keys assertions
        assertTrue(txnBody.hasSupplyKey());
        assertEquals(multiKey.asGrpc(), txnBody.getSupplyKey());
        assertTrue(txnBody.hasFeeScheduleKey());
        assertEquals(multiKey.asGrpc(), txnBody.getFeeScheduleKey());
        assertTrue(txnBody.hasPauseKey());
        assertEquals(multiKey.asGrpc(), txnBody.getPauseKey());

        // assert custom fees
        assertEquals(2, txnBody.getCustomFeesCount());
        assertEquals(fixedFee.asGrpc(), txnBody.getCustomFees(0));
        assertEquals(royaltyFee.asGrpc(), txnBody.getCustomFees(1));
    }

    @Test
    void createsExpectedCryptoTransfer() {
        final var fungibleTransfer =
                new SyntheticTxnFactory.FungibleTokenTransfer(secondAmount, false, fungible, b, a);

        final var result =
                subject.createCryptoTransfer(
                        List.of(
                                new TokenTransferWrapper(
                                        Collections.emptyList(), List.of(fungibleTransfer))));
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        final var expFungibleTransfer = tokenTransfers.get(0);
        assertEquals(fungible, expFungibleTransfer.getToken());
        assertEquals(
                List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
                expFungibleTransfer.getTransfersList());
    }

    @Test
    void createsExpectedTokenUpdateCallForFungible() {
        // given
        final var adminKey =
                new KeyValueWrapper(
                        false,
                        null,
                        new byte[] {},
                        new byte[] {},
                        EntityIdUtils.contractIdFromEvmAddress(contractAddress));
        final var multiKey =
                new KeyValueWrapper(
                        false,
                        EntityIdUtils.contractIdFromEvmAddress(contractAddress),
                        new byte[] {},
                        new byte[] {},
                        null);
        final var tokenUpdateWrapper =
                createFungibleTokenUpdateWrapperWithKeys(
                        List.of(
                                new TokenKeyWrapper(112, multiKey),
                                new TokenKeyWrapper(1, adminKey)));
        final var result = subject.createTokenUpdate(tokenUpdateWrapper);
        final var txnBody = result.build().getTokenUpdate();

        assertEquals(HTSTestsUtil.fungible, txnBody.getToken());

        assertEquals("fungible", txnBody.getName());
        assertEquals("G", txnBody.getSymbol());
        assertEquals(account, txnBody.getTreasury());
        assertEquals("G token memo", txnBody.getMemo().getValue());
        assertEquals(1, txnBody.getExpiry().getSeconds());
        assertEquals(2, txnBody.getAutoRenewPeriod().getSeconds());
        assertTrue(txnBody.hasAutoRenewAccount());

        // keys assertions
        assertTrue(txnBody.hasSupplyKey());
        assertEquals(multiKey.asGrpc(), txnBody.getSupplyKey());
        assertTrue(txnBody.hasFeeScheduleKey());
        assertEquals(multiKey.asGrpc(), txnBody.getFeeScheduleKey());
        assertTrue(txnBody.hasPauseKey());
        assertEquals(multiKey.asGrpc(), txnBody.getPauseKey());
    }

    @Test
    void createsExpectedTokenUpdateCallForNonFungible() {
        // given
        final var ComplexKey =
                new KeyValueWrapper(
                        false,
                        null,
                        new byte[] {},
                        new byte[] {},
                        EntityIdUtils.contractIdFromEvmAddress(contractAddress));
        final var multiKey =
                new KeyValueWrapper(
                        false,
                        EntityIdUtils.contractIdFromEvmAddress(contractAddress),
                        new byte[] {},
                        new byte[] {},
                        null);
        final var wrapper =
                createNonFungibleTokenUpdateWrapperWithKeys(
                        List.of(
                                new TokenKeyWrapper(112, multiKey),
                                new TokenKeyWrapper(2, ComplexKey),
                                new TokenKeyWrapper(4, ComplexKey),
                                new TokenKeyWrapper(8, ComplexKey)));

        // when
        final var result = subject.createTokenUpdate(wrapper);
        final var txnBody = result.build().getTokenUpdate();

        // then

        assertEquals(0, txnBody.getExpiry().getSeconds());
        assertEquals(0, txnBody.getAutoRenewPeriod().getSeconds());
        assertFalse(txnBody.hasAutoRenewAccount());

        // keys assertions
        assertTrue(txnBody.hasSupplyKey());
        assertEquals(multiKey.asGrpc(), txnBody.getSupplyKey());
        assertTrue(txnBody.hasFeeScheduleKey());
        assertEquals(multiKey.asGrpc(), txnBody.getFeeScheduleKey());
        assertTrue(txnBody.hasPauseKey());
        assertEquals(multiKey.asGrpc(), txnBody.getPauseKey());
    }

    @Test
    void createsExpectedTokenUpdateKeysCallForFungible() {
        // given
        final var adminKey =
                new KeyValueWrapper(
                        false,
                        null,
                        new byte[] {},
                        new byte[] {},
                        EntityIdUtils.contractIdFromEvmAddress(contractAddress));
        final var multiKey =
                new KeyValueWrapper(
                        false,
                        EntityIdUtils.contractIdFromEvmAddress(contractAddress),
                        new byte[] {},
                        new byte[] {},
                        null);
        final var tokenUpdateKeysWrapper =
                new TokenUpdateKeysWrapper(
                        fungible,
                        List.of(
                                new TokenKeyWrapper(112, multiKey),
                                new TokenKeyWrapper(1, adminKey)));
        final var result = subject.createTokenUpdateKeys(tokenUpdateKeysWrapper);
        final var txnBody = result.build().getTokenUpdate();

        assertEquals(fungible, txnBody.getToken());

        // keys assertions
        assertTrue(txnBody.hasSupplyKey());
        assertEquals(multiKey.asGrpc(), txnBody.getSupplyKey());
        assertTrue(txnBody.hasFeeScheduleKey());
        assertEquals(multiKey.asGrpc(), txnBody.getFeeScheduleKey());
        assertTrue(txnBody.hasPauseKey());
        assertEquals(multiKey.asGrpc(), txnBody.getPauseKey());
    }

    @Test
    void acceptsEmptyWrappers() {
        final var result = subject.createCryptoTransfer(List.of());

        final var txnBody = result.build();
        assertEquals(0, txnBody.getCryptoTransfer().getTokenTransfersCount());
    }

    @Test
    void canCreateApprovedNftExchanges() {
        final var approvedExchange =
                SyntheticTxnFactory.NftExchange.fromApproval(1L, nonFungible, a, b);
        assertTrue(approvedExchange.isApproval());
    }

    @Test
    void mergesRepeatedTokenIds() {
        final var fungibleTransfer =
                new SyntheticTxnFactory.FungibleTokenTransfer(secondAmount, false, fungible, b, a);
        final var nonFungibleTransfer = new SyntheticTxnFactory.NftExchange(1L, nonFungible, a, b);
        assertFalse(nonFungibleTransfer.isApproval());

        final var result =
                subject.createCryptoTransfer(
                        List.of(
                                new TokenTransferWrapper(
                                        Collections.emptyList(), List.of(fungibleTransfer)),
                                new TokenTransferWrapper(
                                        Collections.emptyList(), List.of(fungibleTransfer)),
                                new TokenTransferWrapper(
                                        List.of(nonFungibleTransfer), Collections.emptyList())));

        final var txnBody = result.build();

        final var finalTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        assertEquals(2, finalTransfers.size());
        final var mergedFungible = finalTransfers.get(0);
        assertEquals(fungible, mergedFungible.getToken());
        assertEquals(
                List.of(aaWith(b, -2 * secondAmount), aaWith(a, +2 * secondAmount)),
                mergedFungible.getTransfersList());
    }

    @Test
    void createsExpectedCryptoTransferForNFTTransfer() {
        final var nftExchange = new SyntheticTxnFactory.NftExchange(serialNo, nonFungible, a, c);

        final var result =
                subject.createCryptoTransfer(
                        Collections.singletonList(
                                new TokenTransferWrapper(
                                        List.of(nftExchange), Collections.emptyList())));
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        final var expNftTransfer = tokenTransfers.get(0);
        assertEquals(nonFungible, expNftTransfer.getToken());
        assertEquals(List.of(nftExchange.asGrpc()), expNftTransfer.getNftTransfersList());
        assertEquals(1, tokenTransfers.size());
    }

    @Test
    void createsExpectedCryptoTransferForFungibleTransfer() {
        final var fungibleTransfer =
                new SyntheticTxnFactory.FungibleTokenTransfer(secondAmount, false, fungible, b, a);

        final var result =
                subject.createCryptoTransfer(
                        Collections.singletonList(
                                new TokenTransferWrapper(
                                        Collections.emptyList(), List.of(fungibleTransfer))));
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        final var expFungibleTransfer = tokenTransfers.get(0);
        assertEquals(fungible, expFungibleTransfer.getToken());
        assertEquals(
                List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
                expFungibleTransfer.getTransfersList());
        assertEquals(1, tokenTransfers.size());
    }

    @Test
    void createsExpectedCryptoTransfersForMultipleTransferWrappers() {
        final var nftExchange = new SyntheticTxnFactory.NftExchange(serialNo, nonFungible, a, c);
        final var fungibleTransfer =
                new SyntheticTxnFactory.FungibleTokenTransfer(secondAmount, false, fungible, b, a);

        final var result =
                subject.createCryptoTransfer(
                        List.of(
                                new TokenTransferWrapper(
                                        Collections.emptyList(), List.of(fungibleTransfer)),
                                new TokenTransferWrapper(
                                        List.of(nftExchange), Collections.emptyList())));
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();

        final var expFungibleTransfer = tokenTransfers.get(0);
        assertEquals(fungible, expFungibleTransfer.getToken());
        assertEquals(
                List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
                expFungibleTransfer.getTransfersList());

        final var expNftTransfer = tokenTransfers.get(1);
        assertEquals(nonFungible, expNftTransfer.getToken());
        assertEquals(List.of(nftExchange.asGrpc()), expNftTransfer.getNftTransfersList());
    }

    @Test
    void mergesFungibleTransfersAsExpected() {
        final var source =
                new TokenTransferWrapper(
                                Collections.emptyList(),
                                List.of(
                                        new SyntheticTxnFactory.FungibleTokenTransfer(
                                                1, false, fungible, a, b)))
                        .asGrpcBuilder();
        final var target =
                new TokenTransferWrapper(
                                Collections.emptyList(),
                                List.of(
                                        new SyntheticTxnFactory.FungibleTokenTransfer(
                                                2, false, fungible, b, c)))
                        .asGrpcBuilder();

        SyntheticTxnFactory.mergeTokenTransfers(target, source);

        assertEquals(fungible, target.getToken());
        final var transfers = target.getTransfersList();
        assertEquals(List.of(aaWith(b, -1), aaWith(c, +2), aaWith(a, -1)), transfers);
    }

    @Test
    void mergesNftExchangesAsExpected() {
        final var repeatedExchange = new SyntheticTxnFactory.NftExchange(1L, nonFungible, a, b);
        final var newExchange = new SyntheticTxnFactory.NftExchange(2L, nonFungible, a, b);
        final var source =
                new TokenTransferWrapper(
                                List.of(repeatedExchange, newExchange), Collections.emptyList())
                        .asGrpcBuilder();
        final var target =
                new TokenTransferWrapper(List.of(repeatedExchange), Collections.emptyList())
                        .asGrpcBuilder();

        SyntheticTxnFactory.mergeTokenTransfers(target, source);

        assertEquals(nonFungible, target.getToken());
        final var transfers = target.getNftTransfersList();
        assertEquals(List.of(repeatedExchange.asGrpc(), newExchange.asGrpc()), transfers);
    }

    @Test
    void distinguishesDifferentExchangeBuilders() {
        final var subject =
                new SyntheticTxnFactory.NftExchange(1L, nonFungible, a, b).asGrpc().toBuilder();

        final var differentSerialNo = new SyntheticTxnFactory.NftExchange(2L, nonFungible, a, b);
        final var differentSender = new SyntheticTxnFactory.NftExchange(1L, nonFungible, c, b);
        final var differentReceiver = new SyntheticTxnFactory.NftExchange(1L, nonFungible, a, c);

        assertFalse(
                SyntheticTxnFactory.areSameBuilder(
                        subject, differentSerialNo.asGrpc().toBuilder()));
        assertFalse(
                SyntheticTxnFactory.areSameBuilder(
                        subject, differentReceiver.asGrpc().toBuilder()));
        assertFalse(
                SyntheticTxnFactory.areSameBuilder(subject, differentSender.asGrpc().toBuilder()));
    }

    @Test
    void createsExpectedGrantKycCall() {
        final var grantWrapper = new GrantRevokeKycWrapper(fungible, a);
        final var result = subject.createGrantKyc(grantWrapper);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenGrantKyc().getToken());
        assertEquals(a, txnBody.getTokenGrantKyc().getAccount());
    }

    @Test
    void createsExpectedRevokeKycCall() {
        final var revokeWrapper = new GrantRevokeKycWrapper(fungible, a);
        final var result = subject.createRevokeKyc(revokeWrapper);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenRevokeKyc().getToken());
        assertEquals(a, txnBody.getTokenRevokeKyc().getAccount());
    }

    @Test
    void createsExpectedFungiblePause() {
        final var fungiblePause = new PauseWrapper(fungible);

        final var result = subject.createPause(fungiblePause);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenPause().getToken());
    }

    @Test
    void createsExpectedNonFungiblePause() {
        final var nonFungiblePause = new PauseWrapper(nonFungible);

        final var result = subject.createPause(nonFungiblePause);
        final var txnBody = result.build();

        assertEquals(nonFungible, txnBody.getTokenPause().getToken());
    }

    @Test
    void createsExpectedFungibleUnpause() {
        final var fungibleUnpause = new UnpauseWrapper(fungible);

        final var result = subject.createUnpause(fungibleUnpause);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenUnpause().getToken());
    }

    @Test
    void createsExpectedNonFungibleUnpause() {
        final var nonFungibleUnpause = new UnpauseWrapper(nonFungible);

        final var result = subject.createUnpause(nonFungibleUnpause);
        final var txnBody = result.build();

        assertEquals(nonFungible, txnBody.getTokenUnpause().getToken());
    }

    @Test
    void createsExpectedFungibleWipe() {
        final var amount = 1234L;
        final var fungibleWipe = WipeWrapper.forFungible(fungible, a, amount);

        final var result = subject.createWipe(fungibleWipe);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenWipe().getToken());
        assertEquals(amount, txnBody.getTokenWipe().getAmount());
        assertEquals(a, txnBody.getTokenWipe().getAccount());
    }

    @Test
    void createsExpectedNftWipe() {
        final var nftWipe = WipeWrapper.forNonFungible(nonFungible, a, targetSerialNos);

        final var result = subject.createWipe(nftWipe);
        final var txnBody = result.build();

        assertEquals(nonFungible, txnBody.getTokenWipe().getToken());
        assertEquals(a, txnBody.getTokenWipe().getAccount());
        assertEquals(targetSerialNos, txnBody.getTokenWipe().getSerialNumbersList());
    }

    private NftTransfer nftFromTo(final long num, final long sender, final long receiver) {
        return NftTransfer.newBuilder()
                .setSerialNumber(num)
                .setSenderAccountID(AccountID.newBuilder().setAccountNum(sender).build())
                .setReceiverAccountID(AccountID.newBuilder().setAccountNum(receiver).build())
                .build();
    }

    private AccountAmount aaWith(final long num, final long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(AccountID.newBuilder().setAccountNum(num).build())
                .setAmount(amount)
                .build();
    }

    @Test
    void createsExpectedUpdateTokenExpiryInfo() {
        final var updateExpiryInfo =
                new TokenUpdateExpiryInfoWrapper(token, new TokenExpiryWrapper(442L, payer, 555L));

        final var result = subject.createTokenUpdateExpiryInfo(updateExpiryInfo);
        final var txnBody = result.build();

        assertEquals(token, txnBody.getTokenUpdate().getToken());
        assertEquals(442L, txnBody.getTokenUpdate().getExpiry().getSeconds());
        assertEquals(payer, txnBody.getTokenUpdate().getAutoRenewAccount());
        assertEquals(555L, txnBody.getTokenUpdate().getAutoRenewPeriod().getSeconds());
    }

    @Test
    void createsExpectedUpdateTokenExpiryInfoWithZeroExpiry() {
        final var updateExpiryInfo =
                new TokenUpdateExpiryInfoWrapper(token, new TokenExpiryWrapper(0L, payer, 555L));

        final var result = subject.createTokenUpdateExpiryInfo(updateExpiryInfo);
        final var txnBody = result.build();

        assertEquals(token, txnBody.getTokenUpdate().getToken());
        assertEquals(0L, txnBody.getTokenUpdate().getExpiry().getSeconds());
        assertEquals(payer, txnBody.getTokenUpdate().getAutoRenewAccount());
        assertEquals(555L, txnBody.getTokenUpdate().getAutoRenewPeriod().getSeconds());
    }

    @Test
    void createsExpectedUpdateTokenExpiryInfoWithZeroAutoRenewPeriod() {
        final var updateExpiryInfo =
                new TokenUpdateExpiryInfoWrapper(token, new TokenExpiryWrapper(442L, payer, 0L));

        final var result = subject.createTokenUpdateExpiryInfo(updateExpiryInfo);
        final var txnBody = result.build();

        assertEquals(token, txnBody.getTokenUpdate().getToken());
        assertEquals(442L, txnBody.getTokenUpdate().getExpiry().getSeconds());
        assertEquals(payer, txnBody.getTokenUpdate().getAutoRenewAccount());
        assertEquals(0L, txnBody.getTokenUpdate().getAutoRenewPeriod().getSeconds());
    }

    @Test
    void createsExpectedUpdateTokenExpiryInfoWithNoAutoRenewAccount() {
        final var updateExpiryInfo =
                new TokenUpdateExpiryInfoWrapper(token, new TokenExpiryWrapper(442L, null, 555L));

        final var result = subject.createTokenUpdateExpiryInfo(updateExpiryInfo);
        final var txnBody = result.build();

        assertEquals(token, txnBody.getTokenUpdate().getToken());
        assertEquals(442L, txnBody.getTokenUpdate().getExpiry().getSeconds());
        assertTrue(txnBody.getTokenUpdate().getAutoRenewAccount().toString().isEmpty());
        assertEquals(555L, txnBody.getTokenUpdate().getAutoRenewPeriod().getSeconds());
    }

    private AccountAmount aaWith(final AccountID account, final long amount) {
        return AccountAmount.newBuilder().setAccountID(account).setAmount(amount).build();
    }

    private static final long serialNo = 100;
    private static final long secondAmount = 200;
    private static final long newExpiry = 1_234_567L;
    private final EntityNum contractNum = EntityNum.fromLong(666);
    private final EntityNum accountNum = EntityNum.fromLong(1234);
    private static final AccountID a = IdUtils.asAccount("0.0.2");
    private static final AccountID b = IdUtils.asAccount("0.0.3");
    private static final AccountID c = IdUtils.asAccount("0.0.4");
    private static final TokenID fungible = IdUtils.asToken("0.0.555");
    private static final TokenID nonFungible = IdUtils.asToken("0.0.666");
    private static final List<Long> targetSerialNos = List.of(1L, 2L, 3L);
    private static final List<ByteString> newMetadata =
            List.of(
                    ByteString.copyFromUtf8("AAA"),
                    ByteString.copyFromUtf8("BBB"),
                    ByteString.copyFromUtf8("CCC"));
    private static final long valueInTinyBars = 123;
    private static final BigInteger value =
            WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(valueInTinyBars));
    private static final long gasLimit = 123;
    private static final byte[] callData = "Between the idea and the reality".getBytes();
    private static final byte[] addressTo = unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbb");
    private static final Duration autoRenewPeriod =
            Duration.newBuilder().setSeconds(2592000).build();
}
