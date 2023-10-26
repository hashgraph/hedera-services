/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.mono.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.node.app.service.mono.state.merkle.internals.BitPackUtils.signedLowOrder32From;
import static com.hedera.node.app.service.mono.state.merkle.internals.BitPackUtils.unsignedHighOrder32From;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.createTokenInfoWrapperForNonFungibleToken;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.createTokenInfoWrapperForToken;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.invalidSerialNumberResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.invalidTokenIdResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.parentContractAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.parentContractAddressConvertedToContractId;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.payer;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.payerIdConvertedToAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenMerkleId;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.FungibleTokenInfoPrecompile.decodeGetFungibleTokenInfo;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.NonFungibleTokenInfoPrecompile.decodeGetNonFungibleTokenInfo;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenInfoPrecompile.decodeGetTokenInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.mono.config.NetworkInfo;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.FungibleTokenInfoPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.NonFungibleTokenInfoPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenInfoPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetTokenInfoPrecompilesTest {

    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame frame;

    @Mock
    private TxnAwareEvmSigsVerifier sigsVerifier;

    @Mock
    private RecordsHistorian recordsHistorian;

    @Mock
    private EncodingFacade encoder;

    @Mock
    private EvmEncodingFacade evmEncoder;

    @Mock
    private SideEffectsTracker sideEffects;

    @Mock
    private TransactionBody.Builder mockSynthBodyBuilder;

    @Mock
    private ExpirableTxnRecord.Builder mockRecordBuilder;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private HederaStackedWorldStateUpdater worldUpdater;

    @Mock
    private WorldLedgers wrappedLedgers;

    @Mock
    private ExpiringCreations creator;

    @Mock
    private ImpliedTransfersMarshal impliedTransfersMarshal;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private StateView stateView;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private InfrastructureFactory infrastructureFactory;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    private static final Address ADDRESS_A = Address.fromHexString("0xa");
    private static final Address ADDRESS_B = Address.fromHexString("0xb");
    private static final Address ADDRESS_C = Address.fromHexString("0xc");
    public static final Bytes GET_TOKEN_INFO_INPUT =
            Bytes.fromHexString("0x1f69565f000000000000000000000000000000000000000000000000000000000000000a");
    public static final Bytes GET_FUNGIBLE_TOKEN_INFO_INPUT =
            Bytes.fromHexString("0x3f28a19b000000000000000000000000000000000000000000000000000000000000000b");

    public static final Bytes GET_NON_FUNGIBLE_TOKEN_INFO_INPUT = Bytes.fromHexString(
            "0x287e1da8000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000001");
    private HTSPrecompiledContract subject;
    private MockedStatic<EntityIdUtils> entityIdUtils;
    private MockedStatic<TokenInfoPrecompile> tokenInfoPrecompile;
    private MockedStatic<FungibleTokenInfoPrecompile> fungibleTokenInfoPrecompile;
    private MockedStatic<NonFungibleTokenInfoPrecompile> nonFungibleTokenInfoPrecompile;

    // Common token properties
    private final String name = "Name";
    private final String symbol = "N";
    private final EntityId treasury = HTSTestsUtil.senderId;
    private final Address treasuryAddress = HTSTestsUtil.senderIdConvertedToAddress;
    private final String memo = "Memo";
    private final long maxSupply = 10000;
    private final long totalSupply = 20500;

    // Key properties
    private final Key tokenKey = Key.newBuilder()
            .setContractID(parentContractAddressConvertedToContractId)
            .build();
    private final List<Key> tokenKeys = new ArrayList<>();

    // Expiry properties
    private final long expiryPeriod = 10200L;
    private final long autoRenewPeriod = 500L;

    // Fungible info properties
    private final int decimals = 10;

    // Non-fungible info properties
    private final long serialNumber = 1;
    private final long creationTime = 152435353252L;
    private final ByteString metadata = ByteString.copyFrom(new byte[] {70, 73, 82, 83, 84});

    // Fee properties
    private final Address feeToken = HTSTestsUtil.tokenAddress;
    private final TokenID feeTokenId = EntityIdUtils.tokenIdFromEvmAddress(feeToken);
    private final TokenID feeTokenEntityId = HTSTestsUtil.tokenAddressConvertedToTokenId;
    private final long amount = 100L;
    private final long numerator = 1L;
    private final long denominator = 2L;
    private final long minimumAmount = 10_000L;
    private final long maximumAmount = 400_000_000L;

    // Info objects
    private final NftID nftID = NftID.newBuilder()
            .setTokenID(tokenMerkleId)
            .setSerialNumber(serialNumber)
            .build();
    private TokenInfo.Builder tokenInfo;
    private TokenNftInfo nonFungibleTokenInfo;

    private EvmTokenInfo evmTokenInfo;
    private EvmNftInfo evmNftInfo;

    @BeforeEach
    void setUp() {
        final PrecompilePricingUtils precompilePricingUtils = new PrecompilePricingUtils(
                assetLoader, exchange, () -> feeCalculator, resourceCosts, stateView, accessorFactory);

        entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(tokenMerkleId))
                .thenReturn(HTSTestsUtil.tokenMerkleAddress);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(treasury)).thenReturn(treasuryAddress);
        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(HTSTestsUtil.payerId))
                .thenReturn(HTSTestsUtil.payerIdConvertedToAddress);
        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(HTSTestsUtil.senderId))
                .thenReturn(HTSTestsUtil.senderIdConvertedToAddress);
        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(feeTokenEntityId))
                .thenReturn(feeToken);

        tokenKeys.add(tokenKey);
        tokenInfo = createTokenInfoWithSingleKey(1, false);
        evmTokenInfo = new EvmTokenInfo(
                fromString("0x03").toByteArray(),
                1,
                false,
                "FT",
                "NAME",
                "MEMO",
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005cc")),
                1L,
                1000L,
                0,
                0L);

        evmNftInfo = new EvmNftInfo(
                serialNumber,
                payerIdConvertedToAddress,
                creationTime,
                metadata.toByteArray(),
                EntityIdUtils.asTypedEvmAddress(sender),
                evmTokenInfo.getLedgerId());
        nonFungibleTokenInfo = TokenNftInfo.newBuilder()
                .setLedgerId(fromString("0x03"))
                .setNftID(NftID.newBuilder()
                        .setTokenID(tokenMerkleId)
                        .setSerialNumber(serialNumber)
                        .build())
                .setAccountID(payer)
                .setCreationTime(
                        new RichInstant(unsignedHighOrder32From(creationTime), signedLowOrder32From(creationTime))
                                .toGrpc())
                .setMetadata(metadata)
                .setSpenderId(sender)
                .build();

        subject = new HTSPrecompiledContract(
                dynamicProperties,
                gasCalculator,
                recordsHistorian,
                sigsVerifier,
                encoder,
                evmEncoder,
                syntheticTxnFactory,
                creator,
                () -> feeCalculator,
                stateView,
                precompilePricingUtils,
                infrastructureFactory,
                evmHTSPrecompiledContract);

        tokenInfoPrecompile = Mockito.mockStatic(TokenInfoPrecompile.class);
        fungibleTokenInfoPrecompile = Mockito.mockStatic(FungibleTokenInfoPrecompile.class);
        nonFungibleTokenInfoPrecompile = Mockito.mockStatic(NonFungibleTokenInfoPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        entityIdUtils.close();
        tokenInfoPrecompile.close();
        fungibleTokenInfoPrecompile.close();
        nonFungibleTokenInfoPrecompile.close();
    }

    @Test
    void getTokenInfoWorks() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)), EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        tokenInfoPrecompile.when(() -> decodeGetTokenInfo(pretendArguments)).thenReturn(tokenInfoWrapper);

        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));
        given(evmEncoder.encodeGetTokenInfo(evmTokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoWorksWithDelegatableContractKey() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)), EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        tokenInfoPrecompile.when(() -> decodeGetTokenInfo(pretendArguments)).thenReturn(tokenInfoWrapper);

        tokenInfo = createTokenInfoWithSingleKey(0, false);
        final var supplyKey = Key.newBuilder()
                .setDelegatableContractId(parentContractAddressConvertedToContractId)
                .build();
        tokenInfo.setSupplyKey(supplyKey);

        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));

        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(parentContractAddressConvertedToContractId))
                .thenReturn(parentContractAddress);

        given(evmEncoder.encodeGetTokenInfo(evmTokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoWithAllKeyTypesWorks() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)), EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        tokenInfoPrecompile.when(() -> decodeGetTokenInfo(pretendArguments)).thenReturn(tokenInfoWrapper);

        tokenInfo = createTokenInfoWithAllKeys();
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));

        given(evmEncoder.encodeGetTokenInfo(evmTokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getFungibleTokenInfoWorks() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_FUNGIBLE_TOKEN_INFO)),
                EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        fungibleTokenInfoPrecompile
                .when(() -> decodeGetFungibleTokenInfo(pretendArguments))
                .thenReturn(tokenInfoWrapper);

        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));
        given(evmEncoder.encodeGetFungibleTokenInfo(evmTokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getNonFungibleTokenInfoWorks() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForNonFungibleToken(tokenMerkleId, serialNumber);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO)),
                EntityIdUtils.asTypedEvmAddress(tokenMerkleId),
                Bytes.wrap(new byte[] {Long.valueOf(serialNumber).byteValue()}));
        nonFungibleTokenInfoPrecompile
                .when(() -> decodeGetNonFungibleTokenInfo(pretendArguments))
                .thenReturn(tokenInfoWrapper);

        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));
        given(wrappedLedgers.evmNftInfo(
                        NftID.newBuilder()
                                .setSerialNumber(serialNumber)
                                .setTokenID(tokenMerkleId)
                                .build(),
                        networkInfo.ledgerId()))
                .willReturn(Optional.of(evmNftInfo));
        given(evmEncoder.encodeGetNonFungibleTokenInfo(evmTokenInfo, evmNftInfo))
                .willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoWithFixedFeeWithDenominationWorks() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)), EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        tokenInfoPrecompile.when(() -> decodeGetTokenInfo(pretendArguments)).thenReturn(tokenInfoWrapper);

        final var fixedFee = getFixedFeeWithDenomination();
        tokenInfo.addAllCustomFees(List.of(fixedFee));
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));

        given(evmEncoder.encodeGetTokenInfo(evmTokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoWithFixedFeeWithoutDenominationWorks() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)), EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        tokenInfoPrecompile.when(() -> decodeGetTokenInfo(pretendArguments)).thenReturn(tokenInfoWrapper);

        final var fixedFee = getFixedFeeWithoutDenomination();
        tokenInfo.addAllCustomFees(List.of(fixedFee));
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));

        given(evmEncoder.encodeGetTokenInfo(evmTokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoWithFractionalFeeWorks() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)), EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        tokenInfoPrecompile.when(() -> decodeGetTokenInfo(pretendArguments)).thenReturn(tokenInfoWrapper);

        final var fractionalFee = getFractionalFee();
        tokenInfo.addAllCustomFees(List.of(fractionalFee));
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));

        given(evmEncoder.encodeGetTokenInfo(evmTokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getNonFungibleTokenInfoWithRoyaltyFeeWithFallbackFeeWorks() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForNonFungibleToken(tokenMerkleId, serialNumber);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO)),
                EntityIdUtils.asTypedEvmAddress(tokenMerkleId),
                Bytes.wrap(new byte[] {Long.valueOf(serialNumber).byteValue()}));
        nonFungibleTokenInfoPrecompile
                .when(() -> decodeGetNonFungibleTokenInfo(pretendArguments))
                .thenReturn(tokenInfoWrapper);

        final var royaltyFee = getRoyaltyFee(true);
        tokenInfo.addAllCustomFees(List.of(royaltyFee));
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));
        given(wrappedLedgers.evmNftInfo(
                        NftID.newBuilder()
                                .setTokenID(tokenMerkleId)
                                .setSerialNumber(serialNumber)
                                .build(),
                        networkInfo.ledgerId()))
                .willReturn(Optional.of(evmNftInfo));

        given(evmEncoder.encodeGetNonFungibleTokenInfo(evmTokenInfo, evmNftInfo))
                .willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getNonFungibleTokenInfoWithRoyaltyFeeWithoutFallbackFeeWorks() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForNonFungibleToken(tokenMerkleId, serialNumber);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO)),
                EntityIdUtils.asTypedEvmAddress(tokenMerkleId),
                Bytes.wrap(new byte[] {Long.valueOf(serialNumber).byteValue()}));
        nonFungibleTokenInfoPrecompile
                .when(() -> decodeGetNonFungibleTokenInfo(pretendArguments))
                .thenReturn(tokenInfoWrapper);

        final var royaltyFee = getRoyaltyFee(false);
        tokenInfo.addAllCustomFees(List.of(royaltyFee));
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));
        given(wrappedLedgers.evmNftInfo(
                        NftID.newBuilder()
                                .setTokenID(tokenMerkleId)
                                .setSerialNumber(serialNumber)
                                .build(),
                        networkInfo.ledgerId()))
                .willReturn(Optional.of(evmNftInfo));

        given(evmEncoder.encodeGetNonFungibleTokenInfo(evmTokenInfo, evmNftInfo))
                .willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoOfMissingTokenFails() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)), EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        tokenInfoPrecompile.when(() -> decodeGetTokenInfo(pretendArguments)).thenReturn(tokenInfoWrapper);

        givenMinimalContextForInvalidTokenIdCall(pretendArguments);
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(invalidTokenIdResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getFungibleTokenInfoOfMissingTokenFails() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_FUNGIBLE_TOKEN_INFO)),
                EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        fungibleTokenInfoPrecompile
                .when(() -> decodeGetFungibleTokenInfo(pretendArguments))
                .thenReturn(tokenInfoWrapper);

        givenMinimalContextForInvalidTokenIdCall(pretendArguments);
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(invalidTokenIdResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getNonFungibleTokenInfoOfMissingSerialNumberFails() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForNonFungibleToken(tokenMerkleId, serialNumber);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO)),
                EntityIdUtils.asTypedEvmAddress(tokenMerkleId),
                Bytes.wrap(new byte[] {Long.valueOf(serialNumber).byteValue()}));
        nonFungibleTokenInfoPrecompile
                .when(() -> decodeGetNonFungibleTokenInfo(pretendArguments))
                .thenReturn(tokenInfoWrapper);

        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));
        given(wrappedLedgers.evmNftInfo(nftID, networkInfo.ledgerId())).willReturn(Optional.empty());

        givenMinimalContextForInvalidNftSerialNumberCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(invalidSerialNumberResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoOfDeletedTokenWorks() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)), EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        tokenInfoPrecompile.when(() -> decodeGetTokenInfo(pretendArguments)).thenReturn(tokenInfoWrapper);

        tokenInfo = createTokenInfoWithSingleKey(1, true);
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));

        given(evmEncoder.encodeGetTokenInfo(evmTokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getFungibleTokenInfoOfDeletedTokenWorks() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_FUNGIBLE_TOKEN_INFO)),
                EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        fungibleTokenInfoPrecompile
                .when(() -> decodeGetFungibleTokenInfo(pretendArguments))
                .thenReturn(tokenInfoWrapper);

        tokenInfo = createTokenInfoWithSingleKey(1, true);
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));

        given(evmEncoder.encodeGetFungibleTokenInfo(evmTokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getNonFungibleTokenInfoOfDeletedTokenWorks() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenInfoWrapper = createTokenInfoWrapperForNonFungibleToken(tokenMerkleId, serialNumber);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO)),
                EntityIdUtils.asTypedEvmAddress(tokenMerkleId),
                Bytes.wrap(new byte[] {Long.valueOf(serialNumber).byteValue()}));
        nonFungibleTokenInfoPrecompile
                .when(() -> decodeGetNonFungibleTokenInfo(pretendArguments))
                .thenReturn(tokenInfoWrapper);

        tokenInfo = createTokenInfoWithSingleKey(1, true);
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));
        given(wrappedLedgers.evmNftInfo(nftID, networkInfo.ledgerId())).willReturn(Optional.of(evmNftInfo));

        given(evmEncoder.encodeGetNonFungibleTokenInfo(evmTokenInfo, evmNftInfo))
                .willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void decodeGetTokenInfoAsExpected() {
        tokenInfoPrecompile.when(() -> decodeGetTokenInfo(GET_TOKEN_INFO_INPUT)).thenCallRealMethod();
        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(ADDRESS_A.toArray()))
                .thenCallRealMethod();
        final var decodedInput = decodeGetTokenInfo(GET_TOKEN_INFO_INPUT);

        assertEquals(TokenID.newBuilder().setTokenNum(10).build(), decodedInput.token());
        assertEquals(-1, decodedInput.serialNumber());
    }

    @Test
    void decodeGetFungibleTokenInfoAsExpected() {
        fungibleTokenInfoPrecompile
                .when(() -> decodeGetFungibleTokenInfo(GET_FUNGIBLE_TOKEN_INFO_INPUT))
                .thenCallRealMethod();
        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(ADDRESS_B.toArray()))
                .thenCallRealMethod();
        final var decodedInput = decodeGetFungibleTokenInfo(GET_FUNGIBLE_TOKEN_INFO_INPUT);

        assertEquals(TokenID.newBuilder().setTokenNum(11).build(), decodedInput.token());
        assertEquals(-1, decodedInput.serialNumber());
    }

    @Test
    void decodeGetNonFungibleTokenInfoAsExpected() {
        nonFungibleTokenInfoPrecompile
                .when(() -> decodeGetNonFungibleTokenInfo(GET_NON_FUNGIBLE_TOKEN_INFO_INPUT))
                .thenCallRealMethod();
        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(ADDRESS_C.toArray()))
                .thenCallRealMethod();
        final var decodedInput = decodeGetNonFungibleTokenInfo(GET_NON_FUNGIBLE_TOKEN_INFO_INPUT);

        assertEquals(TokenID.newBuilder().setTokenNum(12).build(), decodedInput.token());
        assertEquals(1, decodedInput.serialNumber());
    }

    private void givenReadOnlyFeeSchedule() {
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockFeeObject.nodeFee()).willReturn(1L);
        given(mockFeeObject.networkFee()).willReturn(1L);
        given(mockFeeObject.serviceFee()).willReturn(1L);
    }

    private void givenMinimalFrameContext() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(100_000L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(frame.getSenderAddress()).willReturn(senderAddress);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }

    private void givenMinimalContextForSuccessfulCall(final Bytes pretendArguments) {
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
    }

    private void givenMinimalContextForInvalidNftSerialNumberCall(final Bytes pretendArguments) {
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createUnsuccessfulSyntheticRecord(ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER))
                .willReturn(mockRecordBuilder);
    }

    private void givenMinimalContextForInvalidTokenIdCall(final Bytes pretendArguments) {
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createUnsuccessfulSyntheticRecord(ResponseCodeEnum.INVALID_TOKEN_ID))
                .willReturn(mockRecordBuilder);
    }

    private CustomFee getFixedFeeWithDenomination() {
        final var fixedFee = FixedFee.newBuilder()
                .setAmount(amount)
                .setDenominatingTokenId(feeTokenEntityId)
                .build();
        return CustomFee.newBuilder()
                .setFixedFee(fixedFee)
                .setFeeCollectorAccountId(payer)
                .build();
    }

    private CustomFee getFixedFeeWithoutDenomination() {
        final var fixedFee = FixedFee.newBuilder().setAmount(amount).build();
        return CustomFee.newBuilder()
                .setFixedFee(fixedFee)
                .setFeeCollectorAccountId(payer)
                .build();
    }

    private CustomFee getFractionalFee() {
        final var fraction = Fraction.newBuilder()
                .setNumerator(numerator)
                .setDenominator(denominator)
                .build();
        final var fractionalFee = FractionalFee.newBuilder()
                .setFractionalAmount(fraction)
                .setMinimumAmount(minimumAmount)
                .setMaximumAmount(maximumAmount)
                .build();
        return CustomFee.newBuilder()
                .setFractionalFee(fractionalFee)
                .setFeeCollectorAccountId(payer)
                .build();
    }

    private CustomFee getRoyaltyFee(final boolean hasFallbackFee) {
        final RoyaltyFee royaltyFee;
        final var fraction = Fraction.newBuilder()
                .setNumerator(numerator)
                .setDenominator(denominator)
                .build();
        if (hasFallbackFee) {

            final var fallbackFee = FixedFee.newBuilder()
                    .setAmount(amount)
                    .setDenominatingTokenId(feeTokenId)
                    .build();
            royaltyFee = RoyaltyFee.newBuilder()
                    .setExchangeValueFraction(fraction)
                    .setFallbackFee(fallbackFee)
                    .build();
        } else {
            royaltyFee =
                    RoyaltyFee.newBuilder().setExchangeValueFraction(fraction).build();
        }

        return CustomFee.newBuilder().setRoyaltyFee(royaltyFee).build();
    }

    private TokenInfo.Builder createTokenInfoWithSingleKey(final int tokenSupplyType, final boolean isDeleted) {
        final var tokenInfoBuilder = TokenInfo.newBuilder()
                .setLedgerId(fromString("0x03"))
                .setSupplyTypeValue(tokenSupplyType)
                .setExpiry(new RichInstant(unsignedHighOrder32From(expiryPeriod), signedLowOrder32From(expiryPeriod))
                        .toGrpc())
                .setAutoRenewAccount(sender)
                .setAutoRenewPeriod(
                        Duration.newBuilder().setSeconds(autoRenewPeriod).build())
                .setTokenId(tokenMerkleId)
                .setDeleted(isDeleted)
                .setSymbol(symbol)
                .setName(name)
                .setMemo(memo)
                .setDecimals(decimals)
                .setTreasury(sender)
                .setTotalSupply(totalSupply)
                .setMaxSupply(maxSupply);

        tokenInfoBuilder.setAdminKey(tokenKey);
        return tokenInfoBuilder;
    }

    private TokenInfo.Builder createTokenInfoWithAllKeys() {
        final var tokenInfoBuilder = TokenInfo.newBuilder()
                .setLedgerId(fromString("0x03"))
                .setSupplyTypeValue(1)
                .setTokenId(tokenMerkleId)
                .setDeleted(false)
                .setSymbol(symbol)
                .setName(name)
                .setMemo(memo)
                .setTreasury(sender)
                .setTotalSupply(totalSupply)
                .setMaxSupply(maxSupply);

        tokenInfoBuilder.setAdminKey(tokenKey);
        tokenInfoBuilder.setKycKey(tokenKey);
        tokenInfoBuilder.setWipeKey(tokenKey);
        tokenInfoBuilder.setPauseKey(tokenKey);
        tokenInfoBuilder.setFeeScheduleKey(tokenKey);
        tokenInfoBuilder.setFreezeKey(tokenKey);
        tokenInfoBuilder.setSupplyKey(tokenKey);

        return tokenInfoBuilder;
    }

    private ByteString fromString(final String value) {
        return ByteString.copyFrom(Bytes.fromHexString(value).toArray());
    }
}
