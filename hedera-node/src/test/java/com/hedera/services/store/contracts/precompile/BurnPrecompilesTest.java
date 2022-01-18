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
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
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
import com.hedera.services.store.models.NftId;
import com.hedera.services.txns.token.BurnLogic;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
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
import java.util.List;
import java.util.Optional;

import static com.hedera.services.state.expiry.ExpiringCreations.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_BURN_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.NOOP_TREASURY_ADDER;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.NOOP_TREASURY_REMOVER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.AMOUNT;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.burnSuccessResultWith49Supply;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.expirableTxnRecordBuilder;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleBurn;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.invalidSigResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nonFungibleBurn;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nonFungibleId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nonFungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.recipientAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.targetSerialNos;
import static com.hedera.services.store.tokens.views.UniqueTokenViewsManager.NOOP_VIEWS_MANAGER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
class BurnPrecompilesTest {
	@Mock
	private Bytes pretendArguments;
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
	private MessageFrame parentFrame;
	@Mock
	private Deque<MessageFrame> frameDeque;
	@Mock
	private Iterator<MessageFrame> dequeIterator;
	@Mock
	private TxnAwareSoliditySigsVerifier sigsVerifier;
	@Mock
	private AccountRecordsHistorian recordsHistorian;
	@Mock
	private DecodingFacade decoder;
	@Mock
	private EncodingFacade encoder;
	@Mock
	private HTSPrecompiledContract.BurnLogicFactory burnLogicFactory;
	@Mock
	private HTSPrecompiledContract.TokenStoreFactory tokenStoreFactory;
	@Mock
	private HTSPrecompiledContract.AccountStoreFactory accountStoreFactory;
	@Mock
	private BurnLogic burnLogic;
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

	private HTSPrecompiledContract subject;

	@BeforeEach
	void setUp() {
		subject = new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder, encoder,
				syntheticTxnFactory, creator, dissociationFactory, impliedTransfersMarshal);
		subject.setBurnLogicFactory(burnLogicFactory);
		subject.setTokenStoreFactory(tokenStoreFactory);
		subject.setAccountStoreFactory(accountStoreFactory);
		subject.setSideEffectsFactory(() -> sideEffects);
	}

	@Test
	void nftBurnFailurePathWorks() {
		givenNonfungibleFrameContext();

		given(sigsVerifier.hasActiveSupplyKey(nonFungibleTokenAddr, recipientAddr, contractAddr, recipientAddr))
				.willThrow(new InvalidTransactionException(INVALID_SIGNATURE));
		given(creator.createUnsuccessfulSyntheticRecord(INVALID_SIGNATURE)).willReturn(mockRecordBuilder);
		given(encoder.encodeBurnFailure(INVALID_SIGNATURE)).willReturn(invalidSigResult);

		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		assertEquals(invalidSigResult, result);
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void nftBurnHappyPathWorks() {
		givenNonfungibleFrameContext();
		givenLedgers();

		given(sigsVerifier.hasActiveSupplyKey(nonFungibleTokenAddr, recipientAddr, contractAddr, recipientAddr)).willReturn(true);
		given(accountStoreFactory.newAccountStore(
				validator, dynamicProperties, accounts
		)).willReturn(accountStore);
		given(tokenStoreFactory.newTokenStore(
				accountStore, tokens, nfts, tokenRels, NOOP_VIEWS_MANAGER, NOOP_TREASURY_ADDER, NOOP_TREASURY_REMOVER, sideEffects
		)).willReturn(tokenStore);
		given(burnLogicFactory.newBurnLogic(tokenStore, accountStore)).willReturn(burnLogic);
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);
		final var receiptBuilder = TxnReceipt.newBuilder()
				.setNewTotalSupply(123L);
		given(mockRecordBuilder.getReceiptBuilder()).willReturn(receiptBuilder);
		given(encoder.encodeBurnSuccess(123L)).willReturn(successResult);

		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		assertEquals(successResult, result);
		verify(burnLogic).burn(nonFungibleId, 0, targetSerialNos);
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void fungibleBurnHappyPathWorks() {
		givenFungibleFrameContext();
		givenLedgers();

		given(sigsVerifier.hasActiveSupplyKey(fungibleTokenAddr, recipientAddr, contractAddr, recipientAddr)).willReturn(true);
		given(accountStoreFactory.newAccountStore(
				validator, dynamicProperties, accounts
		)).willReturn(accountStore);
		given(tokenStoreFactory.newTokenStore(
				accountStore, tokens, nfts, tokenRels, NOOP_VIEWS_MANAGER, NOOP_TREASURY_ADDER, NOOP_TREASURY_REMOVER, sideEffects
		)).willReturn(tokenStore);
		given(burnLogicFactory.newBurnLogic(tokenStore, accountStore)).willReturn(burnLogic);
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(expirableTxnRecordBuilder);
		given(encoder.encodeBurnSuccess(49)).willReturn(burnSuccessResultWith49Supply);

		subject.gasRequirement(pretendArguments);
		final var result = subject.computeInternal(frame);

		assertEquals(burnSuccessResultWith49Supply, result);
		verify(burnLogic).burn(fungibleId, AMOUNT, List.of());
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, expirableTxnRecordBuilder, mockSynthBodyBuilder);
	}

	private void givenNonfungibleFrameContext() {
		givenFrameContext();
		given(decoder.decodeBurn(pretendArguments)).willReturn(nonFungibleBurn);
		given(syntheticTxnFactory.createBurn(nonFungibleBurn)).willReturn(mockSynthBodyBuilder);
	}

	private void givenFungibleFrameContext() {
		givenFrameContext();
		given(decoder.decodeBurn(pretendArguments)).willReturn(fungibleBurn);
		given(syntheticTxnFactory.createBurn(fungibleBurn)).willReturn(mockSynthBodyBuilder);
	}

	private void givenFrameContext() {
		given(frame.getContractAddress()).willReturn(contractAddr);
		given(frame.getRecipientAddress()).willReturn(recipientAddr);
		given(frame.getWorldUpdater()).willReturn(worldUpdater);
		Optional<WorldUpdater> parent = Optional.of(worldUpdater);
		given(worldUpdater.parentUpdater()).willReturn(parent);
		given(worldUpdater.wrappedTrackingLedgers()).willReturn(wrappedLedgers);
		given(pretendArguments.getInt(0)).willReturn(ABI_ID_BURN_TOKEN);
	}

	private void givenLedgers() {
		given(wrappedLedgers.accounts()).willReturn(accounts);
		given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
		given(wrappedLedgers.nfts()).willReturn(nfts);
		given(wrappedLedgers.tokens()).willReturn(tokens);
	}
}