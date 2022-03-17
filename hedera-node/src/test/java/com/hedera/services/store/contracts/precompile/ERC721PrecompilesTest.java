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
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.NftId;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeObject;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.ledger.ids.ExceptionalEntityIdSource.NOOP_ID_SOURCE;
import static com.hedera.services.ledger.properties.TokenProperty.TOKEN_TYPE;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_BALANCE_OF_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_DECIMALS;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_ERC_TRANSFER;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_NAME;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_OWNER_OF_NFT;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_SYMBOL;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TOKEN_URI_NFT;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TOTAL_SUPPLY_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nonFungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.ownerOfAndTokenUriWrapper;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.precompiledContract;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.receiver;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ERC721PrecompilesTest {
    @Mock
    private Bytes pretendArguments;
    @Mock
    private GlobalDynamicProperties dynamicProperties;
    @Mock
    private OptionValidator validator;
    @Mock
    private GasCalculator gasCalculator;
    @Mock
    private MessageFrame frame;
    @Mock
    private TxnAwareEvmSigsVerifier sigsVerifier;
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
    private HTSPrecompiledContract.TransferLogicFactory transferLogicFactory;
    @Mock
    private HTSPrecompiledContract.HederaTokenStoreFactory hederaTokenStoreFactory;
    @Mock
    private Bytes nestedPretendArguments;
    @Mock
    private FeeObject mockFeeObject;
    @Mock
    private UsagePricesProvider resourceCosts;

    private HTSPrecompiledContract subject;
    private MockedStatic<EntityIdUtils> entityIdUtils;

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
        entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils.when(() -> EntityIdUtils.tokenIdFromEvmAddress(nonFungibleTokenAddr.toArray())).thenReturn(token);
        entityIdUtils.when(() -> EntityIdUtils.contractIdFromEvmAddress(Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()))
                .thenReturn(precompiledContract);
        entityIdUtils.when(() -> EntityIdUtils.accountIdFromEvmAddress(senderAddress)).thenReturn(sender);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(sender)).thenReturn(senderAddress);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(receiver)).thenReturn(recipientAddress);
    }

    @AfterEach
    void closeMocks() {
        entityIdUtils.close();
    }

    @Test
    void name() {
        givenMinimalFrameContext();
        given(pretendArguments.slice(24)).willReturn(Bytes.fromHexString("0" + Integer.toHexString(ABI_ID_NAME)));
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(encoder.encodeName(any())).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void symbol() {
        givenMinimalFrameContext();
        given(pretendArguments.slice(24)).willReturn(Bytes.fromHexString(Integer.toHexString(ABI_ID_SYMBOL)));
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(encoder.encodeSymbol(any())).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void totalSupply() {
        givenMinimalFrameContext();
        given(pretendArguments.slice(24)).willReturn(Bytes.fromHexString(Integer.toHexString(ABI_ID_TOTAL_SUPPLY_TOKEN)));
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(tokens.get(any(), any())).willReturn(10L);
        given(encoder.encodeTotalSupply(10L)).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
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
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(decoder.decodeBalanceOf(eq(nestedPretendArguments), any())).willReturn(
                BALANCE_OF_WRAPPER);
        given(tokenRels.exists(any())).willReturn(true);
        given(tokenRels.get(any(), any())).willReturn(10L);
        given(encoder.encodeBalance(10L)).willReturn(successResult);

        given(tokens.get(token, TOKEN_TYPE)).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);

        // then:
        assertEquals(successResult, subject.computeInternal(frame));
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);

        // when:
        given(tokenRels.exists(any())).willReturn(false);
        given(encoder.encodeBalance(0L)).willReturn(successResult);

        // then:
        assertEquals(successResult, subject.computeInternal(frame));
        verify(encoder).encodeBalance(0L);
    }

    @Test
    void ownerOf() {
        givenMinimalFrameContext();
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(nestedPretendArguments.getInt(0)).willReturn(ABI_ID_OWNER_OF_NFT);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(decoder.decodeOwnerOf(nestedPretendArguments)).willReturn(ownerOfAndTokenUriWrapper);
        given(nfts.get(any(), eq(NftProperty.OWNER))).willReturn(EntityId.fromGrpcAccountId(sender));
        given(encoder.encodeOwner(EntityIdUtils.asTypedEvmAddress(sender))).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void tokenURI() {
        givenMinimalFrameContext();

        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(nestedPretendArguments.getInt(0)).willReturn(ABI_ID_TOKEN_URI_NFT);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(decoder.decodeTokenUriNFT(nestedPretendArguments)).willReturn(ownerOfAndTokenUriWrapper);
        given(nfts.get(any(), eq(NftProperty.METADATA))).willReturn("Metadata".getBytes());
        given(encoder.encodeTokenUri("Metadata")).willReturn(successResult);
        given(nfts.exists(any())).willReturn(true);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void defaultErrorMsgReturnedForMissingTokenURI() {
        givenMinimalFrameContext();

        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(nestedPretendArguments.getInt(0)).willReturn(ABI_ID_TOKEN_URI_NFT);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee())
                .willReturn(1L);
        given(mockFeeObject.getNetworkFee())
                .willReturn(1L);
        given(mockFeeObject.getServiceFee())
                .willReturn(1L);
        given(decoder.decodeTokenUriNFT(nestedPretendArguments)).willReturn(ownerOfAndTokenUriWrapper);
        given(nfts.exists(any())).willReturn(false);
        given(encoder.encodeTokenUri("ERC721Metadata: URI query for nonexistent token")).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.computeViewFunctionGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void transferNotSupported() {
        givenMinimalFrameContextWithoutParentUpdater();
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(nestedPretendArguments.getInt(0)).willReturn(ABI_ID_ERC_TRANSFER);
        given(tokens.get(token, TOKEN_TYPE)).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        subject.prepareFields(frame);

        final var exception = assertThrows(InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArguments, а -> а));
        assertEquals(NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON, exception.getMessage());
    }

    @Test
    void decimalsNotSupported() {
        givenMinimalFrameContextWithoutParentUpdater();
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(nestedPretendArguments.getInt(0)).willReturn(ABI_ID_DECIMALS);
        given(tokens.get(token, TOKEN_TYPE)).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        subject.prepareFields(frame);

        final var exception = assertThrows(InvalidTransactionException.class,
                () -> subject.prepareComputation(pretendArguments, а -> а));
        assertEquals(NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON, exception.getMessage());
    }

    private void givenMinimalFrameContext() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(pretendArguments.getInt(0)).willReturn(ABI_ID_REDIRECT_FOR_TOKEN);
        given(pretendArguments.slice(4, 20)).willReturn(nonFungibleTokenAddr);
        given(pretendArguments.slice(24)).willReturn(nestedPretendArguments);
    }

    private void givenMinimalFrameContextWithoutParentUpdater() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(pretendArguments.getInt(0)).willReturn(ABI_ID_REDIRECT_FOR_TOKEN);
        given(pretendArguments.slice(4, 20)).willReturn(nonFungibleTokenAddr);
        given(pretendArguments.slice(24)).willReturn(nestedPretendArguments);
    }

    public static final BalanceOfWrapper BALANCE_OF_WRAPPER = new BalanceOfWrapper(sender);
    public static final TokenTransferWrapper TOKEN_TRANSFER_WRAPPER = new TokenTransferWrapper(
            List.of(new SyntheticTxnFactory.NftExchange(1, token, sender, receiver)),
            new ArrayList<>() {}
    );

    private void givenLedgers() {
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
    }
}