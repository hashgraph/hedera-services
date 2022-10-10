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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ALLOWANCE;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_APPROVE;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_APPROVE_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_ALLOWANCE;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_APPROVE;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_DECIMALS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_GET_APPROVED;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_OWNER_OF_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_SYMBOL;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOTAL_SUPPLY_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TRANSFER;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TRANSFER_FROM;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_APPROVED;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_IS_APPROVED_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_SET_APPROVAL_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_FROM;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_FROM_NFT;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.AMOUNT;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.accountId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.failResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.invalidFullPrefix;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.ownerEntity;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.precompiledContract;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.receiver;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.serialNumber;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.token;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenTransferChanges;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.BalanceOfWrapper;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.TokenAllowanceWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenTransferWrapper;
import com.hedera.services.store.contracts.precompile.impl.AllowancePrecompile;
import com.hedera.services.store.contracts.precompile.impl.ApprovePrecompile;
import com.hedera.services.store.contracts.precompile.impl.BalanceOfPrecompile;
import com.hedera.services.store.contracts.precompile.impl.ERCTransferPrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.crypto.ApproveAllowanceLogic;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ERC20PrecompilesTest {
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private TransactionBody.Builder mockSynthBodyBuilder;
    @Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nfts;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
            tokenRels;

    @Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock private ExpiringCreations creator;
    @Mock private ImpliedTransfersMarshal impliedTransfersMarshal;
    @Mock private FeeCalculator feeCalculator;
    @Mock private StateView stateView;
    @Mock private CryptoTransferTransactionBody cryptoTransferTransactionBody;
    @Mock private ImpliedTransfersMeta impliedTransfersMeta;
    @Mock private ImpliedTransfers impliedTransfers;
    @Mock private TransferLogic transferLogic;
    @Mock private HederaTokenStore hederaTokenStore;
    @Mock private FeeObject mockFeeObject;
    @Mock private ContractAliases aliases;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private BlockValues blockValues;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private ApproveAllowanceChecks allowanceChecks;
    @Mock private DeleteAllowanceChecks deleteAllowanceChecks;
    @Mock private CryptoApproveAllowanceTransactionBody cryptoApproveAllowanceTransactionBody;
    @Mock private AccountStore accountStore;
    @Mock private TypedTokenStore tokenStore;
    @Mock private ApproveAllowanceLogic approveAllowanceLogic;
    @Mock private AssetsLoader assetLoader;
    @Mock private HbarCentExchange exchange;
    @Mock private ExchangeRate exchangeRate;
    @Mock private AccessorFactory accessorFactory;

    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;

    private HTSPrecompiledContract subject;
    private MockedStatic<EntityIdUtils> entityIdUtils;
    private MockedStatic<ERCTransferPrecompile> ercTransferPrecompile;
    private MockedStatic<AllowancePrecompile> allowancePrecompile;
    private MockedStatic<BalanceOfPrecompile> balanceOfPrecompile;
    private MockedStatic<ApprovePrecompile> approvePrecompile;

    @BeforeEach
    void setUp() throws IOException {
        Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        Map<SubType, BigDecimal> type = new HashMap<>();
        type.put(SubType.TOKEN_FUNGIBLE_COMMON, BigDecimal.valueOf(0));
        type.put(SubType.TOKEN_NON_FUNGIBLE_UNIQUE, BigDecimal.valueOf(0));
        canonicalPrices.put(HederaFunctionality.CryptoTransfer, type);
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(
                        assetLoader,
                        exchange,
                        () -> feeCalculator,
                        resourceCosts,
                        stateView,
                        accessorFactory);
        subject =
                new HTSPrecompiledContract(
                        dynamicProperties,
                        gasCalculator,
                        recordsHistorian,
                        sigsVerifier,
                        encoder,
                        syntheticTxnFactory,
                        creator,
                        impliedTransfersMarshal,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils
                .when(() -> EntityIdUtils.accountIdFromEvmAddress(senderAddress))
                .thenReturn(sender);
        entityIdUtils
                .when(
                        () ->
                                EntityIdUtils.contractIdFromEvmAddress(
                                        Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS)
                                                .toArray()))
                .thenReturn(precompiledContract);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(sender)).thenReturn(senderAddress);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(token)).thenReturn(tokenAddress);
        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(receiver))
                .thenReturn(recipientAddress);
        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray()))
                .thenReturn(token);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        ercTransferPrecompile = Mockito.mockStatic(ERCTransferPrecompile.class);
        allowancePrecompile = Mockito.mockStatic(AllowancePrecompile.class);
        balanceOfPrecompile = Mockito.mockStatic(BalanceOfPrecompile.class);
        approvePrecompile = Mockito.mockStatic(ApprovePrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        entityIdUtils.close();
        ercTransferPrecompile.close();
        allowancePrecompile.close();
        balanceOfPrecompile.close();
        approvePrecompile.close();
    }

    @Test
    void ercAllowanceDisabled() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);

        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        Bytes pretendArgumentsApprove =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                        fungibleTokenAddr,
                        Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsApprove, a -> a));

        Bytes pretendArgumentsTransferFrom =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                        fungibleTokenAddr,
                        Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER_FROM)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsTransferFrom, a -> a));

        Bytes pretendArgumentsAllowance =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                        fungibleTokenAddr,
                        Bytes.of(Integers.toBytes(ABI_ID_ERC_ALLOWANCE)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsAllowance, a -> a));

        Bytes pretendArgumentsApproveForAll =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                        fungibleTokenAddr,
                        Bytes.of(Integers.toBytes(ABI_ID_ERC_SET_APPROVAL_FOR_ALL)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsApproveForAll, a -> a));

        Bytes pretendArgumentsGetApproved =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                        fungibleTokenAddr,
                        Bytes.of(Integers.toBytes(ABI_ID_ERC_GET_APPROVED)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsGetApproved, a -> a));

        Bytes pretendArgumentsApprovedForAll =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                        fungibleTokenAddr,
                        Bytes.of(Integers.toBytes(ABI_ID_ERC_IS_APPROVED_FOR_ALL)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsApprovedForAll, a -> a));
    }

    @Test
    void hapiAllowanceDisabled() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);

        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        Bytes pretendArgumentsApprove =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_APPROVE)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsApprove, a -> a));

        Bytes pretendArgumentsAllowance =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_ALLOWANCE)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsAllowance, a -> a));

        Bytes pretendArgumentsApproveForAll =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_SET_APPROVAL_FOR_ALL)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsApproveForAll, a -> a));

        Bytes pretendArgumentsGetApproved =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_GET_APPROVED)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsGetApproved, a -> a));

        Bytes pretendArgumentsApprovedForAll =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_IS_APPROVED_FOR_ALL)));

        // when:
        subject.prepareFields(frame);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArgumentsApprovedForAll, a -> a));
    }

    @Test
    void invalidNestedFunctionSelector() {
        Bytes nestedPretendArguments = Bytes.of(0, 0, 0, 0);
        Bytes pretendArguments =
                givenMinimalFrameContextWithoutParentUpdater(nestedPretendArguments);

        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        final var result = subject.computePrecompile(pretendArguments, frame);
        assertNull(result.getOutput());
    }

    @Test
    void gasCalculationForReadOnlyMethod() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_NAME));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(encoder.encodeName(any())).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(frame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(TEST_CONSENSUS_TIME);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.compute(pretendArguments, frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void gasCalculationForModifyingMethod() throws InvalidProtocolBufferException {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(TOKEN_TRANSFER_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(anyBoolean(), any(), any(), any())).willReturn(true);
        given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(anyBoolean(), any(), any(), any()))
                .willReturn(true, true);

        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        ercTransferPrecompile
                .when(
                        () ->
                                ERCTransferPrecompile.decodeERCTransfer(
                                        eq(nestedPretendArguments), any(), any(), any()))
                .thenReturn(Collections.singletonList(TOKEN_TRANSFER_WRAPPER));
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(encoder.encodeEcFungibleTransfer(true)).willReturn(successResult);
        given(frame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(TEST_CONSENSUS_TIME);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computePrecompile(pretendArguments, frame);

        // then:
        assertEquals(successResult, result.getOutput());
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void name() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_NAME));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(encoder.encodeName(any())).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(dynamicProperties.shouldExportPrecompileResults()).willReturn(true);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        ArgumentCaptor<EvmFnResult> captor = ArgumentCaptor.forClass(EvmFnResult.class);
        verify(mockRecordBuilder).setContractCallResult(captor.capture());
        assertEquals(0L, captor.getValue().getGas());
        assertEquals(0L, captor.getValue().getAmount());
        assertEquals(EvmFnResult.EMPTY, captor.getValue().getFunctionParameters());
    }

    @Test
    void symbol() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_SYMBOL));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(encoder.encodeSymbol(any())).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void decimals() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_DECIMALS));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(wrappedLedgers.decimalsOf(token)).willReturn(10);
        given(encoder.encodeDecimals(10)).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void totalSupply() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TOTAL_SUPPLY_TOKEN));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(wrappedLedgers.totalSupplyOf(token)).willReturn(10L);
        given(encoder.encodeTotalSupply(10L)).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void ercAllowance() {
        TreeMap<FcTokenAllowanceId, Long> allowances = new TreeMap<>();
        allowances.put(
                FcTokenAllowanceId.from(
                        EntityNum.fromLong(token.getTokenNum()),
                        EntityNum.fromLong(receiver.getAccountNum())),
                10L);

        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_ALLOWANCE));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(accounts.contains(any())).willReturn(true);
        allowancePrecompile
                .when(
                        () ->
                                AllowancePrecompile.decodeTokenAllowance(
                                        eq(nestedPretendArguments), any(), any()))
                .thenReturn(ALLOWANCE_WRAPPER);
        given(accounts.get(any(), any())).willReturn(allowances);
        given(encoder.encodeAllowance(10L)).willReturn(successResult);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void hapiAllowance() {
        TreeMap<FcTokenAllowanceId, Long> alowances = new TreeMap<>();
        alowances.put(
                FcTokenAllowanceId.from(
                        EntityNum.fromLong(token.getTokenNum()),
                        EntityNum.fromLong(receiver.getAccountNum())),
                10L);

        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ALLOWANCE));
        givenMinimalFrameContext(pretendArguments);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(accounts.contains(any())).willReturn(true);
        allowancePrecompile
                .when(
                        () ->
                                AllowancePrecompile.decodeTokenAllowance(
                                        eq(pretendArguments), any(), any()))
                .thenReturn(ALLOWANCE_WRAPPER);
        given(accounts.get(any(), any())).willReturn(alowances);
        given(encoder.encodeAllowance(SUCCESS.getNumber(), 10L)).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void balanceOf() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_BALANCE_OF_TOKEN));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        balanceOfPrecompile
                .when(() -> BalanceOfPrecompile.decodeBalanceOf(eq(nestedPretendArguments), any()))
                .thenReturn(BALANCE_OF_WRAPPER);
        given(wrappedLedgers.balanceOf(any(), any())).willReturn(10L);
        given(encoder.encodeBalance(10L)).willReturn(successResult);

        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray()))
                .thenReturn(token);
        entityIdUtils
                .when(
                        () ->
                                EntityIdUtils.contractIdFromEvmAddress(
                                        Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS)
                                                .toArray()))
                .thenReturn(precompiledContract);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void allowanceValidation() {
        givenPricingUtilsContext();

        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(syntheticTxnFactory.createFungibleApproval(APPROVE_WRAPPER))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);

        approvePrecompile
                .when(
                        () ->
                                ApprovePrecompile.decodeTokenApprove(
                                        eq(nestedPretendArguments),
                                        eq(token),
                                        eq(true),
                                        any(),
                                        any()))
                .thenReturn(APPROVE_WRAPPER);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(failResult, result);
    }

    @Test
    void ercApprove() {
        List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        List<TokenAllowance> tokenAllowances = new ArrayList<>();
        List<NftAllowance> nftAllowances = new ArrayList<>();

        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(syntheticTxnFactory.createFungibleApproval(APPROVE_WRAPPER))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance())
                .willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore))
                .willReturn(approveAllowanceLogic);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);

        given(
                        allowanceChecks.allowancesValidation(
                                cryptoAllowances,
                                tokenAllowances,
                                nftAllowances,
                                new Account(accountId),
                                stateView))
                .willReturn(OK);

        approvePrecompile
                .when(
                        () ->
                                ApprovePrecompile.decodeTokenApprove(
                                        eq(nestedPretendArguments),
                                        eq(token),
                                        eq(true),
                                        any(),
                                        any()))
                .thenReturn(APPROVE_WRAPPER);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(encoder.encodeApprove(true)).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void hapiApprove() {
        List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        List<TokenAllowance> tokenAllowances = new ArrayList<>();
        List<NftAllowance> nftAllowances = new ArrayList<>();

        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_APPROVE));
        givenMinimalFrameContext(pretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(syntheticTxnFactory.createFungibleApproval(APPROVE_WRAPPER))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance())
                .willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore))
                .willReturn(approveAllowanceLogic);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);

        given(
                        allowanceChecks.allowancesValidation(
                                cryptoAllowances,
                                tokenAllowances,
                                nftAllowances,
                                new Account(accountId),
                                stateView))
                .willReturn(OK);

        approvePrecompile
                .when(
                        () ->
                                ApprovePrecompile.decodeTokenApprove(
                                        eq(pretendArguments), eq(null), eq(true), any(), any()))
                .thenReturn(APPROVE_WRAPPER);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(encoder.encodeApprove(SUCCESS.getNumber(), true)).willReturn(successResult);
        given(wrappedLedgers.canonicalAddress(recipientAddress)).willReturn(recipientAddress);
        given(wrappedLedgers.canonicalAddress(contractAddress)).willReturn(senderAddress);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(frame)
                .addLog(
                        EncodingFacade.LogBuilder.logBuilder()
                                .forLogger(tokenAddress)
                                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                                .forIndexedArgument(senderAddress)
                                .forIndexedArgument(recipientAddress)
                                .forDataItem(APPROVE_WRAPPER.amount())
                                .build());
    }

    @Test
    void hapiApproveNFT() {
        List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        List<TokenAllowance> tokenAllowances = new ArrayList<>();
        List<NftAllowance> nftAllowances = new ArrayList<>();

        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_APPROVE_NFT));
        givenMinimalFrameContext(pretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(syntheticTxnFactory.createNonfungibleApproval(eq(APPROVE_NFT_WRAPPER), any(), any()))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance())
                .willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore))
                .willReturn(approveAllowanceLogic);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);

        given(wrappedLedgers.ownerIfPresent(any())).willReturn(ownerEntity);
        given(
                        allowanceChecks.allowancesValidation(
                                cryptoAllowances,
                                tokenAllowances,
                                nftAllowances,
                                new Account(accountId),
                                stateView))
                .willReturn(OK);

        approvePrecompile
                .when(
                        () ->
                                ApprovePrecompile.decodeTokenApprove(
                                        eq(pretendArguments), eq(null), eq(false), any(), any()))
                .thenReturn(APPROVE_NFT_WRAPPER);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(encoder.encodeApproveNFT(SUCCESS.getNumber())).willReturn(successResult);
        given(wrappedLedgers.canonicalAddress(recipientAddress)).willReturn(recipientAddress);
        given(wrappedLedgers.canonicalAddress(contractAddress)).willReturn(senderAddress);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(frame)
                .addLog(
                        EncodingFacade.LogBuilder.logBuilder()
                                .forLogger(tokenAddress)
                                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                                .forIndexedArgument(senderAddress)
                                .forIndexedArgument(recipientAddress)
                                .forIndexedArgument(APPROVE_NFT_WRAPPER.serialNumber())
                                .build());
    }

    @Test
    void transfer() throws InvalidProtocolBufferException {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(TOKEN_TRANSFER_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(anyBoolean(), any(), any(), any())).willReturn(true);
        given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(anyBoolean(), any(), any(), any()))
                .willReturn(true, true);

        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        ercTransferPrecompile
                .when(
                        () ->
                                ERCTransferPrecompile.decodeERCTransfer(
                                        eq(nestedPretendArguments), any(), any(), any()))
                .thenReturn(Collections.singletonList(TOKEN_TRANSFER_WRAPPER));

        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(wrappedLedgers.canonicalAddress(recipientAddress)).willReturn(recipientAddress);
        given(wrappedLedgers.canonicalAddress(senderAddress)).willReturn(senderAddress);
        given(encoder.encodeEcFungibleTransfer(true)).willReturn(successResult);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        final var log =
                EncodingFacade.LogBuilder.logBuilder()
                        .forLogger(tokenAddress)
                        .forEventSignature(AbiConstants.TRANSFER_EVENT)
                        .forIndexedArgument(senderAddress)
                        .forIndexedArgument(recipientAddress)
                        .forDataItem(AMOUNT)
                        .build();
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(frame).addLog(log);
    }

    @Test
    void transferFrom() throws InvalidProtocolBufferException {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER_FROM));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(TOKEN_TRANSFER_FROM_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(anyBoolean(), any(), any(), any())).willReturn(true);
        given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(anyBoolean(), any(), any(), any()))
                .willReturn(true, true);

        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);

        ercTransferPrecompile
                .when(
                        () ->
                                ERCTransferPrecompile.decodeERCTransferFrom(
                                        eq(nestedPretendArguments),
                                        any(),
                                        eq(true),
                                        any(),
                                        any(),
                                        any()))
                .thenReturn(Collections.singletonList(TOKEN_TRANSFER_FROM_WRAPPER));

        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(encoder.encodeEcFungibleTransfer(true)).willReturn(successResult);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferFromHapiFungible() throws InvalidProtocolBufferException {
        final var pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_FROM));
        givenMinimalFrameContext(Bytes.EMPTY);
        givenLedgers();
        givenPricingUtilsContext();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(TOKEN_TRANSFER_FROM_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(anyBoolean(), any(), any(), any())).willReturn(true);
        given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(anyBoolean(), any(), any(), any()))
                .willReturn(true, true);

        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);

        ercTransferPrecompile
                .when(
                        () ->
                                ERCTransferPrecompile.decodeERCTransferFrom(
                                        eq(pretendArguments),
                                        eq(null),
                                        eq(true),
                                        any(),
                                        any(),
                                        any()))
                .thenReturn(Collections.singletonList(TOKEN_TRANSFER_FROM_WRAPPER));

        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(frame)
                .addLog(
                        EncodingFacade.LogBuilder.logBuilder()
                                .forLogger(EntityIdUtils.asTypedEvmAddress(token))
                                .forEventSignature(AbiConstants.TRANSFER_EVENT)
                                .forIndexedArgument(sender)
                                .forIndexedArgument(receiver)
                                .forDataItem(AMOUNT)
                                .build());
    }

    @Test
    void transferFromNFTHapi() throws InvalidProtocolBufferException {
        final var pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_FROM_NFT));
        givenMinimalFrameContext(Bytes.EMPTY);
        givenLedgers();
        givenPricingUtilsContext();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(TOKEN_TRANSFER_FROM_NFT_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(anyBoolean(), any(), any(), any())).willReturn(true);

        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(nfts.contains(NftId.fromGrpc(token, serialNumber))).willReturn(true);

        ercTransferPrecompile
                .when(
                        () ->
                                ERCTransferPrecompile.decodeERCTransferFrom(
                                        eq(pretendArguments),
                                        eq(null),
                                        eq(false),
                                        any(),
                                        any(),
                                        any()))
                .thenReturn(Collections.singletonList(TOKEN_TRANSFER_FROM_NFT_WRAPPER));

        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(frame)
                .addLog(
                        EncodingFacade.LogBuilder.logBuilder()
                                .forLogger(EntityIdUtils.asTypedEvmAddress(token))
                                .forEventSignature(AbiConstants.TRANSFER_EVENT)
                                .forIndexedArgument(sender)
                                .forIndexedArgument(receiver)
                                .forIndexedArgument(serialNumber)
                                .build());
    }

    @Test
    void transferFails() throws InvalidProtocolBufferException {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(TOKEN_TRANSFER_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(anyBoolean(), any(), any(), any())).willReturn(false);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        creator.createUnsuccessfulSyntheticRecord(
                                INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE))
                .willReturn(mockRecordBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        ercTransferPrecompile
                .when(
                        () ->
                                ERCTransferPrecompile.decodeERCTransfer(
                                        eq(nestedPretendArguments), any(), any(), any()))
                .thenReturn(Collections.singletonList(TOKEN_TRANSFER_WRAPPER));

        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(invalidFullPrefix, result);
    }

    @Test
    void ownerOfNotSupported() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_OWNER_OF_NFT));
        Bytes pretendArguments =
                givenMinimalFrameContextWithoutParentUpdater(nestedPretendArguments);

        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        subject.prepareFields(frame);

        final var exception =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> subject.prepareComputation(pretendArguments, a -> a));
        assertEquals(NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON, exception.getMessage());
    }

    @Test
    void tokenURINotSupported() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TOKEN_URI_NFT));
        Bytes pretendArguments =
                givenMinimalFrameContextWithoutParentUpdater(nestedPretendArguments);

        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.FUNGIBLE_COMMON);
        subject.prepareFields(frame);

        final var exception =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> subject.prepareComputation(pretendArguments, a -> a));
        assertEquals(NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON, exception.getMessage());
    }

    private Bytes givenMinimalFrameContext(Bytes nestedArg) {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                fungibleTokenAddr,
                nestedArg);
    }

    private Bytes givenMinimalFrameContextWithoutParentUpdater(Bytes nestedArg) {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                fungibleTokenAddr,
                nestedArg);
    }

    private void givenLedgers() {
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
    }

    public static final BalanceOfWrapper BALANCE_OF_WRAPPER = new BalanceOfWrapper(sender);

    public static final TokenAllowanceWrapper ALLOWANCE_WRAPPER =
            new TokenAllowanceWrapper(token, sender, receiver);

    public static final TokenTransferWrapper TOKEN_TRANSFER_WRAPPER =
            new TokenTransferWrapper(
                    new ArrayList<>() {},
                    List.of(
                            new SyntheticTxnFactory.FungibleTokenTransfer(
                                    AMOUNT, false, token, null, receiver),
                            new SyntheticTxnFactory.FungibleTokenTransfer(
                                    -AMOUNT, false, token, sender, null)));

    public static final TokenTransferWrapper TOKEN_TRANSFER_FROM_WRAPPER =
            new TokenTransferWrapper(
                    new ArrayList<>() {},
                    List.of(
                            new SyntheticTxnFactory.FungibleTokenTransfer(
                                    AMOUNT, true, token, null, receiver),
                            new SyntheticTxnFactory.FungibleTokenTransfer(
                                    -AMOUNT, true, token, sender, null)));

    public static final TokenTransferWrapper TOKEN_TRANSFER_FROM_NFT_WRAPPER =
            new TokenTransferWrapper(
                    List.of(
                            SyntheticTxnFactory.NftExchange.fromApproval(
                                    serialNumber, token, sender, receiver)),
                    new ArrayList<>() {});

    public static final ApproveWrapper APPROVE_WRAPPER =
            new ApproveWrapper(token, receiver, BigInteger.ONE, BigInteger.ZERO, true);

    public static final ApproveWrapper APPROVE_NFT_WRAPPER =
            new ApproveWrapper(token, receiver, BigInteger.ONE, BigInteger.ZERO, false);
}
