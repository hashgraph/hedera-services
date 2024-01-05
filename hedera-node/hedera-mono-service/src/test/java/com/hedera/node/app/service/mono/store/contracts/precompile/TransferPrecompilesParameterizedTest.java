/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.store.contracts.precompile;

import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFTS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKENS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.CustomFeeType;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfers;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.node.app.service.mono.keys.ActivationTest;
import com.hedera.node.app.service.mono.ledger.BalanceChange;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.TransferLogic;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.backing.HashMapBackingAccounts;
import com.hedera.node.app.service.mono.ledger.backing.HashMapBackingNfts;
import com.hedera.node.app.service.mono.ledger.backing.HashMapBackingTokenRels;
import com.hedera.node.app.service.mono.ledger.backing.HashMapBackingTokens;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.ChangeSummaryManager;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.fchashmap.FCHashMap;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferPrecompilesParameterizedTest {
    @Mock
    private HederaTokenStore hederaTokenStore;

    @Mock(strictness = LENIENT)
    private MessageFrame frame;

    @Mock
    private BiPredicate<JKey, TransactionSignature> cryptoValidity;

    @Mock
    private ActivationTest activationTest;

    @Mock(strictness = LENIENT)
    private TransactionContext txnCtx;

    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private TransferLogic transferLogic;

    @Mock
    private SideEffectsTracker sideEffects;

    @Mock(strictness = LENIENT)
    private HederaStackedWorldStateUpdater worldUpdater;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock(strictness = LENIENT)
    private ImpliedTransfersMarshal impliedTransfersMarshal;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private StateView stateView;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock(strictness = LENIENT)
    private InfrastructureFactory infrastructureFactory;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private HbarCentExchange exchange;

    private static final Bytes TRANSFER_TOKEN_INPUT = Bytes.fromHexString(
            "0xeca3691700000000000000000000000000000000000000000000000000000000000004380000000000000000000000000000000000000000000000000000000000000435000000000000000000000000000000000000000000000000000000000000043a0000000000000000000000000000000000000000000000000000000000000014");
    private static final Bytes NEGATIVE_AMOUNT_TRANSFER_TOKEN_INPUT = Bytes.fromHexString(
            "0xeca3691700000000000000000000000000000000000000000000000000000000000004380000000000000000000000000000000000000000000000000000000000000435000000000000000000000000000000000000000000000000000000000000043afffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff000");
    private static final Bytes POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT = Bytes.fromHexString(
            "0x82bba4930000000000000000000000000000000000000000000000000000000000000444000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000044100000000000000000000000000000000000000000010000000000000000004410000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000014");
    private static final Bytes POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT = Bytes.fromHexString(
            "0x82bba49300000000000000000000000000000000000000000000000000000000000004d8000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000004d500000000000000000000000000000000000000000000000000000000000004d500000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000014ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffec");
    private static final Bytes TRANSFER_NFT_INPUT = Bytes.fromHexString(
            "0x5cfc901100000000000000000000000000000000000000000000000000000000000004680000000000000000000000000000000000000000000000000000000000000465000000000000000000000000000000000000000000000000000000000000046a0000000000000000000000000000000000000000000000000000000000000065");
    private static final Bytes TRANSFER_NFTS_INPUT = Bytes.fromHexString(
            "0x2c4ba191000000000000000000000000000000000000000000000000000000000000047a000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000001400000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000047700000000000000000000000000000000000000000000000000000000000004770000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000047c000000000000000000000000000000000000000000000010000000000000047c0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000ea");

    private final SyntheticTxnFactory syntheticTxnFactory = new SyntheticTxnFactory(null);
    private PrecompilePricingUtils precompilePricingUtils;
    private TransferPrecompile subject;
    private static final Id payerIsContractId = new Id(0, 0, 820);
    private static final AccountID payerIsContract = payerIsContractId.asGrpcAccount();
    private static final AccountID payerIsNotContract = asAccount("0.0.822");
    private static final Address address = payerIsContractId.asEvmAddress();
    private final int maxHbarAdjusts = 5;
    private final int maxTokenAdjusts = 10;
    private final int maxOwnershipChanges = 15;
    private final boolean areNftsEnabled = true;
    private final boolean autoCreationEnabled = true;
    private final boolean lazyCreationEnabled = true;
    private final boolean areAllowancesEnabled = true;
    private final int maxFeeNesting = 20;
    private final int maxBalanceChanges = 20;
    private final ImpliedTransfersMeta.ValidationProps validationProps = new ImpliedTransfersMeta.ValidationProps(
            maxHbarAdjusts,
            maxTokenAdjusts,
            maxOwnershipChanges,
            maxFeeNesting,
            maxBalanceChanges,
            areNftsEnabled,
            autoCreationEnabled,
            lazyCreationEnabled,
            areAllowancesEnabled);

    @BeforeEach
    void setup() {
        precompilePricingUtils = new PrecompilePricingUtils(
                assetLoader, exchange, () -> feeCalculator, resourceCosts, stateView, accessorFactory);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTransferPrecompileArgs")
    void parameterizedTransferPrecompileTests(
            final String name,
            final boolean bodyMethodShouldPass,
            final boolean runMethodShouldPass,
            final int selector,
            final Bytes inputBytes,
            final List<BalanceChange> changes,
            final Set<CustomFeeType> htsUnsupportedCustomFeeReceiverDebits,
            final boolean canFallbaToApprovals) {

        final var liveTokenRels = new TransactionalLedger<>(
                TokenRelProperty.class,
                MerkleTokenRelStatus::new,
                new HashMapBackingTokenRels(),
                new ChangeSummaryManager<>());
        final var liveAccounts = new TransactionalLedger<>(
                AccountProperty.class, MerkleAccount::new, new HashMapBackingAccounts(), new ChangeSummaryManager<>());
        final var liveNfts = new TransactionalLedger<>(
                NftProperty.class,
                UniqueTokenAdapter::newEmptyMerkleToken,
                new HashMapBackingNfts(),
                new ChangeSummaryManager<>());
        final var liveTokens = new TransactionalLedger<>(
                TokenProperty.class, MerkleToken::new, new HashMapBackingTokens(), new ChangeSummaryManager<>());
        final FCHashMap<ByteString, EntityNum> aliases = new FCHashMap<>();
        final var liveAliases = new AliasManager(() -> aliases);
        final var ledgers = new WorldLedgers(liveAliases, liveTokenRels, liveAccounts, liveNfts, liveTokens);

        // The changes passed in here are not necessarily the same as the changes derived from the input bytes.
        // Stubbing impliedTransfersMarshal.assessCustomFeesAndValidate without mocking was too involved.
        // Instead, various alternative changes are passed in as parameters.
        final ImpliedTransfers impliedTransfers =
                ImpliedTransfers.valid(validationProps, changes, Collections.emptyList(), Collections.emptyList());

        given(infrastructureFactory.newImpliedTransfersMarshal(any())).willReturn(impliedTransfersMarshal);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, liveTokens, liveNfts, liveTokenRels))
                .willReturn(hederaTokenStore);
        given(infrastructureFactory.newTransferLogic(
                        hederaTokenStore, sideEffects, liveNfts, liveAccounts, liveTokenRels))
                .willReturn(transferLogic);
        given(impliedTransfersMarshal.validityWithCurrentProps(any())).willReturn(OK);
        given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(worldUpdater.aliases()).willReturn(liveAliases);

        given(frame.getSenderAddress()).willReturn(address);
        given(frame.getRecipientAddress()).willReturn(address);
        given(frame.getContractAddress()).willReturn(address);
        given(frame.getMessageFrameStack()).willReturn(new ArrayDeque<>());
        given(txnCtx.activePayer()).willReturn(payerIsContract);

        TxnAwareEvmSigsVerifier sigsVerifier =
                new TxnAwareEvmSigsVerifier(activationTest, txnCtx, cryptoValidity, dynamicProperties);

        subject = new TransferPrecompile(
                ledgers,
                worldUpdater,
                sigsVerifier,
                sideEffects,
                syntheticTxnFactory,
                infrastructureFactory,
                precompilePricingUtils,
                selector,
                senderAddress,
                htsUnsupportedCustomFeeReceiverDebits, // This is passed in as a parameter
                true,
                false,
                canFallbaToApprovals);

        if (bodyMethodShouldPass) {
            subject.body(inputBytes, a -> a);
        } else {
            assertThrows(IllegalArgumentException.class, () -> subject.body(inputBytes, a -> a));
            return;
        }

        if (runMethodShouldPass) {
            subject.run(frame);
        } else {
            assertThrows(InvalidTransactionException.class, () -> subject.run(frame));
        }
    }

    private static Stream<Arguments> provideTransferPrecompileArgs() {
        Set<CustomFeeType> allTypes = EnumSet.of(CustomFeeType.FIXED_FEE, CustomFeeType.ROYALTY_FALLBACK_FEE);
        Set<CustomFeeType> noType = Collections.emptySet();
        Set<CustomFeeType> fixedFeeType = EnumSet.of(CustomFeeType.FIXED_FEE);
        Set<CustomFeeType> royaltyFallbackFeeType = EnumSet.of(CustomFeeType.ROYALTY_FALLBACK_FEE);
        return Stream.of(
                // These tests should pass - as indicated by the first argument
                // Basic tests
                Arguments.of(
                        "basic transferToken no custom fee types allowed",
                        true,
                        true,
                        ABI_ID_TRANSFER_TOKEN,
                        TRANSFER_TOKEN_INPUT,
                        getBasicFungibleChanges(payerIsContract),
                        allTypes,
                        false),
                Arguments.of(
                        "basic transferToken all custom fee types allowed ",
                        true,
                        true,
                        ABI_ID_TRANSFER_TOKEN,
                        TRANSFER_TOKEN_INPUT,
                        getBasicFungibleChanges(payerIsContract),
                        noType,
                        false),
                Arguments.of(
                        "basic transferTokens",
                        true,
                        true,
                        ABI_ID_TRANSFER_TOKENS,
                        POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT,
                        getBasicFungibleChanges(payerIsContract),
                        allTypes,
                        false),
                Arguments.of(
                        "basic transferTokens with negative amount",
                        true,
                        true,
                        ABI_ID_TRANSFER_TOKENS,
                        POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT,
                        getBasicFungibleChanges(payerIsContract),
                        allTypes,
                        false),
                Arguments.of(
                        "basic transferNFT",
                        true,
                        true,
                        ABI_ID_TRANSFER_NFT,
                        TRANSFER_NFT_INPUT,
                        getBasicNFTChange(payerIsContract),
                        allTypes,
                        false),
                Arguments.of(
                        "basic transferNFTs",
                        true,
                        true,
                        ABI_ID_TRANSFER_NFTS,
                        TRANSFER_NFTS_INPUT,
                        getBasicNFTChange(payerIsContract),
                        allTypes,
                        false),
                // Changes that have custom fee but allowed via dynamic property.
                // A little odd in that the enum set contains fee types that are disallowed
                Arguments.of(
                        "custom fee with royalty fallback fee type disallowed",
                        true,
                        true,
                        ABI_ID_TRANSFER_NFTS,
                        TRANSFER_NFTS_INPUT,
                        getBasicCustomFeeWithDebit(),
                        royaltyFallbackFeeType,
                        false),
                Arguments.of(
                        "royalty with fallback fees with fixed fee type disallowed",
                        true,
                        true,
                        ABI_ID_TRANSFER_NFTS,
                        TRANSFER_NFTS_INPUT,
                        getRoyaltyFallbackCustomFeeWithDebit(),
                        fixedFeeType,
                        false),
                // Changes with approvals should pass even if the key activation fails
                Arguments.of(
                        "transferNFT with approval and failed key activation",
                        true,
                        true,
                        ABI_ID_TRANSFER_NFT,
                        TRANSFER_NFT_INPUT,
                        getApprovalNFTChange(),
                        allTypes,
                        false),
                Arguments.of(
                        "transferToken with approval and failed key activation",
                        true,
                        true,
                        ABI_ID_TRANSFER_TOKEN,
                        TRANSFER_TOKEN_INPUT,
                        getApprovalFungibleChanges(),
                        allTypes,
                        false),
                // Changes with fallback to approval should pass even if the key activation fails
                Arguments.of(
                        "transferNFT with failed key activation but can fallback to approval",
                        true,
                        true,
                        ABI_ID_TRANSFER_NFT,
                        TRANSFER_NFT_INPUT,
                        getBasicNFTChange(payerIsContract),
                        allTypes,
                        true),
                Arguments.of(
                        "transferToken with no approval but can fallback to approval",
                        true,
                        true,
                        ABI_ID_TRANSFER_TOKEN,
                        TRANSFER_TOKEN_INPUT,
                        getFungibleDebitChanges(),
                        allTypes,
                        true),

                // These tests should fail
                // Changes that have custom fee but not allowed via dynamic property
                Arguments.of(
                        "custom fee with fixed fee type disallowed",
                        true,
                        false,
                        ABI_ID_TRANSFER_NFTS,
                        TRANSFER_NFTS_INPUT,
                        getBasicCustomFeeWithDebit(),
                        fixedFeeType,
                        false),
                Arguments.of(
                        "royalty with fallback fees with royalty fallback fee type disallowed",
                        true,
                        false,
                        ABI_ID_TRANSFER_NFTS,
                        TRANSFER_NFTS_INPUT,
                        getRoyaltyFallbackCustomFeeWithDebit(),
                        royaltyFallbackFeeType,
                        false),
                // Fail due to failed key activation
                Arguments.of(
                        "transferToken with failed key activation",
                        true,
                        false,
                        ABI_ID_TRANSFER_TOKEN,
                        TRANSFER_TOKEN_INPUT,
                        getBasicFungibleChanges(payerIsNotContract),
                        allTypes,
                        false),
                Arguments.of(
                        "transferTokens with failed key activation",
                        true,
                        false,
                        ABI_ID_TRANSFER_TOKENS,
                        POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT,
                        getBasicFungibleChanges(payerIsNotContract),
                        allTypes,
                        false),
                Arguments.of(
                        "transferTokens with failed key activation and negative amount",
                        true,
                        false,
                        ABI_ID_TRANSFER_TOKENS,
                        POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT,
                        getBasicFungibleChanges(payerIsNotContract),
                        allTypes,
                        false),
                Arguments.of(
                        "transferNFT with failed key activation",
                        true,
                        false,
                        ABI_ID_TRANSFER_NFT,
                        TRANSFER_NFT_INPUT,
                        getBasicNFTChange(payerIsNotContract),
                        allTypes,
                        false),
                Arguments.of(
                        "transferNFTs with failed key activation",
                        true,
                        false,
                        ABI_ID_TRANSFER_NFTS,
                        TRANSFER_NFTS_INPUT,
                        getBasicNFTChange(payerIsNotContract),
                        allTypes,
                        false),
                Arguments.of(
                        "negative amount transferToken should fail when processing body",
                        false,
                        true,
                        ABI_ID_TRANSFER_TOKEN,
                        NEGATIVE_AMOUNT_TRANSFER_TOKEN_INPUT,
                        getBasicFungibleChanges(payerIsNotContract),
                        allTypes,
                        false));
    }

    private static List<BalanceChange> getBasicFungibleChanges(AccountID payerAccount) {
        final AccountID account = asAccount("0.0.1077");
        final var token = new Id(0, 0, 1078);
        return List.of(
                BalanceChange.changingFtUnits(
                        token,
                        token.asGrpcToken(),
                        AccountAmount.newBuilder()
                                .setAmount(20)
                                .setAccountID(payerAccount)
                                .build(),
                        account),
                BalanceChange.changingFtUnits(
                        token,
                        token.asGrpcToken(),
                        AccountAmount.newBuilder()
                                .setAmount(-20)
                                .setAccountID(payerAccount)
                                .build(),
                        account));
    }

    private static List<BalanceChange> getApprovalFungibleChanges() {
        final AccountID account = asAccount("0.0.1077");
        final var token = new Id(0, 0, 1078);
        return List.of(BalanceChange.changingFtUnits(
                token,
                token.asGrpcToken(),
                AccountAmount.newBuilder()
                        .setAmount(-20)
                        .setAccountID(account)
                        .setIsApproval(true)
                        .build(),
                payerIsContract));
    }

    private static List<BalanceChange> getFungibleDebitChanges() {
        final AccountID account = asAccount("0.0.1077");
        final var token = new Id(0, 0, 1078);
        return List.of(BalanceChange.changingFtUnits(
                token,
                token.asGrpcToken(),
                AccountAmount.newBuilder()
                        .setAmount(-20)
                        .setAccountID(payerIsContract)
                        .build(),
                account));
    }

    private static List<BalanceChange> getBasicNFTChange(AccountID payerAccount) {
        final AccountID receiver = asAccount("0.0.1080");
        final var token = new Id(0, 0, 1081);
        return List.of(BalanceChange.changingNftOwnership(
                token,
                token.asGrpcToken(),
                NftTransfer.newBuilder()
                        .setSenderAccountID(payerAccount)
                        .setReceiverAccountID(receiver)
                        .setSerialNumber(1)
                        .build(),
                payerAccount));
    }

    private static List<BalanceChange> getApprovalNFTChange() {
        final AccountID sender = asAccount("0.0.1079");
        final AccountID receiver = asAccount("0.0.1080");
        final var token = new Id(0, 0, 1081);
        return List.of(BalanceChange.changingNftOwnership(
                token,
                token.asGrpcToken(),
                NftTransfer.newBuilder()
                        .setSenderAccountID(sender)
                        .setReceiverAccountID(receiver)
                        .setSerialNumber(1)
                        .setIsApproval(true)
                        .build(),
                payerIsContract));
    }

    private static List<BalanceChange> getBasicCustomFeeWithDebit() {
        final var account = new Id(0, 0, 1082);
        final var token = new Id(0, 0, 1083);
        return List.of(BalanceChange.tokenCustomFeeAdjust(account, token, -10));
    }

    private static List<BalanceChange> getRoyaltyFallbackCustomFeeWithDebit() {
        final var token = new Id(0, 0, 1083);
        final var royaltyCustomFeeWithFallbackBalanceChange =
                BalanceChange.tokenCustomFeeAdjust(payerIsContractId, token, -10);
        royaltyCustomFeeWithFallbackBalanceChange.setIncludesFallbackFee();
        return List.of(royaltyCustomFeeWithFallbackBalanceChange);
    }
}
