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
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.TransactionalLedger;
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
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.txns.token.AssociateLogic;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
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
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;

import static com.hedera.services.state.expiry.ExpiringCreations.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_ASSOCIATE_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_ASSOCIATE_TOKENS;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.NOOP_TREASURY_ADDER;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.NOOP_TREASURY_REMOVER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.accountMerkleId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.associateOp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.invalidSigResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.multiAssociateOp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.parentContractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.parentRecipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenMerkleId;
import static com.hedera.services.store.tokens.views.UniqueTokenViewsManager.NOOP_VIEWS_MANAGER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
class AssociatePrecompileTest {
	@Mock
	private Bytes pretendArguments;
	@Mock
	private OptionValidator validator;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private GasCalculator gasCalculator;
	@Mock
	private AccountRecordsHistorian recordsHistorian;
	@Mock
	private TxnAwareSoliditySigsVerifier sigsVerifier;
	@Mock
	private DecodingFacade decoder;
	@Mock
	private EncodingFacade encoder;
	@Mock
	private SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	private ExpiringCreations creator;
	@Mock
	private DissociationFactory dissociationFactory;
	@Mock
	private AccountStore accountStore;
	@Mock
	private TypedTokenStore tokenStore;
	@Mock
	private AssociateLogic associateLogic;
	@Mock
	private HTSPrecompiledContract.AssociateLogicFactory associateLogicFactory;
	@Mock
	private HTSPrecompiledContract.TokenStoreFactory tokenStoreFactory;
	@Mock
	private HTSPrecompiledContract.AccountStoreFactory accountStoreFactory;
	@Mock
	private SideEffectsTracker sideEffects;
	@Mock
	private MessageFrame frame;
	@Mock
	private MessageFrame parentFrame;
	@Mock
	private Deque<MessageFrame> frameDeque;
	@Mock
	private Iterator<MessageFrame> dequeIterator;
	@Mock
	private AbstractLedgerWorldUpdater worldUpdater;
	@Mock
	private WorldLedgers wrappedLedgers;
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts;
	@Mock
	private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRels;
	@Mock
	private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nfts;
	@Mock
	private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
	@Mock
	private TransactionBody.Builder mockSynthBodyBuilder;
	@Mock
	private ExpirableTxnRecord.Builder mockRecordBuilder;
	@Mock
	private TokenAssociateTransactionBody transactionBody;
	@Mock
	private ImpliedTransfersMarshal impliedTransfersMarshal;

	private HTSPrecompiledContract subject;

	@BeforeEach
	void setUp() {
		subject = new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder, encoder,
				syntheticTxnFactory, creator, dissociationFactory, impliedTransfersMarshal);

