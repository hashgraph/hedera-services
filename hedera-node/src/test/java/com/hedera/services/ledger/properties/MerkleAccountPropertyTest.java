package com.hedera.services.ledger.properties;

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
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.ledger.properties.AccountProperty.ALIAS;
import static com.hedera.services.ledger.properties.AccountProperty.ALREADY_USED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_CONTRACT_KV_PAIRS;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hedera.services.ledger.properties.AccountProperty.PROXY;
import static com.hedera.services.ledger.properties.AccountProperty.TOKENS;
import static com.hedera.services.state.submerkle.ExpirableTxnRecordTestHelper.fromGprc;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MerkleAccountPropertyTest {
	@Mock
	private MerkleAccount mockAccount;
	@Mock
	private MerkleAccountTokens mockAccountTokens;

	@Test
	void tokenGetterWorksWithNewFcmParadigm() {
		final var ids = new CopyOnWriteIds(new long[] { 1, 2, 3 });
		final var copyResult = new MerkleAccountTokens(ids);
		given(mockAccountTokens.tmpNonMerkleCopy()).willReturn(copyResult);
		given(mockAccount.tokens()).willReturn(mockAccountTokens);

		final var result = TOKENS.getter().apply(mockAccount);

		assertSame(copyResult, result);
	}

	@Test
	void tokenSetterWorksWithNewFcmParadigm() {
		final var ids = new CopyOnWriteIds(new long[] { 1, 2, 3, 4, 5, 6 });
		final var newTokens = new MerkleAccountTokens(ids);
		given(mockAccount.tokens()).willReturn(mockAccountTokens);

		TOKENS.setter().accept(mockAccount, newTokens);

		verify(mockAccountTokens).shareTokensOf(newTokens);
	}

	@Test
	void cannotSetNegativeBalance() {
		assertThrows(
				IllegalArgumentException.class,
				() -> BALANCE.setter().accept(new MerkleAccount(), -1L));
	}

	@Test
	void cannotConvertNonNumericObjectToBalance() {
		assertThrows(
				IllegalArgumentException.class,
				() -> BALANCE.setter().accept(new MerkleAccount(), "NotNumeric"));
	}

	@Test
	void gettersAndSettersWork() throws Exception {
		final boolean origIsDeleted = false;
		final boolean origIsReceiverSigReq = false;
		final boolean origIsContract = false;
		final long origBalance = 1L;
		final long origAutoRenew = 1L;
		final long origNumNfts = 123L;
		final long origExpiry = 1L;
		final int origMaxAutoAssociations = 10;
		final int origAlreadyUsedAutoAssociations = 7;
		final var origKey = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
		final String origMemo = "a";
		final var origProxy = AccountID.getDefaultInstance();
		final List<ExpirableTxnRecord> origRecords = new ArrayList<>();
		origRecords.add(expirableRecord(ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT));
		origRecords.add(expirableRecord(ResponseCodeEnum.INVALID_PAYER_SIGNATURE));
		final List<ExpirableTxnRecord> origPayerRecords = new ArrayList<>();
		origPayerRecords.add(expirableRecord(ResponseCodeEnum.INVALID_CHUNK_NUMBER));
		origPayerRecords.add(expirableRecord(ResponseCodeEnum.INSUFFICIENT_TX_FEE));
		final boolean newIsDeleted = true;
		final boolean newIsReceiverSigReq = true;
		final boolean newIsContract = true;
		final long newBalance = 2L;
		final long newAutoRenew = 2L;
		final long newExpiry = 2L;
		final long newNumNfts = 321L;
		final int newMaxAutoAssociations = 15;
		final int newAlreadyUsedAutoAssociations = 11;
		final JKey newKey = new JKeyList();
		final String newMemo = "b";
		final EntityId newProxy = new EntityId(0, 0, 2);
		final var oldAlias = ByteString.copyFromUtf8("then");
		final var newAlias = ByteString.copyFromUtf8("now");
		final int oldNumKvPairs = 123;
		final int newNumKvPairs = 123;

		final var account = new HederaAccountCustomizer()
				.key(JKey.mapKey(origKey))
				.expiry(origExpiry)
				.proxy(EntityId.fromGrpcAccountId(origProxy))
				.autoRenewPeriod(origAutoRenew)
				.isDeleted(origIsDeleted)
				.alias(oldAlias)
				.memo(origMemo)
				.isSmartContract(origIsContract)
				.isReceiverSigRequired(origIsReceiverSigReq)
				.customizing(new MerkleAccount());
		account.setNumContractKvPairs(oldNumKvPairs);
		account.setNftsOwned(origNumNfts);
		account.setBalance(origBalance);
		account.records().offer(origPayerRecords.get(0));
		account.records().offer(origPayerRecords.get(1));
		account.setMaxAutomaticAssociations(origMaxAutoAssociations);
		account.setAlreadyUsedAutomaticAssociations(origAlreadyUsedAutoAssociations);

		final var adminKey = TOKEN_ADMIN_KT.asJKeyUnchecked();
		final var unfrozenToken = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"UnfrozenToken", "UnfrozenTokenName", false, true,
				new EntityId(1, 2, 3));
		unfrozenToken.setFreezeKey(adminKey);
		unfrozenToken.setKycKey(adminKey);
		final var frozenToken = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"FrozenToken", "FrozenTokenName", true, false,
				new EntityId(1, 2, 3));
		frozenToken.setFreezeKey(adminKey);
		frozenToken.setKycKey(adminKey);

		ALIAS.setter().accept(account, newAlias);
		IS_DELETED.setter().accept(account, newIsDeleted);
		IS_RECEIVER_SIG_REQUIRED.setter().accept(account, newIsReceiverSigReq);
		IS_SMART_CONTRACT.setter().accept(account, newIsContract);
		BALANCE.setter().accept(account, newBalance);
		AUTO_RENEW_PERIOD.setter().accept(account, newAutoRenew);
		EXPIRY.setter().accept(account, newExpiry);
		KEY.setter().accept(account, newKey);
		MEMO.setter().accept(account, newMemo);
		PROXY.setter().accept(account, newProxy);
		NUM_NFTS_OWNED.setter().accept(account, newNumNfts);
		MAX_AUTOMATIC_ASSOCIATIONS.setter().accept(account, newMaxAutoAssociations);
		ALREADY_USED_AUTOMATIC_ASSOCIATIONS.setter().accept(account, newAlreadyUsedAutoAssociations);
		NUM_CONTRACT_KV_PAIRS.setter().accept(account, newNumKvPairs);

		assertEquals(newIsDeleted, IS_DELETED.getter().apply(account));
		assertEquals(newIsReceiverSigReq, IS_RECEIVER_SIG_REQUIRED.getter().apply(account));
		assertEquals(newIsContract, IS_SMART_CONTRACT.getter().apply(account));
		assertEquals(newBalance, BALANCE.getter().apply(account));
		assertEquals(newAutoRenew, AUTO_RENEW_PERIOD.getter().apply(account));
		assertEquals(newExpiry, EXPIRY.getter().apply(account));
		assertEquals(newKey, KEY.getter().apply(account));
		assertEquals(newMemo, MEMO.getter().apply(account));
		assertEquals(newProxy, PROXY.getter().apply(account));
		assertEquals(newNumNfts, NUM_NFTS_OWNED.getter().apply(account));
		assertEquals(newAlreadyUsedAutoAssociations, ALREADY_USED_AUTOMATIC_ASSOCIATIONS.getter().apply(account));
		assertEquals(newMaxAutoAssociations, MAX_AUTOMATIC_ASSOCIATIONS.getter().apply(account));
		assertEquals(newAlias, ALIAS.getter().apply(account));
		assertEquals(newNumKvPairs, NUM_CONTRACT_KV_PAIRS.getter().apply(account));
	}

	private ExpirableTxnRecord expirableRecord(final ResponseCodeEnum status) {
		return fromGprc(
				TransactionRecord.newBuilder()
						.setReceipt(TransactionReceipt.newBuilder().setStatus(status))
						.build()
		);
	}
}
