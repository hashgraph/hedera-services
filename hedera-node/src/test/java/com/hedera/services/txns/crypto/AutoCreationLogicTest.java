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
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeObject;
import com.swirlds.merkle.map.MerkleMap;
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
import static com.hedera.services.txns.crypto.AutoCreationLogic.AUTO_MEMO;
import static com.hedera.services.txns.crypto.AutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AutoCreationLogicTest {
	@Mock
	private UsageLimits usageLimits;
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
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	@Mock
	private RecordsHistorian recordsHistorian;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;

	private AutoCreationLogic subject;

	@BeforeEach
	void setUp() {
		subject = new AutoCreationLogic(
				usageLimits, syntheticTxnFactory, creator, ids, aliasManager,
				sigImpactHistorian, currentView, txnCtx, () -> accounts);

		subject.setFeeCalculator(feeCalculator);
	}

	@Test
	void refusesToCreateBeyondMaxNumber() {
		final var input = wellKnownChange();

		final var result = subject.create(input, accountsLedger);
		assertEquals(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED, result.getLeft());
	}

	@Test
	void happyPathWorks() {
		givenCollaborators();

		final var input = wellKnownChange();
		final var expectedExpiry = consensusNow.getEpochSecond() + THREE_MONTHS_IN_SECONDS;

		final var result = subject.create(input, accountsLedger);
		subject.submitRecordsTo(recordsHistorian);

		assertEquals(initialTransfer - totalFee, input.getAggregatedUnits());
		assertEquals(initialTransfer - totalFee, input.getNewBalance());
		verify(aliasManager).link(alias, createdNum);
		verify(sigImpactHistorian).markAliasChanged(alias);
		verify(sigImpactHistorian).markEntityChanged(createdNum.longValue());
		verify(accountsLedger).set(createdNum.toGrpcAccountId(), AccountProperty.EXPIRY, expectedExpiry);
		verify(recordsHistorian).trackPrecedingChildRecord(DEFAULT_SOURCE_ID, mockSyntheticCreation, mockBuilder);
		assertEquals(totalFee, mockBuilder.getFee());
		assertEquals(Pair.of(OK, totalFee), result);
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
				.willReturn(mockBuilder);
		given(usageLimits.areCreatableAccounts(1)).willReturn(true);
	}

	private BalanceChange wellKnownChange() {
		return BalanceChange.changingHbar(AccountAmount.newBuilder()
						.setAmount(initialTransfer)
						.setAccountID(AccountID.newBuilder().setAlias(alias).build())
						.build(),
				payer);
	}

	private static final TransactionBody.Builder mockSyntheticCreation = TransactionBody.newBuilder();
	private static final long initialTransfer = 16L;
	private static final Key aPrimitiveKey = Key.newBuilder()
			.setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
			.build();
	private static final ByteString alias = aPrimitiveKey.toByteString();
	private static final AccountID created = IdUtils.asAccount("0.0.1234");
	public static final AccountID payer = IdUtils.asAccount("0.0.12345");
	private static final EntityNum createdNum = EntityNum.fromAccountId(created);
	private static final FeeObject fees = new FeeObject(1L, 2L, 3L);
	private static final long totalFee = 6L;
	private static final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);
	private static final ExpirableTxnRecord.Builder mockBuilder = ExpirableTxnRecord.newBuilder()
			.setAlias(alias)
			.setReceiptBuilder(TxnReceipt.newBuilder()
					.setAccountId(new EntityId(0, 0, createdNum.longValue())));
}
