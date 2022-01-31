/*
 * -
 *  * ‌
 *  * Hedera Services Node
 *  * ​
 *  * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  * ​
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ‍
 *
 */

package com.hedera.services.txns.crypto.validators;

import com.google.protobuf.BoolValue;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.ledger.backing.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.properties.NftProperty.OWNER;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_FROZEN;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class AllowanceChecksTest {
	@Mock
	private TransactionalLedger tokenRelsLedger;
	@Mock
	private TransactionalLedger nftsLedger;
	@Mock
	private TypedTokenStore tokenStore;
	@Mock
	private Account owner;

	AllowanceChecks subject;

	private final AccountID spender1 = asAccount("0.0.123");
	private final TokenID token1 = asToken("0.0.100");
	private final TokenID token2 = asToken("0.0.200");
	private final AccountID ownerId = asAccount("0.0.5000");

	private final Token token1Model = new Token(Id.fromGrpcToken(token1));
	private final Token token2Model = new Token(Id.fromGrpcToken(token2));

	private final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).build();
	private final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).setTokenId(token1).build();
	private final NftAllowance nftAllowance1 = NftAllowance.newBuilder().setSpender(spender1)
			.setTokenId(token1).setApprovedForAll(BoolValue.of(false)).addAllSerialNumbers(List.of(1L, 10L)).build();

	private final Pair<AccountID, TokenID> ownerReln1 = asTokenRel(ownerId, token1);
	private final Pair<AccountID, TokenID> spenderReln1 = asTokenRel(spender1, token1);

	private List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
	private List<TokenAllowance> tokenAllowances = new ArrayList<>();
	private List<NftAllowance> nftAllowances = new ArrayList<>();

	private TransactionBody cryptoApproveAllowanceTxn;

	@BeforeEach
	void setUp() {
		setUpForTest();
		subject = new AllowanceChecks(tokenRelsLedger, nftsLedger, tokenStore);
	}

	private void setUpForTest() {
		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);

		token1Model.setMaxSupply(5000L);
		token2Model.setMaxSupply(5000L);
		given(tokenStore.loadToken(token1Model.getId())).willReturn(token1Model);
		given(tokenStore.loadToken(token2Model.getId())).willReturn(token2Model);
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId));
		given(owner.isAssociatedWith(token1Model.getId())).willReturn(true);
		given(owner.isAssociatedWith(token2Model.getId())).willReturn(true);
		given(nftsLedger.get(NftId.withDefaultShardRealm(token1Model.getId().num(), 1L), OWNER))
				.willReturn(owner.getId());
		given(nftsLedger.get(NftId.withDefaultShardRealm(token1Model.getId().num(), 10L), OWNER))
				.willReturn(owner.getId());

		given(tokenRelsLedger.get(asTokenRel(ownerId, token1), IS_FROZEN)).willReturn(false);
		given(tokenRelsLedger.get(asTokenRel(spender1, token1), IS_FROZEN)).willReturn(false);
	}

	@Test
	void validatesDuplicateSpenders() {
		assertFalse(subject.hasRepeatedSpender(cryptoAllowances.stream().map(a -> a.getSpender()).toList()));
		assertFalse(subject.hasRepeatedSpender(tokenAllowances.stream().map(a -> a.getSpender()).toList()));
		assertFalse(subject.hasRepeatedSpender(nftAllowances.stream().map(a -> a.getSpender()).toList()));

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);

		assertTrue(subject.hasRepeatedSpender(cryptoAllowances.stream().map(a -> a.getSpender()).toList()));
		assertTrue(subject.hasRepeatedSpender(tokenAllowances.stream().map(a -> a.getSpender()).toList()));
		assertTrue(subject.hasRepeatedSpender(nftAllowances.stream().map(a -> a.getSpender()).toList()));
	}

	@Test
	void failsWithFrozenOwnerAccounts() {
		getValidTxnCtx();
		given(tokenRelsLedger.get(asTokenRel(owner.getId().asGrpcAccount(), token1), IS_FROZEN))
				.willReturn(true);

		assertTrue(subject.frozenAccounts(ownerId, spender1, token1));
	}

	@Test
	void failsWithFrozenSpenderAccounts() {
		getValidTxnCtx();
		given(tokenRelsLedger.get(asTokenRel(owner.getId().asGrpcAccount(), token1), IS_FROZEN))
				.willReturn(false);
		given(tokenRelsLedger.get(asTokenRel(spender1, token1), IS_FROZEN)).willReturn(true);

		assertTrue(subject.frozenAccounts(ownerId, spender1, token1));
	}

	@Test
	void succeedsWithEmptyLists() {
		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.build()
				)
				.build();
		assertEquals(OK, subject.validateCryptoAllowances(
				cryptoApproveAllowanceTxn.getCryptoAdjustAllowance().getCryptoAllowancesList(), owner));
		assertEquals(OK, subject.validateFungibleTokenAllowances(
				cryptoApproveAllowanceTxn.getCryptoAdjustAllowance().getTokenAllowancesList(), owner));
		assertEquals(OK, subject.validateNftAllowances(
				cryptoApproveAllowanceTxn.getCryptoAdjustAllowance().getNftAllowancesList(), owner));
	}

	@Test
	void failsIfOwnerSameAsSpender() {
		final var badCryptoAllowance = CryptoAllowance.newBuilder().setSpender(ownerId).setAmount(
				10L).build();
		final var badTokenAllowance = TokenAllowance.newBuilder().setSpender(ownerId).setAmount(
				20L).setTokenId(token2).build();
		final var badNftAllowance = NftAllowance.newBuilder().setSpender(ownerId)
				.setTokenId(token1).setApprovedForAll(BoolValue.of(false)).addAllSerialNumbers(List.of(1L)).build();

		cryptoAllowances.add(badCryptoAllowance);
		assertEquals(SPENDER_ACCOUNT_SAME_AS_OWNER, subject.validateCryptoAllowances(cryptoAllowances, owner));

		tokenAllowances.add(badTokenAllowance);
		assertEquals(SPENDER_ACCOUNT_SAME_AS_OWNER, subject.validateFungibleTokenAllowances(tokenAllowances, owner));

		nftAllowances.add(badNftAllowance);
		assertEquals(SPENDER_ACCOUNT_SAME_AS_OWNER, subject.validateNftAllowances(nftAllowances, owner));
	}

	private void getValidTxnCtx() {
		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
								.build()
				)
				.build();
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(ownerId)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
				.build();
	}
}