		subject.setAssociateLogicFactory(associateLogicFactory);
		subject.setTokenStoreFactory(tokenStoreFactory);
		subject.setAccountStoreFactory(accountStoreFactory);
		subject.setSideEffectsFactory(() -> sideEffects);
	}

	@Test
	void computeAssociateTokenFailurePathWorks() {
		// given:
		givenCommonFrameContext();
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_ASSOCIATE_TOKEN);
		given(decoder.decodeAssociation(pretendArguments)).willReturn(associateOp);
		given(syntheticTxnFactory.createAssociate(associateOp)).willReturn(mockSynthBodyBuilder);
		given(sigsVerifier.hasActiveKey(
				Id.fromGrpcAccount(accountMerkleId).asEvmAddress(), contractAddress, contractAddress, senderAddress))
				.willReturn(false);
		given(creator.createUnsuccessfulSyntheticRecord(INVALID_SIGNATURE)).willReturn(mockRecordBuilder);

		// when:
		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(invalidSigResult, result);
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void computeAssociateTokenHappyPathWorksWithDelegateCall() {
		given(frame.getContractAddress()).willReturn(contractAddress);
		given(frame.getRecipientAddress()).willReturn(contractAddress);
		given(frame.getSenderAddress()).willReturn(senderAddress);
		given(frame.getWorldUpdater()).willReturn(worldUpdater);
		Optional<WorldUpdater> parent = Optional.of(worldUpdater);
		given(worldUpdater.parentUpdater()).willReturn(parent);
		given(worldUpdater.wrappedTrackingLedgers()).willReturn(wrappedLedgers);
		given(frame.getRecipientAddress()).willReturn(recipientAddress);
		givenLedgers();
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_ASSOCIATE_TOKEN);
		given(decoder.decodeAssociation(pretendArguments))
				.willReturn(associateOp);
		given(syntheticTxnFactory.createAssociate(associateOp))
				.willReturn(mockSynthBodyBuilder);
		given(sigsVerifier.hasActiveKey(
				Id.fromGrpcAccount(accountMerkleId).asEvmAddress(), recipientAddress, contractAddress, recipientAddress))
				.willReturn(true);
		given(accountStoreFactory.newAccountStore(validator, dynamicProperties, accounts))
				.willReturn(accountStore);
		given(tokenStoreFactory.newTokenStore(accountStore, tokens, nfts, tokenRels, NOOP_VIEWS_MANAGER,
				NOOP_TREASURY_ADDER, NOOP_TREASURY_REMOVER, sideEffects))
				.willReturn(tokenStore);
		given(associateLogicFactory.newAssociateLogic(tokenStore, accountStore, dynamicProperties))
				.willReturn(associateLogic);
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);

		// when:
		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		verify(associateLogic).associate(Id.fromGrpcAccount(accountMerkleId), Collections.singletonList(tokenMerkleId));
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void computeAssociateTokenHappyPathWorksWithDelegateCallFromParentFrame() {
		givenFrameContextWithDelegateCallFromParent();
		givenLedgers();
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_ASSOCIATE_TOKEN);
		given(decoder.decodeAssociation(pretendArguments))
				.willReturn(associateOp);
		given(syntheticTxnFactory.createAssociate(associateOp))
				.willReturn(mockSynthBodyBuilder);
		given(sigsVerifier.hasActiveKey(Id.fromGrpcAccount(accountMerkleId).asEvmAddress(), parentRecipientAddress, contractAddress,
				senderAddress))
				.willReturn(true);
		given(accountStoreFactory.newAccountStore(validator, dynamicProperties, accounts))
				.willReturn(accountStore);
		given(tokenStoreFactory.newTokenStore(accountStore, tokens, nfts, tokenRels, NOOP_VIEWS_MANAGER,
				NOOP_TREASURY_ADDER, NOOP_TREASURY_REMOVER, sideEffects))
				.willReturn(tokenStore);
		given(associateLogicFactory.newAssociateLogic(tokenStore, accountStore, dynamicProperties))
				.willReturn(associateLogic);
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);

		// when:
		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		verify(associateLogic).associate(Id.fromGrpcAccount(accountMerkleId), Collections.singletonList(tokenMerkleId));
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void computeAssociateTokenHappyPathWorksWithEmptyMessageFrameStack() {
		givenFrameContextWithEmptyMessageFrameStack();
		givenLedgers();
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_ASSOCIATE_TOKEN);
		given(decoder.decodeAssociation(pretendArguments))
				.willReturn(associateOp);
		given(syntheticTxnFactory.createAssociate(associateOp))
				.willReturn(mockSynthBodyBuilder);
		given(sigsVerifier.hasActiveKey(Id.fromGrpcAccount(accountMerkleId).asEvmAddress(), contractAddress, contractAddress,
				senderAddress))
				.willReturn(true);
		given(accountStoreFactory.newAccountStore(validator, dynamicProperties, accounts))
				.willReturn(accountStore);
		given(tokenStoreFactory.newTokenStore(accountStore, tokens, nfts, tokenRels, NOOP_VIEWS_MANAGER,
				NOOP_TREASURY_ADDER, NOOP_TREASURY_REMOVER, sideEffects))
				.willReturn(tokenStore);
		given(associateLogicFactory.newAssociateLogic(tokenStore, accountStore, dynamicProperties))
				.willReturn(associateLogic);
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);

		// when:
		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		verify(associateLogic).associate(Id.fromGrpcAccount(accountMerkleId), Collections.singletonList(tokenMerkleId));
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void computeAssociateTokenHappyPathWorksWithoutParentFrame() {
		givenFrameContextWithoutParentFrame();
		givenLedgers();
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_ASSOCIATE_TOKEN);
		given(decoder.decodeAssociation(pretendArguments))
				.willReturn(associateOp);
		given(syntheticTxnFactory.createAssociate(associateOp))
				.willReturn(mockSynthBodyBuilder);
		given(sigsVerifier.hasActiveKey(Id.fromGrpcAccount(accountMerkleId).asEvmAddress(), contractAddress, contractAddress,
				senderAddress))
				.willReturn(true);
		given(accountStoreFactory.newAccountStore(validator, dynamicProperties, accounts))
				.willReturn(accountStore);
		given(tokenStoreFactory.newTokenStore(accountStore, tokens, nfts, tokenRels, NOOP_VIEWS_MANAGER,
				NOOP_TREASURY_ADDER, NOOP_TREASURY_REMOVER, sideEffects))
				.willReturn(tokenStore);
		given(associateLogicFactory.newAssociateLogic(tokenStore, accountStore, dynamicProperties))
				.willReturn(associateLogic);
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);

		// when:
		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		verify(associateLogic).associate(Id.fromGrpcAccount(accountMerkleId), Collections.singletonList(tokenMerkleId));
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void computeMultiAssociateTokenHappyPathWorks() {
		givenCommonFrameContext();
		givenLedgers();
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_ASSOCIATE_TOKENS);
		given(decoder.decodeMultipleAssociations(pretendArguments))
				.willReturn(multiAssociateOp);
		given(syntheticTxnFactory.createAssociate(multiAssociateOp))
				.willReturn(mockSynthBodyBuilder);
		given(transactionBody.getTokensCount()).willReturn(1);
		given(mockSynthBodyBuilder.getTokenAssociate()).willReturn(transactionBody);
		given(sigsVerifier.hasActiveKey(
				Id.fromGrpcAccount(accountMerkleId).asEvmAddress(), contractAddress, contractAddress, senderAddress))
				.willReturn(true);
		given(accountStoreFactory.newAccountStore(validator, dynamicProperties, accounts))
				.willReturn(accountStore);
		given(tokenStoreFactory.newTokenStore(accountStore, tokens, nfts, tokenRels, NOOP_VIEWS_MANAGER,
				NOOP_TREASURY_ADDER, NOOP_TREASURY_REMOVER, sideEffects))
				.willReturn(tokenStore);
		given(associateLogicFactory.newAssociateLogic(tokenStore, accountStore, dynamicProperties))
				.willReturn(associateLogic);
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);

		// when:
		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		verify(associateLogic).associate(Id.fromGrpcAccount(accountMerkleId), multiAssociateOp.tokenIds());
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	private void givenFrameContext() {
		given(parentFrame.getContractAddress()).willReturn(parentContractAddress);
		given(parentFrame.getRecipientAddress()).willReturn(parentContractAddress);
		givenCommonFrameContext();
		given(frame.getRecipientAddress()).willReturn(recipientAddress);
		given(frame.getMessageFrameStack().descendingIterator().hasNext()).willReturn(true);
		given(frame.getMessageFrameStack().descendingIterator().next()).willReturn(parentFrame);
	}

	private void givenFrameContextWithDelegateCallFromParent() {
		given(parentFrame.getContractAddress()).willReturn(parentContractAddress);
		given(parentFrame.getRecipientAddress()).willReturn(parentRecipientAddress);
		givenCommonFrameContext();
		given(frame.getMessageFrameStack().descendingIterator().hasNext()).willReturn(true);
		given(frame.getMessageFrameStack().descendingIterator().next()).willReturn(parentFrame);
	}

	private void givenFrameContextWithEmptyMessageFrameStack() {
		givenCommonFrameContext();
		given(frame.getMessageFrameStack().descendingIterator().hasNext()).willReturn(false);
	}

	private void givenFrameContextWithoutParentFrame() {
		givenCommonFrameContext();
		given(frame.getMessageFrameStack().descendingIterator().hasNext()).willReturn(true, false);
	}

	private void givenCommonFrameContext() {
		given(frame.getContractAddress()).willReturn(contractAddress);
		given(frame.getRecipientAddress()).willReturn(contractAddress);
		given(frame.getSenderAddress()).willReturn(senderAddress);
		given(frame.getMessageFrameStack()).willReturn(frameDeque);
		given(frame.getMessageFrameStack().descendingIterator()).willReturn(dequeIterator);
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