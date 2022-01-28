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
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static com.hedera.services.ledger.ids.ExceptionalEntityIdSource.NOOP_ID_SOURCE;
import static com.hedera.services.state.expiry.ExpiringCreations.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_DECIMALS;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_NAME;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_SYMBOL;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TOTAL_SUPPLY_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.BALANCE_OF;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TOKEN_TRANSFER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.balanceOfOp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokensTransferChangesSenderOnly;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokensTransferListReceiverOnly;
import static com.hedera.services.store.tokens.views.UniqueTokenViewsManager.NOOP_VIEWS_MANAGER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ERC20PrecompilesTest {
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
    private AbstractLedgerWorldUpdater worldUpdater;
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
    private final EntityIdSource ids = NOOP_ID_SOURCE;


    private HTSPrecompiledContract subject;

    @BeforeEach
    void setUp() {
        subject = new HTSPrecompiledContract(
                validator, dynamicProperties, gasCalculator,
                recordsHistorian, sigsVerifier, decoder, encoder,
                syntheticTxnFactory, creator, dissociationFactory, impliedTransfersMarshal,
                () -> feeCalculator, stateView, precompilePricingUtils);
        subject.setTokenStoreFactory(tokenStoreFactory);
        subject.setAccountStoreFactory(accountStoreFactory);
        subject.setSideEffectsFactory(() -> sideEffects);
    }

    @Test
    void name() {
        givenMinimalFrameContext();
        givenLedgers();
        given(pretendArguments.slice(24)).willReturn(Bytes.fromHexString("0" + Integer.toHexString(ABI_ID_NAME)));

        // when:
        subject.prepareComputation(pretendArguments);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void symbol() {
        givenMinimalFrameContext();
        givenLedgers();
        given(pretendArguments.slice(24)).willReturn(Bytes.fromHexString(Integer.toHexString(ABI_ID_SYMBOL)));

        // when:
        subject.prepareComputation(pretendArguments);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);

    }

    @Test
    void decimals() {
        givenMinimalFrameContext();
        givenLedgers();
        given(pretendArguments.slice(24)).willReturn(Bytes.fromHexString(Integer.toHexString(ABI_ID_DECIMALS)));

        // when:
        subject.prepareComputation(pretendArguments);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);

    }

    @Test
    void totalSupply() {
        givenMinimalFrameContext();
        givenLedgers();
        given(pretendArguments.slice(24)).willReturn(Bytes.fromHexString(Integer.toHexString(ABI_ID_TOTAL_SUPPLY_TOKEN)));

        // when:
        subject.prepareComputation(pretendArguments);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);

    }

    @Test
    void balanceOf() {
        givenMinimalFrameContext();
        givenLedgers();
        given(pretendArguments.slice(24)).willReturn(BALANCE_OF);
        given(decoder.decodeBalanceOf(pretendArguments)).willReturn(balanceOfOp);

        // when:
        subject.prepareComputation(pretendArguments);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);

    }

//    @Test
//    void transfer() {
//        subject.setTransferLogicFactory(transferLogicFactory);
//        givenMinimalFrameContext();
//        givenLedgers();
//        given(pretendArguments.slice(24)).willReturn(TOKEN_TRANSFER);
//
//
//        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(tokensTransferListReceiverOnly))).willReturn(mockSynthBodyBuilder);
//        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
//        given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(any(), any(), any(), any())).willReturn(true);
//        given(decoder.decodeTransferTokens(pretendArguments)).willReturn(Collections.singletonList(tokensTransferListReceiverOnly));
//        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody)).willReturn(OK);
//
//        given(hederaTokenStoreFactory.newHederaTokenStore(
//                ids, validator, sideEffects, NOOP_VIEWS_MANAGER, dynamicProperties, tokenRels, nfts, tokens
//        )).willReturn(hederaTokenStore);
//
//        given(transferLogicFactory.newLogic(
//                accounts, nfts, tokenRels, hederaTokenStore,
//                sideEffects,
//                NOOP_VIEWS_MANAGER,
//                dynamicProperties,
//                validator,
//                null,
//                recordsHistorian
//        )).willReturn(transferLogic);
//        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
//                .willReturn(mockRecordBuilder);
//        given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), any(), any(), any()))
//                .willReturn(impliedTransfers);
//        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokensTransferChangesSenderOnly);
//        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
//        given(impliedTransfersMeta.code()).willReturn(OK);
//
//        // when:
//        subject.prepareComputation(pretendArguments);
//        final var result = subject.computeInternal(frame);
//
//        // then:
//        assertEquals(successResult, result);
//        // and:
//        verify(transferLogic).doZeroSum(tokensTransferChangesSenderOnly);
//        verify(wrappedLedgers).commit();
//        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
//
//    }

    private void givenMinimalFrameContext() {
        //given(frame.getContractAddress()).willReturn(contractAddr);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers()).willReturn(wrappedLedgers);
        given(pretendArguments.getInt(0)).willReturn(ABI_ID_REDIRECT_FOR_TOKEN);
        given(pretendArguments.slice(4, 20)).willReturn(fungibleTokenAddr);
    }

    private void givenLedgers() {
//        given(wrappedLedgers.accounts()).willReturn(accounts);
//        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
//        given(wrappedLedgers.nfts()).willReturn(nfts);
//        given(wrappedLedgers.tokens()).willReturn(tokens);
    }

}
