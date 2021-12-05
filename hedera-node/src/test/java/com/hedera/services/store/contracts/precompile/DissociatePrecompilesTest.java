package com.hedera.services.store.contracts.precompile;

/*-
 * ‌
 * Hedera Services Node
 *
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
 * ‍
 */

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.SoliditySigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
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
import com.hedera.services.txns.token.DissociateLogic;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.NOOP_TREASURY_ADDER;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.NOOP_TREASURY_REMOVER;
import static com.hedera.services.store.tokens.views.UniqTokenViewsManager.NOOP_VIEWS_MANAGER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
class DissociatePrecompilesTest {
	private static final Bytes pretendArguments = Bytes.fromBase64String("ABCDEF");

	@Mock
	private AccountStore accountStore;
	@Mock
	private TypedTokenStore tokenStore;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private OptionValidator validator;
	@Mock
	private GasCalculator gasCalculator;
	@Mock
	private MessageFrame frame;
	@Mock
	private SoliditySigsVerifier sigsVerifier;
	@Mock
	private AccountRecordsHistorian recordsHistorian;
	@Mock
	private DecodingFacade decoder;
	@Mock
	private HTSPrecompiledContract.MintLogicFactory mintLogicFactory;
	@Mock
	private HTSPrecompiledContract.TokenStoreFactory tokenStoreFactory;
	@Mock
	private HTSPrecompiledContract.AccountStoreFactory accountStoreFactory;
	@Mock
	private HTSPrecompiledContract.DissociateLogicFactory dissociateLogicFactory;
	@Mock
	private DissociateLogic dissociateLogic;
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
	private TransactionalLedger<EntityNum, AccountProperty, MerkleAccount> accounts;
	@Mock
	private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
	@Mock
	private EntityCreator creator;
	@Mock
	private DissociationFactory dissociationFactory;

	private HTSPrecompiledContract subject;

	@BeforeEach
	void setUp() {
		subject = new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder,
				syntheticTxnFactory, creator, dissociationFactory);
		subject.setMintLogicFactory(mintLogicFactory);
		subject.setDissociateLogicFactory(dissociateLogicFactory);
		subject.setTokenStoreFactory(tokenStoreFactory);
		subject.setAccountStoreFactory(accountStoreFactory);
		subject.setSideEffectsFactory(() -> sideEffects);
	}

	@Test
	void dissociateTokenFailurePathWorks() {
		givenFrameContext();

		given(sigsVerifier.hasActiveKey(accountId, recipientAddr, contractAddr))
				.willThrow(new InvalidTransactionException(INVALID_SIGNATURE));
		given(creator.createUnsuccessfulSyntheticRecord(INVALID_SIGNATURE)).willReturn(mockRecordBuilder);
		given(decoder.decodeDissociate(pretendArguments)).willReturn(dissociateToken);
		given(syntheticTxnFactory.createDissociate(dissociateToken)).willReturn(mockSynthBodyBuilder);

		// when:
		final var result = subject.computeDissociateToken(pretendArguments, frame);

		// then:
		assertEquals(invalidSigResult, result);

		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void dissociateTokenHappyPathWorks() {
		givenFrameContext();
		givenLedgers();

		given(sigsVerifier.hasActiveKey(accountId, recipientAddr, contractAddr)).willReturn(true);
		given(accountStoreFactory.newAccountStore(
				validator, dynamicProperties, accounts
		)).willReturn(accountStore);
		given(tokenStoreFactory.newTokenStore(
				accountStore, tokens, nfts, tokenRels, NOOP_VIEWS_MANAGER, NOOP_TREASURY_ADDER, NOOP_TREASURY_REMOVER, sideEffects
		)).willReturn(tokenStore);
		given(dissociateLogicFactory.newDissociateLogic(validator, tokenStore, accountStore, dissociationFactory)).willReturn(dissociateLogic);
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects)).willReturn(mockRecordBuilder);
		given(decoder.decodeDissociate(pretendArguments)).willReturn(dissociateToken);
		given(syntheticTxnFactory.createDissociate(dissociateToken)).willReturn(mockSynthBodyBuilder);

		// when:
		final var result = subject.computeDissociateToken(pretendArguments, frame);

		// then:
		assertEquals(successResult, result);
		// and:
		verify(dissociateLogic).dissociate(accountId, singletonList(nonFungible));
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void computeMultiDissociateTokenHappyPathWorks() {
		givenFrameContext();
		givenLedgers();
		given(decoder.decodeMultipleDissociations(pretendArguments))
				.willReturn(multiDissociateOp);
		given(syntheticTxnFactory.createDissociate(multiDissociateOp))
				.willReturn(mockSynthBodyBuilder);
		given(sigsVerifier.hasActiveKey(accountId, recipientAddr, contractAddr))
				.willReturn(true);
		given(accountStoreFactory.newAccountStore(validator, dynamicProperties, accounts))
				.willReturn(accountStore);
		given(tokenStoreFactory.newTokenStore(accountStore, tokens, nfts, tokenRels, NOOP_VIEWS_MANAGER,
				NOOP_TREASURY_ADDER, NOOP_TREASURY_REMOVER, sideEffects))
				.willReturn(tokenStore);
		given(dissociateLogicFactory.newDissociateLogic(validator, tokenStore, accountStore, dissociationFactory))
				.willReturn(dissociateLogic);
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects))
				.willReturn(mockRecordBuilder);

		// when:
		final var result = subject.computeDissociateTokens(pretendArguments, frame);

		// then:
		assertEquals(successResult, result);
		verify(dissociateLogic).dissociate(accountId, multiDissociateOp.getTokenIds());
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	private void givenFrameContext() {
		given(frame.getContractAddress()).willReturn(contractAddr);
		given(frame.getRecipientAddress()).willReturn(recipientAddr);
		given(frame.getWorldUpdater()).willReturn(worldUpdater);
		given(worldUpdater.wrappedTrackingLedgers()).willReturn(wrappedLedgers);
	}

	private void givenLedgers() {
		given(wrappedLedgers.accounts()).willReturn(accounts);
		given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
		given(wrappedLedgers.nfts()).willReturn(nfts);
		given(wrappedLedgers.tokens()).willReturn(tokens);
	}

	private static final TokenID nonFungible = IdUtils.asToken("0.0.777");
	private static final AccountID account = IdUtils.asAccount("0.0.3");
	private static final Id accountId = Id.fromGrpcAccount(account);
	private static final SyntheticTxnFactory.Dissociation dissociateToken =
			SyntheticTxnFactory.Dissociation.singleDissociation(account, nonFungible);
	private static final SyntheticTxnFactory.Dissociation multiDissociateOp =
			SyntheticTxnFactory.Dissociation.singleDissociation(account, nonFungible);
	private static final Address recipientAddr = Address.ALTBN128_ADD;
	private static final Address contractAddr = Address.ALTBN128_MUL;
	private static final Bytes successResult = UInt256.valueOf(ResponseCodeEnum.SUCCESS_VALUE);
	private static final Bytes invalidSigResult = UInt256.valueOf(ResponseCodeEnum.INVALID_SIGNATURE_VALUE);
}
