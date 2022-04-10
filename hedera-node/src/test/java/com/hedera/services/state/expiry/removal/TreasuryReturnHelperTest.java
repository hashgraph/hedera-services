package com.hedera.services.state.expiry.removal;

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

import static com.hedera.test.utils.TxnUtils.asymmetricTtlOf;
import static com.hedera.test.utils.TxnUtils.ttlOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TreasuryReturnHelperTest {
	@Mock
	private MerkleMap<EntityNum, MerkleToken> tokens;
	@Mock
	private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels;

	private List<CurrencyAdjustments> returnTransfers = new ArrayList<>();

	private TreasuryReturnHelper subject;

	@BeforeEach
	void setUp() {
		subject = new TreasuryReturnHelper(() -> tokens, () -> tokenRels);
	}

	@Test
	void justReportsDebitIfTokenIsGoneSomehow() {
		subject.updateReturns(expiredAccountNum, missingTokenNum, tokenBalance, returnTransfers);

		final var ttls = List.of(
				asymmetricTtlOf(missingTokenNum.toGrpcTokenId(),
						expiredAccountNum.toGrpcAccountId(), tokenBalance));
		assertEquals(adjustmentsFrom(ttls), returnTransfers);
	}

	@Test
	void justReportsDebitIfTokenIsDeleted() {
		givenTokenPresent(deletedTokenNum, deletedToken);

		subject.updateReturns(expiredAccountNum, deletedTokenNum, tokenBalance, returnTransfers);

		final var ttls = List.of(
				asymmetricTtlOf(deletedTokenNum.toGrpcTokenId(),
						expiredAccountNum.toGrpcAccountId(), tokenBalance));
		assertEquals(adjustmentsFrom(ttls), returnTransfers);
	}

	@Test
	void doesTreasuryReturnForNonzeroBalance() {
		givenTokenPresent(survivedTokenNum, longLivedToken);
		final var treasuryRel = mutableRel(treasuryNum, survivedTokenNum, tokenBalance);
		givenModifiableRelPresent(treasuryNum, survivedTokenNum, treasuryRel);

		subject.updateReturns(expiredAccountNum, survivedTokenNum, tokenBalance, returnTransfers);

		final var ttls = List.of(
				ttlOf(survivedTokenGrpcId,
						expiredAccountNum.toGrpcAccountId(), treasuryId.toGrpcAccountId(), tokenBalance));
		assertEquals(adjustmentsFrom(ttls), returnTransfers);
		assertEquals(2 * tokenBalance, treasuryRel.getBalance());
	}

	private void givenTokenPresent(EntityNum id, MerkleToken token) {
		given(tokens.get(id)).willReturn(token);
	}

	private void givenModifiableRelPresent(EntityNum account, EntityNum token, MerkleTokenRelStatus mutableRel) {
		var rel = EntityNumPair.fromLongs(account.longValue(), token.longValue());
		given(tokenRels.getForModify(rel)).willReturn(mutableRel);
	}

	private MerkleTokenRelStatus mutableRel(EntityNum account, EntityNum token, long balance) {
		return new MerkleTokenRelStatus(balance, false, false, true);
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

	private final long tokenBalance = 1_234L;
	private final EntityId expiredTreasuryId = new EntityId(0, 0, 2L);
	private final EntityNum treasuryNum = EntityNum.fromLong(666L);
	private final EntityNum expiredAccountNum = expiredTreasuryId.asNum();
	private final EntityNum deletedTokenNum = EntityNum.fromLong(1234L);
	private final EntityNum survivedTokenNum = EntityNum.fromLong(4321L);
	private final EntityNum missingTokenNum = EntityNum.fromLong(5678L);
	private final EntityId treasuryId = treasuryNum.toEntityId();
	private final TokenID survivedTokenGrpcId = survivedTokenNum.toGrpcTokenId();
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
