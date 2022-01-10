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
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareSoliditySigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
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
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static com.hedera.services.ledger.ids.ExceptionalEntityIdSource.NOOP_ID_SOURCE;
import static com.hedera.services.state.expiry.ExpiringCreations.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_CRYPTO_TRANSFER;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TRANSFER_NFT;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TRANSFER_NFTS;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TRANSFER_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TRANSFER_TOKENS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TOKEN_TRANSFER_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.feeCollector;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nftTransferChanges;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nftTransferList;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nftsTransferChanges;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nftsTransferList;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.receiver;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenTransferChanges;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokensTransferChanges;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokensTransferChangesSenderOnly;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokensTransferList;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokensTransferListReceiverOnly;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokensTransferListSenderOnly;
import static com.hedera.services.store.tokens.views.UniqueTokenViewsManager.NOOP_VIEWS_MANAGER;
import static com.hedera.services.utils.EntityIdUtils.asTypedSolidityAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransferPrecompilesTest {
	@Mock
	private Bytes pretendArguments;
	@Mock
	private HederaTokenStore hederaTokenStore;
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
	private HTSPrecompiledContract.TransferLogicFactory transferLogicFactory;
	@Mock
	private HTSPrecompiledContract.HederaTokenStoreFactory hederaTokenStoreFactory;
	@Mock
	private HTSPrecompiledContract.AccountStoreFactory accountStoreFactory;
	@Mock
	private TransferLogic transferLogic;
	@Mock
	private SideEffectsTracker sideEffects;
	@Mock
	private TransactionBody.Builder mockSynthBodyBuilder;
	@Mock
	private CryptoTransferTransactionBody cryptoTransferTransactionBody;
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
	private final EntityIdSource ids = NOOP_ID_SOURCE;
	@Mock
	private ExpiringCreations creator;
	@Mock
	private ImpliedTransfersMarshal impliedTransfersMarshal;
	@Mock
	private ImpliedTransfers impliedTransfers;
	@Mock
	private DissociationFactory dissociationFactory;
	@Mock
	private ImpliedTransfersMeta impliedTransfersMeta;

	private HTSPrecompiledContract subject;

	@BeforeEach
	void setUp() {
		subject = new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder, encoder,
				syntheticTxnFactory, creator, dissociationFactory, impliedTransfersMarshal);
		subject.setTransferLogicFactory(transferLogicFactory);
		subject.setHederaTokenStoreFactory(hederaTokenStoreFactory);
		subject.setAccountStoreFactory(accountStoreFactory);
		subject.setSideEffectsFactory(() -> sideEffects);
	}

	@Test
	void transferTokenHappyPathWorks() {
		givenFrameContext();
		givenLedgers();

		given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(tokensTransferList)))
				.willReturn(mockSynthBodyBuilder);
		given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
		given(cryptoTransferTransactionBody.getTokenTransfersCount()).willReturn(1);
		given(sigsVerifier.hasActiveKey(any(), any(), any())).willReturn(true);
		given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(any(), any(), any())).willReturn(true);
		given(decoder.decodeTransferTokens(pretendArguments)).willReturn(Collections.singletonList(tokensTransferList));

		hederaTokenStore.setAccountsLedger(accounts);
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
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);
		given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), any(), any(), any()))
				.willReturn(impliedTransfers);
		given(impliedTransfers.getAllBalanceChanges()).willReturn(tokensTransferChanges);
		given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
		given(impliedTransfersMeta.code()).willReturn(ResponseCodeEnum.OK);
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_TRANSFER_TOKENS);

		// when:
		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		// and:
		verify(transferLogic).doZeroSum(tokensTransferChanges);
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void transferTokenWithSenderOnlyHappyPathWorks() {
		givenFrameContext();
		givenLedgers();

		given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(tokensTransferListSenderOnly)))
				.willReturn(mockSynthBodyBuilder);
		given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
		given(cryptoTransferTransactionBody.getTokenTransfersCount()).willReturn(1);
		given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(any(), any(), any())).willReturn(true);
		given(decoder.decodeTransferTokens(pretendArguments))
				.willReturn(Collections.singletonList(tokensTransferListSenderOnly));

		hederaTokenStore.setAccountsLedger(accounts);
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
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);
		given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), any(), any(), any()))
				.willReturn(impliedTransfers);
		given(impliedTransfers.getAllBalanceChanges()).willReturn(tokensTransferChangesSenderOnly);
		given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
		given(impliedTransfersMeta.code()).willReturn(ResponseCodeEnum.OK);
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_TRANSFER_TOKENS);

		// when:
		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		// and:
		verify(transferLogic).doZeroSum(tokensTransferChangesSenderOnly);
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void transferTokenWithReceiverOnlyHappyPathWorks() {
		givenFrameContext();
		givenLedgers();

		given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(tokensTransferListReceiverOnly)))
				.willReturn(mockSynthBodyBuilder);
		given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
		given(cryptoTransferTransactionBody.getTokenTransfersCount()).willReturn(1);
		given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(any(), any(), any())).willReturn(true);
		given(decoder.decodeTransferTokens(pretendArguments))
				.willReturn(Collections.singletonList(tokensTransferListReceiverOnly));

		hederaTokenStore.setAccountsLedger(accounts);
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
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);
		given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), any(), any(), any()))
				.willReturn(impliedTransfers);
		given(impliedTransfers.getAllBalanceChanges()).willReturn(tokensTransferChangesSenderOnly);
		given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
		given(impliedTransfersMeta.code()).willReturn(ResponseCodeEnum.OK);
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_TRANSFER_TOKENS);

		// when:
		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		// and:
		verify(transferLogic).doZeroSum(tokensTransferChangesSenderOnly);
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void transferNftsHappyPathWorks() {
		givenFrameContext();
		givenLedgers();

		given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(nftsTransferList)))
				.willReturn(mockSynthBodyBuilder);
		given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
		given(cryptoTransferTransactionBody.getTokenTransfersCount()).willReturn(1);
		given(sigsVerifier.hasActiveKey(any(), any(), any())).willReturn(true);
		given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(any(), any(), any())).willReturn(true);
		given(decoder.decodeTransferNFTs(pretendArguments)).willReturn(Collections.singletonList(nftsTransferList));

		hederaTokenStore.setAccountsLedger(accounts);
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
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);
		given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), any(), any(), any()))
				.willReturn(impliedTransfers);
		given(impliedTransfers.getAllBalanceChanges()).willReturn(nftsTransferChanges);
		given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
		given(impliedTransfersMeta.code()).willReturn(ResponseCodeEnum.OK);
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_TRANSFER_NFTS);

		// when:
		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		// and:
		verify(transferLogic).doZeroSum(nftsTransferChanges);
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void transferNftHappyPathWorks() {
		final var recipientAddr = Address.ALTBN128_ADD;
		final var senderId = Id.fromGrpcAccount(sender);
		final var receiverId = Id.fromGrpcAccount(receiver);
		givenFrameContext();
		given(frame.getRecipientAddress()).willReturn(recipientAddr);
		givenLedgers();

		given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(nftTransferList)))
				.willReturn(mockSynthBodyBuilder);
		given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
		given(cryptoTransferTransactionBody.getTokenTransfersCount()).willReturn(1);
		given(sigsVerifier.hasActiveKey(any(), any(), any())).willReturn(true);
		given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(any(), any(), any())).willReturn(true);
		given(decoder.decodeTransferNFT(pretendArguments)).willReturn(Collections.singletonList(nftTransferList));

		hederaTokenStore.setAccountsLedger(accounts);
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
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);
		given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), any(), any(), any()))
				.willReturn(impliedTransfers);
		given(impliedTransfers.getAllBalanceChanges()).willReturn(nftTransferChanges);
		given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
		given(impliedTransfersMeta.code()).willReturn(ResponseCodeEnum.OK);
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_TRANSFER_NFT);

		// when:
		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		// and:
		verify(transferLogic).doZeroSum(nftTransferChanges);
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
		verify(sigsVerifier)
				.hasActiveKey(senderId, recipientAddr, contractAddr);
		verify(sigsVerifier)
				.hasActiveKeyOrNoReceiverSigReq(receiverId.asEvmAddress(), recipientAddr, contractAddr);
		verify(sigsVerifier)
				.hasActiveKey(receiverId, recipientAddr, contractAddr);
		verify(sigsVerifier, never())
				.hasActiveKeyOrNoReceiverSigReq(asTypedSolidityAddress(feeCollector), recipientAddr, contractAddr);
	}

	@Test
	void cryptoTransferHappyPathWorks() {
		givenFrameContext();
		givenLedgers();

		given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(nftTransferList)))
				.willReturn(mockSynthBodyBuilder);
		given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
		given(cryptoTransferTransactionBody.getTokenTransfersCount()).willReturn(1);
		given(sigsVerifier.hasActiveKey(any(), any(), any())).willReturn(true);
		given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(any(), any(), any())).willReturn(true);
		given(decoder.decodeCryptoTransfer(pretendArguments)).willReturn(Collections.singletonList(nftTransferList));

		hederaTokenStore.setAccountsLedger(accounts);
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
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);
		given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), any(), any(), any()))
				.willReturn(impliedTransfers);
		given(impliedTransfers.getAllBalanceChanges()).willReturn(nftTransferChanges);
		given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
		given(impliedTransfersMeta.code()).willReturn(ResponseCodeEnum.OK);
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_CRYPTO_TRANSFER);

		// when:
		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		// and:
		verify(transferLogic).doZeroSum(nftTransferChanges);
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}


	@Test
	void transferFailsAndCatchesProperly() {
		givenFrameContext();
		givenLedgers();

		given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(any(), any(), any())).willReturn(true);
		given(sigsVerifier.hasActiveKey(any(), any(), any())).willReturn(true);

		hederaTokenStore.setAccountsLedger(accounts);
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
		given(decoder.decodeTransferToken(pretendArguments)).willReturn(Collections.singletonList(TOKEN_TRANSFER_WRAPPER));
		given(impliedTransfersMarshal.assessCustomFeesAndValidate(anyInt(), anyInt(), any(), any(), any()))
				.willReturn(impliedTransfers);
		given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
		given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
		given(impliedTransfersMeta.code()).willReturn(ResponseCodeEnum.OK);
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_TRANSFER_TOKEN);
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(mockSynthBodyBuilder);
		given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
		given(cryptoTransferTransactionBody.getTokenTransfersCount()).willReturn(1);

		doThrow(new InvalidTransactionException(ResponseCodeEnum.FAIL_INVALID))
				.when(transferLogic)
				.doZeroSum(tokenTransferChanges);

		// when:
		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		// then:
		assertNotEquals(successResult, result);
		// and:
		verify(transferLogic).doZeroSum(tokenTransferChanges);
		verify(wrappedLedgers, never()).commit();
		verify(worldUpdater, never()).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void transferWithWrongInput() {
		given(decoder.decodeTransferToken(pretendArguments)).willThrow(new IndexOutOfBoundsException());
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_TRANSFER_TOKEN);

		assertThrows(InvalidTransactionException.class, () -> subject.gasRequirement(pretendArguments));
	}

	private void givenFrameContext() {
		given(frame.getContractAddress()).willReturn(contractAddr);
		given(frame.getWorldUpdater()).willReturn(worldUpdater);
		Optional<WorldUpdater> parent = Optional.of(worldUpdater);
		given(worldUpdater.parentUpdater()).willReturn(parent);
		given(worldUpdater.wrappedTrackingLedgers()).willReturn(wrappedLedgers);
	}

	private void givenLedgers() {
		given(wrappedLedgers.accounts()).willReturn(accounts);
		given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
		given(wrappedLedgers.nfts()).willReturn(nfts);
		given(wrappedLedgers.tokens()).willReturn(tokens);
	}
}