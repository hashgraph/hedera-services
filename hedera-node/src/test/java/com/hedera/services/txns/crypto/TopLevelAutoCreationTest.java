package com.hedera.services.txns.crypto;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeObject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.services.txns.crypto.TopLevelAutoCreation.AUTO_MEMO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TopLevelAutoCreationTest {
	@Mock
	private StateView currentView;
	@Mock
	private EntityIdSource ids;
	@Mock
	private EntityCreator creator;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private AliasManager aliasManager;
	@Mock
	private SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	private FeeCalculator feeCalculator;
	@Mock
	private AccountRecordsHistorian recordsHistorian;
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

	private TopLevelAutoCreation subject;

	@BeforeEach
	void setUp() {
		subject = new TopLevelAutoCreation(
				syntheticTxnFactory, creator, ids, aliasManager, currentView, txnCtx, accountsLedger);
		subject.setFeeCalculator(feeCalculator);
	}

	@Test
	void happyPathWorks() {
		givenCollaborators();

		final var input = wellKnownChange();

		final var result = subject.createFromTrigger(input);
		subject.submitRecordsTo(recordsHistorian);

		assertEquals(initialTransfer - totalFee, input.units());
		assertEquals(initialTransfer - totalFee, input.getNewBalance());
		verify(aliasManager).link(alias, createdNum);
		assertEquals(Pair.of(OK, totalFee), result);
		verify(recordsHistorian).trackPrecedingChildRecord(
				eq(DEFAULT_SOURCE_ID),
				eq(mockSyntheticCreation),
				any(ExpirableTxnRecord.Builder.class));
	}

	@Test
	void translatesPresumablyImpossibleDecodingErrorUnchecked() {
		final var invalidAlias = ByteString.copyFromUtf8("not-a-primitive-key");
		final var invalidInput = BalanceChange.changingHbar(AccountAmount.newBuilder()
				.setAmount(initialTransfer)
				.setAccountID(AccountID.newBuilder().setAlias(invalidAlias).build())
				.build());

		assertThrows(IllegalStateException.class, () -> subject.createFromTrigger(invalidInput));
	}

	@Test
	void shortCircuitsWhenFeeExceedsChangeBalance() {
		given(txnCtx.consensusTime())
				.willReturn(consensusNow);
		given(syntheticTxnFactory.createAccount(aPrimitiveKey, 0L))
				.willReturn(mockSyntheticCreation);
		given(feeCalculator.computeFee(any(), eq(EMPTY_KEY), eq(currentView), eq(consensusNow)))
				.willReturn(fees);

		final var input = wellKnownChange();
		input.adjustUnits(-initialTransfer - totalFee - 1);

		final var result = subject.createFromTrigger(input);
		assertEquals(input.codeForInsufficientBalance(), result.getKey());
	}

	@Test
	void resetAndReclaimWork() {
		givenCollaborators();
		final var input = wellKnownChange();

		subject.createFromTrigger(input);
		assertFalse(subject.getPendingCreations().isEmpty());

		assertTrue(subject.reclaimPendingAliases());
		subject.reset();
		assertTrue(subject.getPendingCreations().isEmpty());
		verify(aliasManager).unlink(alias);
		assertFalse(subject.reclaimPendingAliases());
	}

	private void givenCollaborators() {
		given(txnCtx.consensusTime())
				.willReturn(consensusNow);
		given(ids.newAccountId(any()))
				.willReturn(created);
		given(syntheticTxnFactory.createAccount(aPrimitiveKey, 0L))
				.willReturn(mockSyntheticCreation);
		given(feeCalculator.computeFee(any(), eq(EMPTY_KEY), eq(currentView), eq(consensusNow)))
				.willReturn(fees);
		given(creator.createSuccessfulSyntheticRecord(eq(Collections.emptyList()), any(), eq(AUTO_MEMO)))
				.willReturn(ExpirableTxnRecord.newBuilder().setAlias(alias));
	}

	private BalanceChange wellKnownChange() {
		return BalanceChange.changingHbar(AccountAmount.newBuilder()
				.setAmount(initialTransfer)
				.setAccountID(AccountID.newBuilder().setAlias(alias).build())
				.build());
	}

	private static final TransactionBody.Builder mockSyntheticCreation = TransactionBody.newBuilder();
	private static final long initialTransfer = 16L;
	private static final Key aPrimitiveKey = Key.newBuilder()
			.setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
			.build();
	private static final ByteString alias = aPrimitiveKey.toByteString();
	private static final AccountID created = IdUtils.asAccount("0.0.1234");
	private static final EntityNum createdNum = EntityNum.fromAccountId(created);
	private static final FeeObject fees = new FeeObject(1L, 2L, 3L);
	private static final long totalFee = 6L;
	private static final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);
}
