/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.service.mono.store.contracts.precompile.utils.KeyActivationUtils.validateKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.CustomFeeType;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfers;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMeta;
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
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.swirlds.fchashmap.FCHashMap;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferPrecompilesParameterizedTest {
    @Mock
    private HederaTokenStore hederaTokenStore;

    @Mock
    private MessageFrame frame;

    @Mock(strictness = LENIENT)
    private TxnAwareEvmSigsVerifier sigsVerifier;

    @Mock
    private TransferLogic transferLogic;

    @Mock
    private SideEffectsTracker sideEffects;

    private final SyntheticTxnFactory syntheticTxnFactory = new SyntheticTxnFactory(null);

    @Mock
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

    private PrecompilePricingUtils precompilePricingUtils;
    private TransferPrecompile subject;
    private MockedStatic<KeyActivationUtils> keyActivationUtils;

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
    void setUp() {
        precompilePricingUtils = new PrecompilePricingUtils(
                assetLoader, exchange, () -> feeCalculator, resourceCosts, stateView, accessorFactory);

        keyActivationUtils = Mockito.mockStatic(KeyActivationUtils.class);
    }

    @AfterEach
    void closeMocks() {
        keyActivationUtils.close();
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
            final boolean passesKeyActivation,
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
        // This test does not cover key activation. Rather a parameter is passed in to indicate pass/fail.
        keyActivationUtils
                .when(() -> validateKey(any(), any(), any(), any(), any(), eq(CryptoTransfer)))
                .thenReturn(passesKeyActivation);

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
                        getBasicFungibleChanges(),
                        allTypes,
                        true,
                        false),
                Arguments.of(
                        "basic transferToken all custom fee types allowed ",
                        true,
                        true,
                        ABI_ID_TRANSFER_TOKEN,
                        TRANSFER_TOKEN_INPUT,
                        getBasicFungibleChanges(),
                        noType,
                        true,
                        false),
                Arguments.of(
                        "basic transferTokens",
                        true,
                        true,
                        ABI_ID_TRANSFER_TOKENS,
                        POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT,
                        getBasicFungibleChanges(),
                        allTypes,
                        true,
                        false),
                Arguments.of(
                        "basic transferTokens with negative amount",
                        true,
                        true,
                        ABI_ID_TRANSFER_TOKENS,
                        POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT,
                        getBasicFungibleChanges(),
                        allTypes,
                        true,
                        false),
                Arguments.of(
                        "basic transferNFT",
                        true,
                        true,
                        ABI_ID_TRANSFER_NFT,
                        TRANSFER_NFT_INPUT,
                        getBasicNFTChange(),
                        allTypes,
                        true,
                        false),
                Arguments.of(
                        "basic transferNFTs",
                        true,
                        true,
                        ABI_ID_TRANSFER_NFTS,
                        TRANSFER_NFTS_INPUT,
                        getBasicNFTChange(),
                        allTypes,
                        true,
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
                        true,
                        false),
                Arguments.of(
                        "royalty with fallback fees with fixed fee type disallowed",
                        true,
                        true,
                        ABI_ID_TRANSFER_NFTS,
                        TRANSFER_NFTS_INPUT,
                        getRoyaltyFallbackCustomFeeWithDebit(),
                        fixedFeeType,
                        true,
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
                        false,
                        false),
                Arguments.of(
                        "transferToken with approval and failed key activation",
                        true,
                        true,
                        ABI_ID_TRANSFER_TOKEN,
                        TRANSFER_TOKEN_INPUT,
                        getApprovalFungibleChanges(),
                        allTypes,
                        false,
                        false),
                // Changes with fallback to approval should pass even if the key activation fails
                Arguments.of(
                        "transferNFT with failed key activation but can fallback to approval",
                        true,
                        true,
                        ABI_ID_TRANSFER_NFT,
                        TRANSFER_NFT_INPUT,
                        getBasicNFTChange(),
                        allTypes,
                        false,
                        true),
                Arguments.of(
                        "transferToken with no approval but can fallback to approval",
                        true,
                        true,
                        ABI_ID_TRANSFER_TOKEN,
                        TRANSFER_TOKEN_INPUT,
                        getFungibleDebitChanges(),
                        allTypes,
                        false,
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
                        true,
                        false),
                Arguments.of(
                        "royalty with fallback fees with royalty fallback fee type disallowed",
                        true,
                        false,
                        ABI_ID_TRANSFER_NFTS,
                        TRANSFER_NFTS_INPUT,
                        getRoyaltyFallbackCustomFeeWithDebit(),
                        royaltyFallbackFeeType,
                        true,
                        false),
                // Fail due to failed key activation
                Arguments.of(
                        "transferToken with failed key activation",
                        true,
                        false,
                        ABI_ID_TRANSFER_TOKEN,
                        TRANSFER_TOKEN_INPUT,
                        getBasicFungibleChanges(),
                        allTypes,
                        false,
                        false),
                Arguments.of(
                        "transferTokens with failed key activation",
                        true,
                        false,
                        ABI_ID_TRANSFER_TOKENS,
                        POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT,
                        getBasicFungibleChanges(),
                        allTypes,
                        false,
                        false),
                Arguments.of(
                        "transferTokens with failed key activation and negative amount",
                        true,
                        false,
                        ABI_ID_TRANSFER_TOKENS,
                        POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT,
                        getBasicFungibleChanges(),
                        allTypes,
                        false,
                        false),
                Arguments.of(
                        "transferNFT with failed key activation",
                        true,
                        false,
                        ABI_ID_TRANSFER_NFT,
                        TRANSFER_NFT_INPUT,
                        getBasicNFTChange(),
                        allTypes,
                        false,
                        false),
                Arguments.of(
                        "transferNFTs with failed key activation",
                        true,
                        false,
                        ABI_ID_TRANSFER_NFTS,
                        TRANSFER_NFTS_INPUT,
                        getBasicNFTChange(),
                        allTypes,
                        false,
                        false),
                Arguments.of(
                        "negative amount transferToken should fail when processing body",
                        false,
                        true,
                        ABI_ID_TRANSFER_TOKEN,
                        NEGATIVE_AMOUNT_TRANSFER_TOKEN_INPUT,
                        getBasicFungibleChanges(),
                        allTypes,
                        true,
                        false));
    }

    private static List<BalanceChange> getBasicFungibleChanges() {
        final AccountID payer = asAccount("0.0.1234");
        final AccountID nullAccount = asAccount("0.0.0");
        final AccountID account = asAccount("0.0.1077");
        final var token = new Id(0, 0, 1078);
        return List.of(
                BalanceChange.changingFtUnits(
                        token,
                        token.asGrpcToken(),
                        AccountAmount.newBuilder()
                                .setAmount(20)
                                .setAccountID(nullAccount)
                                .build(),
                        payer),
                BalanceChange.changingFtUnits(
                        token,
                        token.asGrpcToken(),
                        AccountAmount.newBuilder()
                                .setAmount(-20)
                                .setAccountID(account)
                                .build(),
                        payer));
    }

    private static List<BalanceChange> getApprovalFungibleChanges() {
        final AccountID payer = asAccount("0.0.1234");
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
                payer));
    }

    private static List<BalanceChange> getFungibleDebitChanges() {
        final AccountID payer = asAccount("0.0.1234");
        final AccountID account = asAccount("0.0.1077");
        final var token = new Id(0, 0, 1078);
        return List.of(BalanceChange.changingFtUnits(
                token,
                token.asGrpcToken(),
                AccountAmount.newBuilder().setAmount(-20).setAccountID(account).build(),
                payer));
    }

    private static List<BalanceChange> getBasicNFTChange() {
        final AccountID payer = asAccount("0.0.1234");
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
                        .build(),
                payer));
    }

    private static List<BalanceChange> getApprovalNFTChange() {
        final AccountID payer = asAccount("0.0.1234");
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
                payer));
    }

    private static List<BalanceChange> getBasicCustomFeeWithDebit() {
        final var account = new Id(0, 0, 1082);
        final var token = new Id(0, 0, 1083);
        return List.of(BalanceChange.tokenCustomFeeAdjust(account, token, -10));
    }

    private static List<BalanceChange> getRoyaltyFallbackCustomFeeWithDebit() {
        final var account = new Id(0, 0, 1082);
        final var token = new Id(0, 0, 1083);
        final var royaltyCustomFeeWithFallbackBalanceChange = BalanceChange.tokenCustomFeeAdjust(account, token, -10);
        royaltyCustomFeeWithFallbackBalanceChange.setIncludesFallbackFee();
        return List.of(royaltyCustomFeeWithFallbackBalanceChange);
    }
}
