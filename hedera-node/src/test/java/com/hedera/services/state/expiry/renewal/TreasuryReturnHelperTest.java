package com.hedera.services.state.expiry.renewal;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.hedera.services.state.expiry.renewal.RenewalRecordsHelperTest.ttlOf;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TreasuryReturnHelperTest {
	@Mock
	private MerkleMap<EntityNum, MerkleToken> tokens;
	@Mock
	private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels;

	private List<EntityId> tokenTypes = new ArrayList<>();
	private List<CurrencyAdjustments> returnTransfers = new ArrayList<>();

	private TreasuryReturnHelper subject;

	@BeforeEach
	void setUp() {
		subject = new TreasuryReturnHelper(() -> tokens, () -> tokenRels);
	}

	@Test
	void onlyRemovesIfTokenDoesntExist() {
		givenRelPresent(num, missingTokenId, 0);

		subject.updateReturns(num.toGrpcAccountId(), missingTokenGrpcId, tokenTypes, returnTransfers);

		verify(tokenRels).remove(EntityNumPair.fromNums(num, missingTokenId));
		assertTrue(tokenTypes.isEmpty());
		assertTrue(returnTransfers.isEmpty());
	}

	@Test
	void onlyRemovesIfTokenDeleted() {
		givenTokenPresent(deletedTokenId, deletedToken);
		givenRelPresent(num, deletedTokenId, 123);

		subject.updateReturns(num.toGrpcAccountId(), deletedTokenGrpcId, tokenTypes, returnTransfers);

		verify(tokenRels).remove(EntityNumPair.fromNums(num, deletedTokenId));
		assertTrue(tokenTypes.isEmpty());
		assertTrue(returnTransfers.isEmpty());
	}

	@Test
	void shortCircuitsToJustRemovingRelIfZeroBalance() {
		givenTokenPresent(survivedTokenId, longLivedToken);
		givenRelPresent(num, survivedTokenId, 0);

		subject.updateReturns(num.toGrpcAccountId(), survivedTokenGrpcId, tokenTypes, returnTransfers);

		verify(tokenRels).remove(EntityNumPair.fromNums(num, survivedTokenId));
		assertTrue(tokenTypes.isEmpty());
		assertTrue(returnTransfers.isEmpty());
	}

	@Test
	void doesTreasuryReturnForNonzeroFungibleBalance() {
		givenTokenPresent(survivedTokenId, longLivedToken);
		givenRelPresent(num, survivedTokenId, tokenBalance);
		givenModifiableRelPresent(treasuryNum, survivedTokenId, 0L);

		subject.updateReturns(num.toGrpcAccountId(), survivedTokenGrpcId, tokenTypes, returnTransfers);

		verify(tokenRels).remove(EntityNumPair.fromAccountTokenRel(num.toGrpcAccountId(), survivedTokenGrpcId));
		final var ttls = List.of(
				ttlOf(survivedTokenGrpcId,
						num.toGrpcAccountId(), treasuryId.toGrpcAccountId(), tokenBalance));
		assertEquals(tokensFrom(ttls), tokenTypes);
		assertEquals(adjustmentsFrom(ttls), returnTransfers);
	}

	private void givenTokenPresent(EntityNum id, MerkleToken token) {
		given(tokens.containsKey(id)).willReturn(true);
		given(tokens.get(id)).willReturn(token);
	}

	private void givenRelPresent(EntityNum account, EntityNum token, long balance) {
		final var rel = EntityNumPair.fromNums(account, token);
		given(tokenRels.get(rel)).willReturn(new MerkleTokenRelStatus(balance, false, false, false));
	}

	private void givenModifiableRelPresent(EntityNum account, EntityNum token, long balance) {
		var rel = EntityNumPair.fromLongs(account.longValue(), token.longValue());
		given(tokenRels.getForModify(rel)).willReturn(new MerkleTokenRelStatus(balance, false, false, true));
	}

	static List<EntityId> tokensFrom(final List<TokenTransferList> ttls) {
		return ttls.stream().map(TokenTransferList::getToken).map(EntityId::fromGrpcTokenId).collect(toList());
	}

	static List<CurrencyAdjustments> adjustmentsFrom(final List<TokenTransferList> ttls) {
		return ttls.stream().map(ttl -> new CurrencyAdjustments(
				ttl.getTransfersList().stream()
						.mapToLong(AccountAmount::getAmount)
						.toArray(),
				ttl.getTransfersList().stream()
						.map(AccountAmount::getAccountID)
						.mapToLong(AccountID::getAccountNum)
						.toArray())
		).collect(Collectors.toList());
	}

	private final long expiredNum = 2L;
	private final long deletedTokenNum = 1234L;
	private final long survivedTokenNum = 4321L;
	private final long missingTokenNum = 5678L;
	private final long tokenBalance = 1_234L;
	private final EntityId expiredTreasuryId = new EntityId(0, 0, expiredNum);
	private final EntityNum treasuryNum = EntityNum.fromLong(666L);
	private final EntityNum num = EntityNum.fromLong(expiredNum);
	private final EntityNum deletedTokenId = EntityNum.fromLong(deletedTokenNum);
	private final EntityNum survivedTokenId = EntityNum.fromLong(survivedTokenNum);
	private final EntityNum missingTokenId = EntityNum.fromLong(missingTokenNum);
	private final EntityId treasuryId = treasuryNum.toId().asEntityId();
	private final TokenID deletedTokenGrpcId = deletedTokenId.toGrpcTokenId();
	private final TokenID survivedTokenGrpcId = survivedTokenId.toGrpcTokenId();
	private final TokenID missingTokenGrpcId = missingTokenId.toGrpcTokenId();
	private final MerkleToken deletedToken = new MerkleToken(
			Long.MAX_VALUE, 1L, 0,
			"GONE", "Long lost dream",
			true, true, expiredTreasuryId);
	private final MerkleToken longLivedToken = new MerkleToken(
			Long.MAX_VALUE, 1L, 0,
			"HERE", "Dreams never die",
			true, true, treasuryId);
	{
		deletedToken.setDeleted(true);
	}
}
