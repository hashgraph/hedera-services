package com.hedera.services.store.contracts.precompile;
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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareSoliditySigsVerifier;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.ledger.ids.ExceptionalEntityIdSource.NOOP_ID_SOURCE;
import static com.hedera.services.ledger.properties.TokenProperty.TOKEN_TYPE;
import static com.hedera.services.legacy.core.jproto.TxnReceipt.SUCCESS_LITERAL;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_BALANCE_OF_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_DECIMALS;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_ERC_TRANSFER;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_ERC_TRANSFER_FROM;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_NAME;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_SYMBOL;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TOTAL_SUPPLY_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.AMOUNT;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.precompiledContract;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.receiver;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.token;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenTransferChanges;
import static com.hedera.services.store.tokens.views.UniqueTokenViewsManager.NOOP_VIEWS_MANAGER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ERC20PrecompilesTest {
    @Mock
    private Bytes pretendArguments;
    @Mock
    private Bytes nestedPretendArguments;
    @Mock
    private GlobalDynamicProperties dynamicProperties;
    @Mock
    private OptionValidator validator;
    @Mock
    private GasCalculator gasCalculator;
    @Mock
    private MessageFrame frame;
    @Mock
    private TxnAwareSoliditySigsVerifier sigsVerifier;
    @Mock
    private AccountRecordsHistorian recordsHistorian;
    @Mock
    private DecodingFacade decoder;
    @Mock
    private EncodingFacade encoder;
    @Mock
    private HTSPrecompiledContract.TokenStoreFactory tokenStoreFactory;
    @Mock
    private HTSPrecompiledContract.AccountStoreFactory accountStoreFactory;
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
    private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nfts;
    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRels;
    @Mock
    private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts;
    @Mock
    private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock
    private ExpiringCreations creator;
    @Mock
    private DissociationFactory dissociationFactory;
    @Mock
    private ImpliedTransfersMarshal impliedTransfersMarshal;
    @Mock
    private FeeCalculator feeCalculator;
    @Mock
    private StateView stateView;
    @Mock
    private PrecompilePricingUtils precompilePricingUtils;
    @Mock
    private CryptoTransferTransactionBody cryptoTransferTransactionBody;
    @Mock
    private ImpliedTransfersMeta impliedTransfersMeta;
    @Mock
    private ImpliedTransfers impliedTransfers;
    @Mock
    private TransferLogic transferLogic;
    @Mock
    private HederaTokenStore hederaTokenStore;
    @Mock
    private HTSPrecompiledContract.TransferLogicFactory transferLogicFactory;
    @Mock
    private HTSPrecompiledContract.HederaTokenStoreFactory hederaTokenStoreFactory;
    @Mock
    private FeeObject mockFeeObject;
    @Mock
    private ContractAliases aliases;
    @Mock
    private TxnReceipt.Builder txnReceipt;
    @Mock
    private UsagePricesProvider resourceCosts;

    private HTSPrecompiledContract subject;
    private final EntityIdSource ids = NOOP_ID_SOURCE;

    @BeforeEach
    void setUp() {
        subject = new HTSPrecompiledContract(
                validator, dynamicProperties, gasCalculator,
                recordsHistorian, sigsVerifier, decoder, encoder,
                syntheticTxnFactory, creator, dissociationFactory, impliedTransfersMarshal,
                () -> feeCalculator, stateView, precompilePricingUtils, resourceCosts);
        subject.setTransferLogicFactory(transferLogicFactory);
        subject.setTokenStoreFactory(tokenStoreFactory);
        subject.setHederaTokenStoreFactory(hederaTokenStoreFactory);
        subject.setAccountStoreFactory(accountStoreFactory);
        subject.setSideEffectsFactory(() -> sideEffects);
    }

    @Test
    void invalidNestedFunctionSelector () {
        givenMinimalFrameContextWithoutParentUpdater();

        given(frame.isStatic()).willReturn(true);
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(nestedPretendArguments.getInt(0)).willReturn(0);

        MockedStatic<EntityIdUtils> entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils.when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray())).thenReturn(token);
        entityIdUtils.when(() -> EntityIdUtils.contractIdFromEvmAddress(Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()))
                .thenReturn(precompiledContract);

        given(tokens.get(token, TOKEN_TYPE)).willReturn(TokenType.FUNGIBLE_COMMON);

        subject.initializeLedgers(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        final var result = subject.compute(pretendArguments, frame);
        assertNull(result);
    }

    @Test
    void name() {
        givenMinimalFrameContext();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(nestedPretendArguments.getInt(0)).willReturn(ABI_ID_NAME);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(encoder.encodeName(any())).willReturn(successResult);

        MockedStatic<EntityIdUtils> entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils.when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray())).thenReturn(token);
        entityIdUtils.when(() -> EntityIdUtils.contractIdFromEvmAddress(Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()))
                .thenReturn(precompiledContract);

        given(tokens.get(token, TOKEN_TYPE)).willReturn(TokenType.FUNGIBLE_COMMON);
        // when:
        subject.initializeLedgers(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.computeGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        entityIdUtils.close();
    }

    @Test
    void symbol() {
        givenMinimalFrameContext();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(nestedPretendArguments.getInt(0)).willReturn(ABI_ID_SYMBOL);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(encoder.encodeSymbol(any())).willReturn(successResult);

        MockedStatic<EntityIdUtils> entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils.when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray())).thenReturn(token);
        entityIdUtils.when(() -> EntityIdUtils.contractIdFromEvmAddress(Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()))
                .thenReturn(precompiledContract);

        given(tokens.get(token, TOKEN_TYPE)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.initializeLedgers(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.computeGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        entityIdUtils.close();
    }

    @Test
    void decimals() {
        givenMinimalFrameContext();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(nestedPretendArguments.getInt(0)).willReturn(ABI_ID_DECIMALS);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);

        given(tokens.get(any(), any())).willReturn(10);
        given(encoder.encodeDecimals(10)).willReturn(successResult);

        MockedStatic<EntityIdUtils> entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils.when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray())).thenReturn(token);
        entityIdUtils.when(() -> EntityIdUtils.contractIdFromEvmAddress(Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()))
                .thenReturn(precompiledContract);

        given(tokens.get(token, TOKEN_TYPE)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.initializeLedgers(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.computeGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        entityIdUtils.close();
    }

    @Test
    void totalSupply() {
        givenMinimalFrameContext();
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(nestedPretendArguments.getInt(0)).willReturn(ABI_ID_TOTAL_SUPPLY_TOKEN);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);

        given(tokens.get(any(), any())).willReturn(10L);
        given(encoder.encodeTotalSupply(10L)).willReturn(successResult);

        MockedStatic<EntityIdUtils> entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils.when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray())).thenReturn(token);
        entityIdUtils.when(() -> EntityIdUtils.contractIdFromEvmAddress(Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()))
                .thenReturn(precompiledContract);

        given(tokens.get(token, TOKEN_TYPE)).willReturn(TokenType.FUNGIBLE_COMMON);

        // when:
        subject.initializeLedgers(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.computeGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        entityIdUtils.close();
    }

    @Test
    void balanceOf() {
        givenMinimalFrameContext();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(nestedPretendArguments.getInt(0)).willReturn(ABI_ID_BALANCE_OF_TOKEN);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);

        given(decoder.decodeBalanceOf(eq(nestedPretendArguments), any())).willReturn(
                BALANCE_OF_WRAPPER);
        given(tokenRels.get(any(), any())).willReturn(10L);
        given(encoder.encodeBalance(10L)).willReturn(successResult);

        MockedStatic<EntityIdUtils> entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils.when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray())).thenReturn(token);
        entityIdUtils.when(() -> EntityIdUtils.contractIdFromEvmAddress(Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()))
                .thenReturn(precompiledContract);

        given(tokens.get(token, TOKEN_TYPE)).willReturn(TokenType.FUNGIBLE_COMMON);
        // when:
        subject.initializeLedgers(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.computeGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        entityIdUtils.close();
    }

    @Test
    void transfer() {
        givenMinimalFrameContext();
        givenLedgers();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(TOKEN_TRANSFER_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(nestedPretendArguments.getInt(0)).willReturn(ABI_ID_ERC_TRANSFER);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody)).willReturn(OK);
        given(sigsVerifier.hasActiveKey(any(), any(), any(), any(), any())).willReturn(true);
        given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(any(), any(), any(), any(), any())).willReturn(true, true);

        given(hederaTokenStoreFactory.newHederaTokenStore(
                ids, validator, sideEffects, NOOP_VIEWS_MANAGER, dynamicProperties, tokenRels, nfts, tokens
        )).willReturn(hederaTokenStore);

        given(transferLogicFactory.newLogic(
                accounts, nfts, tokenRels, hederaTokenStore,
                sideEffects,
                NOOP_VIEWS_MANAGER,
                dynamicProperties,
                validator,
                null,
                recordsHistorian
        )).willReturn(transferLogic);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().setCryptoTransfer(cryptoTransferTransactionBody).build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(decoder.decodeErcTransfer(eq(nestedPretendArguments), any(), any(), any())).willReturn(
                Collections.singletonList(TOKEN_TRANSFER_WRAPPER));

        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);

        MockedStatic<EntityIdUtils> entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils.when(() -> EntityIdUtils.accountIdFromEvmAddress(senderAddress)).thenReturn(sender);
        entityIdUtils.when(() -> EntityIdUtils.contractIdFromEvmAddress(Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()))
                .thenReturn(precompiledContract);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(sender)).thenReturn(senderAddress);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(receiver)).thenReturn(recipientAddress);
        entityIdUtils.when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray())).thenReturn(token);

        given(tokens.get(token, TOKEN_TYPE)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(encoder.ercFungibleTransfer(true)).willReturn(successResult);
        given(mockRecordBuilder.getReceiptBuilder()).willReturn(txnReceipt);
        given(txnReceipt.getStatus()).willReturn(SUCCESS_LITERAL);
        // when:
        subject.initializeLedgers(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.computeGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);

        entityIdUtils.close();
    }

    @Test
    void transferFrom() {
        givenMinimalFrameContext();
        givenLedgers();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(TOKEN_TRANSFER_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(nestedPretendArguments.getInt(0)).willReturn(ABI_ID_ERC_TRANSFER_FROM);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody)).willReturn(OK);
        given(sigsVerifier.hasActiveKey(any(), any(), any(), any(), any())).willReturn(true);
        given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(any(), any(), any(), any(), any())).willReturn(true, true);

        given(hederaTokenStoreFactory.newHederaTokenStore(
                ids, validator, sideEffects, NOOP_VIEWS_MANAGER, dynamicProperties, tokenRels, nfts, tokens
        )).willReturn(hederaTokenStore);

        given(transferLogicFactory.newLogic(
                accounts, nfts, tokenRels, hederaTokenStore,
                sideEffects,
                NOOP_VIEWS_MANAGER,
                dynamicProperties,
                validator,
                null,
                recordsHistorian
        )).willReturn(transferLogic);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().setCryptoTransfer(cryptoTransferTransactionBody).build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);

        given(decoder.decodeERCTransferFrom(eq(nestedPretendArguments), any(), eq(true), any())).willReturn(
                Collections.singletonList(TOKEN_TRANSFER_WRAPPER));

        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);

        MockedStatic<EntityIdUtils> entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils.when(() -> EntityIdUtils.accountIdFromEvmAddress(senderAddress)).thenReturn(sender);
        entityIdUtils.when(() -> EntityIdUtils.contractIdFromEvmAddress(Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()))
                .thenReturn(precompiledContract);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(sender)).thenReturn(senderAddress);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(receiver)).thenReturn(recipientAddress);
        entityIdUtils.when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray())).thenReturn(token);

        given(tokens.get(token, TOKEN_TYPE)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(encoder.ercFungibleTransfer(true)).willReturn(successResult);
        given(mockRecordBuilder.getReceiptBuilder()).willReturn(txnReceipt);
        given(txnReceipt.getStatus()).willReturn(SUCCESS_LITERAL);
        // when:
        subject.initializeLedgers(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.computeGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);

        entityIdUtils.close();
    }

    private void givenMinimalFrameContext() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers()).willReturn(wrappedLedgers);
        given(pretendArguments.getInt(0)).willReturn(ABI_ID_REDIRECT_FOR_TOKEN);
        given(pretendArguments.slice(4, 20)).willReturn(fungibleTokenAddr);
        given(pretendArguments.slice(24)).willReturn(nestedPretendArguments);
    }

    private void givenMinimalFrameContextWithoutParentUpdater() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers()).willReturn(wrappedLedgers);
        given(pretendArguments.getInt(0)).willReturn(ABI_ID_REDIRECT_FOR_TOKEN);
        given(pretendArguments.slice(4, 20)).willReturn(fungibleTokenAddr);
        given(pretendArguments.slice(24)).willReturn(nestedPretendArguments);
    }

    private void givenLedgers() {
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
    }

    public static final BalanceOfWrapper BALANCE_OF_WRAPPER = new BalanceOfWrapper(sender);

    public static final TokenTransferWrapper TOKEN_TRANSFER_WRAPPER = new TokenTransferWrapper(
            new ArrayList<>() {},
            List.of(new SyntheticTxnFactory.FungibleTokenTransfer(AMOUNT, token, null, receiver),
                    new SyntheticTxnFactory.FungibleTokenTransfer(-AMOUNT, token, sender, null))
    );
}