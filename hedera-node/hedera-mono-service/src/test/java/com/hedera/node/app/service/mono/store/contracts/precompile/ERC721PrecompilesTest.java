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
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_APPROVE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_DECIMALS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_GET_APPROVED;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_OWNER_OF_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_SYMBOL;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOTAL_SUPPLY_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TRANSFER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TRANSFER_FROM;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_APPROVED;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_IS_APPROVED_FOR_ALL;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_SET_APPROVAL_FOR_ALL;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_NFT_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.failResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.feeCollector;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.invalidFullPrefix;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.missingNftResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nftTransferList;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nonFungibleTokenAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.ownerOfAndTokenUriWrapper;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.precompiledContract;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.receiver;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.senderId;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.serialNumber;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.token;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenTransferChanges;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.BalanceOfWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetApprovedWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.IsApproveForAllWrapper;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfers;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.TransferLogic;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.SetApprovalForAllWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.ApprovePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.BalanceOfPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.ERCTransferPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetApprovedPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.IsApprovedForAllPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.OwnerOfPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.SetApprovalForAllPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenURIPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Account;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hedera.node.app.service.mono.txns.crypto.ApproveAllowanceLogic;
import com.hedera.node.app.service.mono.txns.crypto.DeleteAllowanceLogic;
import com.hedera.node.app.service.mono.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.node.app.service.mono.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.apache.commons.lang3.tuple.Pair;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ERC721PrecompilesTest {
    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame frame;

    @Mock(strictness = LENIENT)
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
    private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nfts;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel> tokenRels;

    @Mock
    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;

    @Mock
    private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;

    @Mock
    private ExpiringCreations creator;

    @Mock
    private ImpliedTransfersMarshal impliedTransfersMarshal;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private StateView stateView;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private InfrastructureFactory infrastructureFactory;

    @Mock
    private CryptoApproveAllowanceTransactionBody cryptoApproveAllowanceTransactionBody;

    @Mock
    private DeleteAllowanceChecks deleteAllowanceChecks;

    @Mock
    private ApproveAllowanceChecks allowanceChecks;

    @Mock
    private AccountStore accountStore;

    @Mock
    private CryptoTransferTransactionBody cryptoTransferTransactionBody;

    @Mock
    private CryptoDeleteAllowanceTransactionBody cryptoDeleteAllowanceTransactionBody;

    @Mock
    private ContractAliases aliases;

    @Mock
    private ImpliedTransfers impliedTransfers;

    @Mock
    private ImpliedTransfersMeta impliedTransfersMeta;

    @Mock
    private ApproveAllowanceLogic approveAllowanceLogic;

    @Mock
    private DeleteAllowanceLogic deleteAllowanceLogic;

    @Mock
    private TransferLogic transferLogic;

    @Mock
    private HederaTokenStore hederaTokenStore;

    @Mock
    private TypedTokenStore tokenStore;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private ExchangeRate exchangeRate;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private Account account;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final Address ADDRESS_2 = Address.fromHexString("0x2");
    private static final Address ADDRESS_3 = Address.fromHexString("0x3");

    private enum WithHapiBlockLimit {
        LOW,
        HIGH
    }

    private static final Map<WithHapiBlockLimit, Consumer<GlobalDynamicProperties>> setHapiBlockLimitGivens = Map.of(
            WithHapiBlockLimit.LOW,
            props -> {
                given(props.maxNumWithHapiSigsAccess()).willReturn(0L);
            },
            WithHapiBlockLimit.HIGH,
            props -> {
                given(props.maxNumWithHapiSigsAccess()).willReturn(Long.MAX_VALUE);
                given(props.systemContractsWithTopLevelSigsAccess()).willReturn(Set.of(CryptoTransfer));
            });

    private HTSPrecompiledContract subject;
    private MockedStatic<EntityIdUtils> entityIdUtils;
    private MockedStatic<IsApprovedForAllPrecompile> isApprovedForAllPrecompile;
    private MockedStatic<ApprovePrecompile> approvePrecompile;
    private MockedStatic<SetApprovalForAllPrecompile> setApprovalForAllPrecompile;
    private MockedStatic<GetApprovedPrecompile> getApprovedPrecompile;
    private MockedStatic<BalanceOfPrecompile> balanceOfPrecompile;
    private MockedStatic<OwnerOfPrecompile> ownerOfPrecompile;
    private MockedStatic<ERCTransferPrecompile> ercTransferPrecompile;
    private MockedStatic<TokenURIPrecompile> tokenURIPrecompile;

    @BeforeEach
    void setUp() {
        final PrecompilePricingUtils precompilePricingUtils = new PrecompilePricingUtils(
                assetLoader, exchange, () -> feeCalculator, resourceCosts, stateView, accessorFactory);
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
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(nonFungibleTokenAddr.toArray()))
                .thenReturn(token);
        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(nonFungibleTokenAddr))
                .thenReturn(token);
        entityIdUtils
                .when(() -> EntityIdUtils.contractIdFromEvmAddress(
                        Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()))
                .thenReturn(precompiledContract);
        entityIdUtils
                .when(() -> EntityIdUtils.accountIdFromEvmAddress(senderAddress))
                .thenReturn(sender);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(token)).thenReturn(tokenAddress);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(sender)).thenReturn(senderAddress);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(receiver)).thenReturn(recipientAddress);
        entityIdUtils.when(() -> EntityIdUtils.asEvmAddress(3)).thenReturn(ADDRESS_3.toArray());
        entityIdUtils.when(() -> EntityIdUtils.asEvmAddress(2)).thenReturn(ADDRESS_2.toArray());
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        isApprovedForAllPrecompile = Mockito.mockStatic(IsApprovedForAllPrecompile.class);
        approvePrecompile = Mockito.mockStatic(ApprovePrecompile.class);
        setApprovalForAllPrecompile = Mockito.mockStatic(SetApprovalForAllPrecompile.class);
        getApprovedPrecompile = Mockito.mockStatic(GetApprovedPrecompile.class);
        balanceOfPrecompile = Mockito.mockStatic(BalanceOfPrecompile.class);
        ownerOfPrecompile = Mockito.mockStatic(OwnerOfPrecompile.class);
        ercTransferPrecompile = Mockito.mockStatic(ERCTransferPrecompile.class);
        tokenURIPrecompile = Mockito.mockStatic(TokenURIPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        entityIdUtils.close();
        isApprovedForAllPrecompile.close();
        approvePrecompile.close();
        setApprovalForAllPrecompile.close();
        getApprovedPrecompile.close();
        balanceOfPrecompile.close();
        ownerOfPrecompile.close();
        ercTransferPrecompile.close();
        tokenURIPrecompile.close();
    }

    @Test
    void name() {
        final Bytes pretendArguments = givenMinimalFrameContext(Bytes.of(Integers.toBytes(ABI_ID_ERC_NAME)));
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.nodeFee()).willReturn(1L);
        given(mockFeeObject.networkFee()).willReturn(1L);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(evmEncoder.encodeName(any())).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void symbol() {
        final Bytes pretendArguments = givenMinimalFrameContext(Bytes.of(Integers.toBytes(ABI_ID_ERC_SYMBOL)));
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.nodeFee()).willReturn(1L);
        given(mockFeeObject.networkFee()).willReturn(1L);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(evmEncoder.encodeSymbol(any())).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void ercIsApprovedForAllWorksWithBothOwnerAndOperatorExtant() {
        final Set<FcTokenAllowanceId> allowances = new TreeSet<>();
        final FcTokenAllowanceId fcTokenAllowanceId = FcTokenAllowanceId.from(
                EntityNum.fromLong(token.getTokenNum()), EntityNum.fromLong(receiver.getAccountNum()));
        allowances.add(fcTokenAllowanceId);

        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_IS_APPROVED_FOR_ALL));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(accounts.contains(IS_APPROVE_FOR_ALL_WRAPPER.owner())).willReturn(true);
        given(accounts.contains(IS_APPROVE_FOR_ALL_WRAPPER.operator())).willReturn(true);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.nodeFee()).willReturn(1L);
        given(mockFeeObject.networkFee()).willReturn(1L);
        given(mockFeeObject.serviceFee()).willReturn(1L);

        given(evmEncoder.encodeIsApprovedForAll(true)).willReturn(successResult);
        isApprovedForAllPrecompile
                .when(() -> IsApprovedForAllPrecompile.decodeIsApprovedForAll(any(), any(), any()))
                .thenReturn(IS_APPROVE_FOR_ALL_WRAPPER);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(accounts.get(any(), any())).willReturn(allowances);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void hapiIsApprovedForAllWorksWithBothOwnerAndOperatorExtant() {
        final Set<FcTokenAllowanceId> allowances = new TreeSet<>();
        final FcTokenAllowanceId fcTokenAllowanceId = FcTokenAllowanceId.from(
                EntityNum.fromLong(token.getTokenNum()), EntityNum.fromLong(receiver.getAccountNum()));
        allowances.add(fcTokenAllowanceId);

        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_IS_APPROVED_FOR_ALL));
        givenMinimalFrameContext(pretendArguments);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(accounts.contains(IS_APPROVE_FOR_ALL_WRAPPER.owner())).willReturn(true);
        given(accounts.contains(IS_APPROVE_FOR_ALL_WRAPPER.operator())).willReturn(true);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.nodeFee()).willReturn(1L);
        given(mockFeeObject.networkFee()).willReturn(1L);
        given(mockFeeObject.serviceFee()).willReturn(1L);

        given(encoder.encodeIsApprovedForAll(SUCCESS.getNumber(), true)).willReturn(successResult);
        isApprovedForAllPrecompile
                .when(() -> IsApprovedForAllPrecompile.decodeIsApprovedForAll(eq(pretendArguments), eq(null), any()))
                .thenReturn(IS_APPROVE_FOR_ALL_WRAPPER);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(accounts.get(any(), any())).willReturn(allowances);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void isApprovedForAllWorksWithOperatorMissing() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_IS_APPROVED_FOR_ALL));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(accounts.contains(IS_APPROVE_FOR_ALL_WRAPPER.owner())).willReturn(true);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.nodeFee()).willReturn(1L);
        given(mockFeeObject.networkFee()).willReturn(1L);
        given(mockFeeObject.serviceFee()).willReturn(1L);

        given(evmEncoder.encodeIsApprovedForAll(false)).willReturn(successResult);
        isApprovedForAllPrecompile
                .when(() -> IsApprovedForAllPrecompile.decodeIsApprovedForAll(any(), any(), any()))
                .thenReturn(IS_APPROVE_FOR_ALL_WRAPPER);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void approve() {
        final List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        final List<TokenAllowance> tokenAllowances = new ArrayList<>();
        final List<NftAllowance> nftAllowances = new ArrayList<>();
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);

        given(syntheticTxnFactory.createNonfungibleApproval(eq(APPROVE_WRAPPER), any(), any()))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance()).willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore))
                .willReturn(approveAllowanceLogic);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(account);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);

        given(allowanceChecks.allowancesValidation(
                        cryptoAllowances, tokenAllowances, nftAllowances, account, accountStore, tokenStore))
                .willReturn(OK);

        approvePrecompile
                .when(() -> ApprovePrecompile.decodeTokenApprove(
                        eq(nestedPretendArguments), eq(token), eq(false), any(), any()))
                .thenReturn(APPROVE_WRAPPER);
        given(encoder.encodeApprove(true)).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void approveRevertsIfItFails() {
        givenPricingUtilsContext();
        final List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        final List<TokenAllowance> tokenAllowances = new ArrayList<>();
        final List<NftAllowance> nftAllowances = new ArrayList<>();
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);

        given(syntheticTxnFactory.createNonfungibleApproval(eq(APPROVE_WRAPPER), any(), any()))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance()).willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(any(), any(), any(), any(), any()))
                .willReturn(tokenStore);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(account);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        given(allowanceChecks.allowancesValidation(
                        cryptoAllowances, tokenAllowances, nftAllowances, account, accountStore, tokenStore))
                .willReturn(OK);
        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);

        approvePrecompile
                .when(() -> ApprovePrecompile.decodeTokenApprove(
                        eq(nestedPretendArguments), eq(token), eq(false), any(), any()))
                .thenReturn(APPROVE_WRAPPER);
        given(infrastructureFactory.newApproveAllowanceLogic(any(), any())).willReturn(approveAllowanceLogic);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);
        willThrow(new InvalidTransactionException(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO))
                .given(approveAllowanceLogic)
                .approveAllowance(any(), any(), any(), any());

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);
        final var expectedFailure = EncodingFacade.resultFrom(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);

        verify(frame)
                .setRevertReason(
                        Bytes.of(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO.name().getBytes()));
        assertEquals(expectedFailure, result);
    }

    @Test
    void approveSpender0WhenOwner() {
        givenPricingUtilsContext();
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);

        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(syntheticTxnFactory.createDeleteAllowance(APPROVE_WRAPPER_0, EntityId.fromGrpcAccountId(sender)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoDeleteAllowance()).willReturn(cryptoDeleteAllowanceTransactionBody);
        given(cryptoDeleteAllowanceTransactionBody.getNftAllowancesList())
                .willReturn(
                        List.of(NftRemoveAllowance.newBuilder().setOwner(sender).build()));

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(account);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(deleteAllowanceChecks.deleteAllowancesValidation(any(), any(), any(), any()))
                .willReturn(OK);

        approvePrecompile
                .when(() -> ApprovePrecompile.decodeTokenApprove(
                        eq(nestedPretendArguments), eq(token), eq(false), any(), any()))
                .thenReturn(APPROVE_WRAPPER_0);
        given(encoder.encodeApprove(true)).willReturn(successResult);
        given(infrastructureFactory.newDeleteAllowanceLogic(any(), any())).willReturn(deleteAllowanceLogic);
        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void approveSpender0WhenGrantedApproveForAll() {
        givenPricingUtilsContext();
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);

        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(syntheticTxnFactory.createDeleteAllowance(APPROVE_WRAPPER_0, EntityId.fromGrpcAccountId(sender)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoDeleteAllowance()).willReturn(cryptoDeleteAllowanceTransactionBody);
        given(cryptoDeleteAllowanceTransactionBody.getNftAllowancesList())
                .willReturn(List.of(
                        NftRemoveAllowance.newBuilder().setOwner(feeCollector).build()));
        given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(any(), any(), any(), any(), any()))
                .willReturn(tokenStore);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(account);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(deleteAllowanceChecks.deleteAllowancesValidation(any(), any(), any(), any()))
                .willReturn(OK);

        approvePrecompile
                .when(() -> ApprovePrecompile.decodeTokenApprove(
                        eq(nestedPretendArguments), eq(token), eq(false), any(), any()))
                .thenReturn(APPROVE_WRAPPER_0);
        given(encoder.encodeApprove(true)).willReturn(successResult);
        given(infrastructureFactory.newDeleteAllowanceLogic(any(), any())).willReturn(deleteAllowanceLogic);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void approveSpender0NoGoodIfNotPermissioned() {
        givenPricingUtilsContext();
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);

        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(syntheticTxnFactory.createDeleteAllowance(APPROVE_WRAPPER_0, EntityId.fromGrpcAccountId(sender)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        approvePrecompile
                .when(() -> ApprovePrecompile.decodeTokenApprove(
                        eq(nestedPretendArguments), eq(token), eq(false), any(), any()))
                .thenReturn(APPROVE_WRAPPER_0);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);
        final var expectedFailure = EncodingFacade.resultFrom(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);

        verify(frame)
                .setRevertReason(
                        Bytes.of(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO.name().getBytes()));
        assertEquals(expectedFailure, result);
    }

    @Test
    void validatesImpliedNftApprovalDeletion() {
        givenPricingUtilsContext();
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(creator.createUnsuccessfulSyntheticRecord(INVALID_ALLOWANCE_OWNER_ID))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);

        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(syntheticTxnFactory.createDeleteAllowance(APPROVE_WRAPPER_0, EntityId.fromGrpcAccountId(sender)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoDeleteAllowance()).willReturn(cryptoDeleteAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(account);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        given(cryptoDeleteAllowanceTransactionBody.getNftAllowancesList()).willReturn(Collections.emptyList());
        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);

        approvePrecompile
                .when(() -> ApprovePrecompile.decodeTokenApprove(
                        eq(nestedPretendArguments), eq(token), eq(false), any(), any()))
                .thenReturn(APPROVE_WRAPPER_0);
        given(deleteAllowanceChecks.deleteAllowancesValidation(any(), any(), any(), any()))
                .willReturn(INVALID_ALLOWANCE_OWNER_ID);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);
        final var expectedFailure = EncodingFacade.resultFrom(INVALID_ALLOWANCE_OWNER_ID);

        verify(frame).setRevertReason(Bytes.of(INVALID_ALLOWANCE_OWNER_ID.name().getBytes()));
        assertEquals(expectedFailure, result);
    }

    @Test
    void allowanceValidation() {
        givenPricingUtilsContext();
        final List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        final List<TokenAllowance> tokenAllowances = new ArrayList<>();
        final List<NftAllowance> nftAllowances = new ArrayList<>();

        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);

        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);
        given(syntheticTxnFactory.createNonfungibleApproval(eq(APPROVE_WRAPPER), any(), any()))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance()).willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(account);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        given(allowanceChecks.allowancesValidation(
                        cryptoAllowances, tokenAllowances, nftAllowances, account, accountStore, tokenStore))
                .willReturn(FAIL_INVALID);

        approvePrecompile
                .when(() -> ApprovePrecompile.decodeTokenApprove(
                        eq(nestedPretendArguments), eq(token), eq(false), any(), any()))
                .thenReturn(APPROVE_WRAPPER);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(failResult, result);
    }

    @Test
    void ercSetApprovalForAll() {
        final List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        final List<TokenAllowance> tokenAllowances = new ArrayList<>();
        final List<NftAllowance> nftAllowances = new ArrayList<>();

        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_SET_APPROVAL_FOR_ALL));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);

        given(syntheticTxnFactory.createApproveAllowanceForAllNFT(SET_APPROVAL_FOR_ALL_WRAPPER, new EntityId(0, 0, 7L)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance()).willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore))
                .willReturn(approveAllowanceLogic);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(account);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(wrappedLedgers.canonicalAddress(recipientAddress)).willReturn(recipientAddress);
        given(wrappedLedgers.canonicalAddress(contractAddress)).willReturn(senderAddress);

        given(allowanceChecks.allowancesValidation(
                        cryptoAllowances, tokenAllowances, nftAllowances, account, accountStore, tokenStore))
                .willReturn(OK);

        setApprovalForAllPrecompile
                .when(() ->
                        SetApprovalForAllPrecompile.decodeSetApprovalForAll(eq(nestedPretendArguments), any(), any()))
                .thenReturn(SET_APPROVAL_FOR_ALL_WRAPPER);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(frame)
                .addLog(EncodingFacade.LogBuilder.logBuilder()
                        .forLogger(tokenAddress)
                        .forEventSignature(AbiConstants.APPROVAL_FOR_ALL_EVENT)
                        .forIndexedArgument(senderAddress)
                        .forIndexedArgument(recipientAddress)
                        .forDataItem(SET_APPROVAL_FOR_ALL_WRAPPER.approved())
                        .build());
    }

    @Test
    void hapiSetApprovalForAll() {
        final List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        final List<TokenAllowance> tokenAllowances = new ArrayList<>();
        final List<NftAllowance> nftAllowances = new ArrayList<>();

        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_SET_APPROVAL_FOR_ALL));
        givenMinimalFrameContext(pretendArguments);
        givenLedgers();
        givenPricingUtilsContext();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);

        given(syntheticTxnFactory.createApproveAllowanceForAllNFT(SET_APPROVAL_FOR_ALL_WRAPPER, new EntityId(0, 0, 7L)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance()).willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore))
                .willReturn(approveAllowanceLogic);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(account);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);

        given(allowanceChecks.allowancesValidation(
                        cryptoAllowances, tokenAllowances, nftAllowances, account, accountStore, tokenStore))
                .willReturn(OK);

        setApprovalForAllPrecompile
                .when(() -> SetApprovalForAllPrecompile.decodeSetApprovalForAll(eq(pretendArguments), any(), any()))
                .thenReturn(SET_APPROVAL_FOR_ALL_WRAPPER);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void ercGetApproved() {
        final Set<FcTokenAllowanceId> allowances = new TreeSet<>();
        final FcTokenAllowanceId fcTokenAllowanceId = FcTokenAllowanceId.from(
                EntityNum.fromLong(token.getTokenNum()), EntityNum.fromLong(receiver.getAccountNum()));
        allowances.add(fcTokenAllowanceId);

        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_GET_APPROVED));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(wrappedLedgers.nfts()).willReturn(nfts);
        final var nftId = NftId.fromGrpc(token, GET_APPROVED_WRAPPER.serialNo());
        given(nfts.contains(nftId)).willReturn(true);
        given(nfts.get(nftId, NftProperty.SPENDER)).willReturn(EntityId.fromAddress(ADDRESS_3));
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.nodeFee()).willReturn(1L);
        given(mockFeeObject.networkFee()).willReturn(1L);
        given(mockFeeObject.serviceFee()).willReturn(1L);

        given(evmEncoder.encodeGetApproved(ADDRESS_3)).willReturn(successResult);
        getApprovedPrecompile
                .when(() -> GetApprovedPrecompile.decodeGetApproved(any(), any()))
                .thenReturn(GET_APPROVED_WRAPPER);
        given(wrappedLedgers.canonicalAddress(any())).willReturn(ADDRESS_3);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void hapiGetApproved() {
        final Set<FcTokenAllowanceId> allowances = new TreeSet<>();
        final FcTokenAllowanceId fcTokenAllowanceId = FcTokenAllowanceId.from(
                EntityNum.fromLong(token.getTokenNum()), EntityNum.fromLong(receiver.getAccountNum()));
        allowances.add(fcTokenAllowanceId);

        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_GET_APPROVED));
        givenMinimalFrameContext(pretendArguments);

        given(wrappedLedgers.nfts()).willReturn(nfts);
        final var nftId = NftId.fromGrpc(token, GET_APPROVED_WRAPPER.serialNo());
        given(nfts.contains(nftId)).willReturn(true);
        given(nfts.get(nftId, NftProperty.SPENDER)).willReturn(EntityId.fromAddress(ADDRESS_3));
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.nodeFee()).willReturn(1L);
        given(mockFeeObject.networkFee()).willReturn(1L);
        given(mockFeeObject.serviceFee()).willReturn(1L);

        given(encoder.encodeGetApproved(SUCCESS.getNumber(), ADDRESS_3)).willReturn(successResult);
        getApprovedPrecompile
                .when(() -> GetApprovedPrecompile.decodeGetApproved(pretendArguments, null))
                .thenReturn(GET_APPROVED_WRAPPER);
        given(wrappedLedgers.canonicalAddress(any())).willReturn(ADDRESS_3);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void totalSupply() {
        final Bytes pretendArguments =
                givenMinimalFrameContext(Bytes.of(Integers.toBytes(ABI_ID_ERC_TOTAL_SUPPLY_TOKEN)));
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.nodeFee()).willReturn(1L);
        given(mockFeeObject.networkFee()).willReturn(1L);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(wrappedLedgers.totalSupplyOf(any())).willReturn(10L);
        given(evmEncoder.encodeTotalSupply(10L)).willReturn(successResult);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void balanceOf() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_BALANCE_OF_TOKEN));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.nodeFee()).willReturn(1L);
        given(mockFeeObject.networkFee()).willReturn(1L);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        balanceOfPrecompile
                .when(() -> BalanceOfPrecompile.decodeBalanceOf(eq(nestedPretendArguments), any()))
                .thenReturn(BALANCE_OF_WRAPPER);
        given(wrappedLedgers.balanceOf(any(), any())).willReturn(10L);
        given(evmEncoder.encodeBalance(10L)).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then:
        assertEquals(successResult, subject.computeInternal(frame));
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void ownerOfHappyPathWorks() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_OWNER_OF_NFT));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.nodeFee()).willReturn(1L);
        given(mockFeeObject.networkFee()).willReturn(1L);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        ownerOfPrecompile
                .when(() -> OwnerOfPrecompile.decodeOwnerOf(nestedPretendArguments))
                .thenReturn(ownerOfAndTokenUriWrapper);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(nfts.contains(NftId.fromGrpc(token, ownerOfAndTokenUriWrapper.serialNo())))
                .willReturn(true);
        given(wrappedLedgers.ownerOf(any())).willReturn(senderAddress);
        given(wrappedLedgers.canonicalAddress(senderAddress)).willReturn(senderAddress);
        given(evmEncoder.encodeOwner(senderAddress)).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void ownerOfRevertsWithMissingNft() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_OWNER_OF_NFT));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.nodeFee()).willReturn(1L);
        given(mockFeeObject.networkFee()).willReturn(1L);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        ownerOfPrecompile
                .when(() -> OwnerOfPrecompile.decodeOwnerOf(nestedPretendArguments))
                .thenReturn(ownerOfAndTokenUriWrapper);
        given(wrappedLedgers.nfts()).willReturn(nfts);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        assertEquals(missingNftResult, result);
    }

    @ParameterizedTest
    @EnumSource
    void transferFrom(final WithHapiBlockLimit limit) throws InvalidProtocolBufferException {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER_FROM));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        setHapiBlockLimitGivens.get(limit).accept(dynamicProperties);
        givenLedgers();
        givenPricingUtilsContext();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(nftTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(frame.getSenderAddress()).willReturn(senderAddress);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(true);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(infrastructureFactory.newImpliedTransfersMarshal(any())).willReturn(impliedTransfersMarshal);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(infrastructureFactory.newTransferLogic(hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder()
                        .setCryptoTransfer(cryptoTransferTransactionBody)
                        .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        ercTransferPrecompile
                .when(() -> ERCTransferPrecompile.decodeERCTransferFrom(
                        eq(nestedPretendArguments), any(), eq(false), any(), any(), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFT_WRAPPER);
        final var nftId = NftId.fromGrpc(token, serialNumber);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.canonicalAddress(recipientAddress)).willReturn(recipientAddress);
        given(wrappedLedgers.canonicalAddress(senderAddress)).willReturn(senderAddress);
        given(nfts.contains(nftId)).willReturn(true);

        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();

        final var log = EncodingFacade.LogBuilder.logBuilder()
                .forLogger(tokenAddress)
                .forEventSignature(AbiConstants.TRANSFER_EVENT)
                .forIndexedArgument(senderAddress)
                .forIndexedArgument(recipientAddress)
                .forIndexedArgument(serialNumber)
                .build();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(Bytes.EMPTY, result);

        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(frame).addLog(log);
    }

    @ParameterizedTest
    @EnumSource
    void transferFromFailsForInvalidSig(final WithHapiBlockLimit limit) throws InvalidProtocolBufferException {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER_FROM));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        setHapiBlockLimitGivens.get(limit).accept(dynamicProperties);
        givenPricingUtilsContext();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(nftTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any(), eq(CryptoTransfer)))
                .willReturn(false);
        given(infrastructureFactory.newImpliedTransfersMarshal(any())).willReturn(impliedTransfersMarshal);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        given(creator.createUnsuccessfulSyntheticRecord(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder()
                        .setCryptoTransfer(cryptoTransferTransactionBody)
                        .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        ercTransferPrecompile
                .when(() -> ERCTransferPrecompile.decodeERCTransferFrom(
                        eq(nestedPretendArguments), any(), eq(false), any(), any(), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFT_WRAPPER);
        final var nftId = NftId.fromGrpc(token, serialNumber);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(nfts.contains(nftId)).willReturn(true);

        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
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
    void erc721SystemFailureSurfacesResult() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_OWNER_OF_NFT));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.nodeFee()).willReturn(1L);
        given(mockFeeObject.networkFee()).willReturn(1L);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        ownerOfPrecompile
                .when(() -> OwnerOfPrecompile.decodeOwnerOf(nestedPretendArguments))
                .thenReturn(ownerOfAndTokenUriWrapper);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        assertEquals(failResult, result);
    }

    @Test
    void tokenURI() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TOKEN_URI_NFT));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.nodeFee()).willReturn(1L);
        given(mockFeeObject.networkFee()).willReturn(1L);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        tokenURIPrecompile
                .when(() -> TokenURIPrecompile.decodeTokenUriNFT(nestedPretendArguments))
                .thenReturn(ownerOfAndTokenUriWrapper);
        given(wrappedLedgers.metadataOf(any())).willReturn("Metadata");
        given(evmEncoder.encodeTokenUri("Metadata")).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void transferNotSupported() {
        final Bytes pretendArguments =
                givenMinimalFrameContextWithoutParentUpdater(Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER)));
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        subject.prepareFields(frame);

        final var exception = assertThrows(
                InvalidTransactionException.class, () -> subject.prepareComputation(pretendArguments, a -> a));
        assertEquals(NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON, exception.getMessage());
    }

    @Test
    void decimalsNotSupported() {
        final Bytes pretendArguments =
                givenMinimalFrameContextWithoutParentUpdater(Bytes.of(Integers.toBytes(ABI_ID_ERC_DECIMALS)));
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        subject.prepareFields(frame);

        final var exception = assertThrows(
                InvalidTransactionException.class, () -> subject.prepareComputation(pretendArguments, a -> a));
        assertEquals(NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON, exception.getMessage());
    }

    private Bytes givenMinimalFrameContext(final Bytes nestedPretendArguments) {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)), nonFungibleTokenAddr, nestedPretendArguments);
    }

    private Bytes givenMinimalFrameContextWithoutParentUpdater(final Bytes nestedPretendArguments) {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)), nonFungibleTokenAddr, nestedPretendArguments);
    }

    public static final BalanceOfWrapper<AccountID> BALANCE_OF_WRAPPER = new BalanceOfWrapper<>(sender);

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

    public static final IsApproveForAllWrapper<TokenID, AccountID, AccountID> IS_APPROVE_FOR_ALL_WRAPPER =
            new IsApproveForAllWrapper<>(token, sender, receiver);

    public static final GetApprovedWrapper<?> GET_APPROVED_WRAPPER =
            new GetApprovedWrapper<>(token, token.getTokenNum());

    public static final SetApprovalForAllWrapper SET_APPROVAL_FOR_ALL_WRAPPER =
            new SetApprovalForAllWrapper(token, receiver, true);

    public static final ApproveWrapper APPROVE_WRAPPER =
            new ApproveWrapper(token, receiver, BigInteger.ZERO, BigInteger.ONE, false);

    public static final ApproveWrapper APPROVE_WRAPPER_0 =
            new ApproveWrapper(token, IdUtils.asAccount("0.0.0"), BigInteger.ZERO, BigInteger.ONE, false);
}
