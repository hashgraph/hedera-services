package com.hedera.services.ledger;

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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.ledger.BalanceChange.DEFAULT_PAYER;
import static com.hedera.services.ledger.BalanceChange.NO_TOKEN_FOR_HBAR_ADJUST;
import static com.hedera.services.ledger.BalanceChange.changingNftOwnership;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.nftXfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceChangeTest {
	private static final AccountID payerID = AccountID.newBuilder().setAccountNum(12345L).build();
	private static final AccountID revokedSpender = AccountID.newBuilder().setAccountNum(123L).build();
	private static final EntityNum payerNum = EntityNum.fromAccountId(payerID);
	private static final TokenID fungibleTokenID = TokenID.newBuilder().setTokenNum(1234L).build();
	private static final TokenID nonFungibleTokenID = TokenID.newBuilder().setTokenNum(1235L).build();
	private static final FcTokenAllowanceId fungibleAllowanceId =
			FcTokenAllowanceId.from(EntityNum.fromTokenId(fungibleTokenID), payerNum);
	private static final FcTokenAllowanceId nftAllowanceId =
			FcTokenAllowanceId.from(EntityNum.fromTokenId(nonFungibleTokenID), payerNum);
	private static final Map<EntityNum, Long> CRYPTO_ALLOWANCES = new HashMap<>();
	private static final Map<FcTokenAllowanceId, Long> FUNGIBLE_ALLOWANCES = new HashMap<>();
	private static final Map<FcTokenAllowanceId, FcTokenAllowance> NFT_ALLOWANCES = new HashMap<>();
	static {
		CRYPTO_ALLOWANCES.put(payerNum, 100L);
		FUNGIBLE_ALLOWANCES.put(fungibleAllowanceId, 100L);
		NFT_ALLOWANCES.put(fungibleAllowanceId, FcTokenAllowance.from(true));
		NFT_ALLOWANCES.put(nftAllowanceId, FcTokenAllowance.from(List.of(1L, 2L)));
	}

	private final Id t = new Id(1, 2, 3);
	private final long delta = -1_234L;
	private final long serialNo = 1234L;
	private final AccountID a = asAccount("1.2.3");
	private final AccountID b = asAccount("2.3.4");

	@Mock
	MerkleAccount owner;

	@Test
	void objectContractSanityChecks() {
		// given:
		final var hbarChange = IdUtils.hbarChange(a, delta);
		final var tokenChange = IdUtils.tokenChange(t, a, delta);
		final var nftChange = changingNftOwnership(t, t.asGrpcToken(), nftXfer(a, b, serialNo), payerID);
		// and:
		final var hbarRepr = "BalanceChange{token=ℏ, account=Id[shard=1, realm=2, num=3], alias=, units=-1234, expectedDecimals=-1}";
		final var tokenRepr = "BalanceChange{token=Id[shard=1, realm=2, num=3], account=Id[shard=1, realm=2, num=3], " +
				"alias=, units=-1234, expectedDecimals=-1}";
		final var nftRepr = "BalanceChange{nft=Id[shard=1, realm=2, num=3], serialNo=1234, " +
				"from=Id[shard=1, realm=2, num=3], to=Id[shard=2, realm=3, num=4]}";

		// expect:
		assertNotEquals(hbarChange, tokenChange);
		assertNotEquals(hbarChange.hashCode(), tokenChange.hashCode());
		// and:
		assertEquals(hbarRepr, hbarChange.toString());
		assertEquals(tokenRepr, tokenChange.toString());
		assertEquals(nftRepr, nftChange.toString());
		// and:
		assertSame(a, hbarChange.accountId());
		assertEquals(delta, hbarChange.getAggregatedUnits());
		assertEquals(t.asGrpcToken(), tokenChange.tokenId());
	}

	@Test
	void recognizesFungibleTypes() {
		// given:
		final var hbarChange = IdUtils.hbarChange(a, delta);
		final var tokenChange = IdUtils.tokenChange(t, a, delta);

		assertTrue(hbarChange.isForHbar());
		assertFalse(tokenChange.isForHbar());
		// and:
		assertFalse(hbarChange.isForNft());
		assertFalse(tokenChange.isForNft());
	}

	@Test
	void noTokenForHbarAdjust() {
		final var hbarChange = IdUtils.hbarChange(a, delta);
		assertSame(NO_TOKEN_FOR_HBAR_ADJUST, hbarChange.tokenId());
	}

	@Test
	void hbarAdjust() {
		final var hbarAdjust = BalanceChange.hbarAdjust(Id.DEFAULT, 10);
		assertEquals(Id.DEFAULT, hbarAdjust.getAccount());
		assertTrue(hbarAdjust.isForHbar());
		assertEquals(0, hbarAdjust.getAllowanceUnits());
		assertEquals(10, hbarAdjust.getAggregatedUnits());
		assertEquals(10, hbarAdjust.originalUnits());
		hbarAdjust.aggregateUnits(10);
		assertEquals(20, hbarAdjust.getAggregatedUnits());
		assertEquals(10, hbarAdjust.originalUnits());
	}

	@Test
	void objectContractWorks() {
		final var adjust = BalanceChange.hbarAdjust(Id.DEFAULT, 10);
		adjust.setCodeForInsufficientBalance(INSUFFICIENT_PAYER_BALANCE);
		assertEquals(INSUFFICIENT_PAYER_BALANCE, adjust.codeForInsufficientBalance());
		adjust.setExemptFromCustomFees(false);
		assertFalse(adjust.isExemptFromCustomFees());
	}

	@Test
	void tokenAdjust() {
		final var tokenAdjust = BalanceChange.tokenAdjust(
				IdUtils.asModelId("1.2.3"),
				IdUtils.asModelId("3.2.1"),
				10, DEFAULT_PAYER, true);
		assertEquals(10, tokenAdjust.getAggregatedUnits());
		assertEquals(10, tokenAdjust.getAllowanceUnits());
		assertEquals(new Id(1, 2, 3), tokenAdjust.getAccount());
		assertEquals(new Id(3, 2, 1), tokenAdjust.getToken());
	}

	@Test
	void ownershipChangeFactoryWorks() {
		// setup:
		final var xfer = NftTransfer.newBuilder()
				.setSenderAccountID(a)
				.setReceiverAccountID(b)
				.setSerialNumber(serialNo)
				.build();

		// given:
		final var nftChange = changingNftOwnership(t, t.asGrpcToken(), xfer, payerID);

		// expect:
		assertEquals(a, nftChange.accountId());
		assertEquals(b, nftChange.counterPartyAccountId());
		assertEquals(t.asGrpcToken(), nftChange.tokenId());
		assertEquals(serialNo, nftChange.serialNo());
		// and:
		assertTrue(nftChange.isForNft());
		assertEquals(new NftId(t.shard(), t.realm(), t.num(), serialNo), nftChange.nftId());
	}

	@Test
	void canReplaceAlias() {
		final var created = IdUtils.asAccount("0.0.1234");
		final var anAlias = ByteString.copyFromUtf8("abcdefg");
		final var subject = BalanceChange.changingHbar(AccountAmount.newBuilder()
				.setAmount(1234)
				.setAccountID(AccountID.newBuilder()
						.setAlias(anAlias))
				.build(), payerID);

		subject.replaceAliasWith(created);
		assertFalse(subject.hasNonEmptyAlias());
		assertEquals(created, subject.accountId());
	}

	@Test
	void settersAndGettersOfDecimalsWorks(){
		final var created = new Id(1, 2, 3);
		final var token = new Id(4, 5, 6);
		final var subject = BalanceChange.changingFtUnits(token, token.asGrpcToken(),
				AccountAmount.newBuilder()
						.setAmount(1234)
						.setAccountID(created.asGrpcAccount())
						.build(), payerID);
		assertEquals(-1, subject.getExpectedDecimals());
		assertFalse(subject.hasExpectedDecimals());

		subject.setExpectedDecimals(2);

		assertEquals(2, subject.getExpectedDecimals());
		assertTrue(subject.hasExpectedDecimals());
	}

	@Test
	void failsAsExpectedWhenSpenderIsNotGrantedAllowance() {
		when(owner.getCryptoAllowances()).thenReturn(CRYPTO_ALLOWANCES);
		BalanceChange subject = BalanceChange.changingHbar(
				AccountAmount.newBuilder().setAmount(-50).setAccountID(a).setIsApproval(true).build(), revokedSpender);

		assertEquals(SPENDER_DOES_NOT_HAVE_ALLOWANCE, subject.checkAllowanceUsageUsing(owner));
	}

	@Test
	void failsAsExpectedWhenSpenderHasInsufficientAllowance() {
		when(owner.getCryptoAllowances()).thenReturn(CRYPTO_ALLOWANCES);
		BalanceChange subject = BalanceChange.changingHbar(
				AccountAmount.newBuilder().setAmount(-101).setAccountID(a).setIsApproval(true).build(), payerID);

		assertEquals(AMOUNT_EXCEEDS_ALLOWANCE, subject.checkAllowanceUsageUsing(owner));
	}

	@Test
	void failsAsExpectedWhenSpenderIsNotGrantedAllowanceOnFungible() {
		when(owner.getFungibleTokenAllowances()).thenReturn(FUNGIBLE_ALLOWANCES);
		BalanceChange subject = BalanceChange.changingFtUnits(
				Id.fromGrpcToken(fungibleTokenID), fungibleTokenID,
				AccountAmount.newBuilder().setAmount(-50).setAccountID(a).setIsApproval(true).build(),
				revokedSpender);

		assertEquals(SPENDER_DOES_NOT_HAVE_ALLOWANCE, subject.checkAllowanceUsageUsing(owner));
	}

	@Test
	void failsAsExpectedWhenSpenderIsHasInsufficientAllowanceOnFungible() {
		when(owner.getFungibleTokenAllowances()).thenReturn(FUNGIBLE_ALLOWANCES);
		BalanceChange subject = BalanceChange.changingFtUnits(
				Id.fromGrpcToken(fungibleTokenID), fungibleTokenID,
				AccountAmount.newBuilder().setAmount(-101).setAccountID(a).setIsApproval(true).build(),
				payerID);

		assertEquals(AMOUNT_EXCEEDS_ALLOWANCE, subject.checkAllowanceUsageUsing(owner));
	}

	@Test
	void failsAsExpectedWhenSpenderIsNotGrantedAllowanceOnNFT() {
		when(owner.getNftAllowances()).thenReturn(NFT_ALLOWANCES);
		BalanceChange subject = BalanceChange.changingNftOwnership(
				Id.fromGrpcToken(fungibleTokenID), fungibleTokenID,
				NftTransfer.newBuilder()
						.setIsApproval(true)
						.setSenderAccountID(a)
						.setReceiverAccountID(b)
						.setSerialNumber(1L).build(),
				revokedSpender);

		assertEquals(SPENDER_DOES_NOT_HAVE_ALLOWANCE, subject.checkAllowanceUsageUsing(owner));
	}

	@Test
	void failsAsExpectedWhenSpenderIsHasNoAllowanceOnSpecificNFT() {
		when(owner.getNftAllowances()).thenReturn(NFT_ALLOWANCES);
		BalanceChange subject = BalanceChange.changingNftOwnership(
				Id.fromGrpcToken(nonFungibleTokenID), nonFungibleTokenID,
				NftTransfer.newBuilder()
						.setIsApproval(true)
						.setSenderAccountID(a)
						.setReceiverAccountID(b)
						.setSerialNumber(3L).build(),
				payerID);

		assertEquals(SPENDER_DOES_NOT_HAVE_ALLOWANCE, subject.checkAllowanceUsageUsing(owner));
	}

	@Test
	void happyPathWithSpenderIsHasAllowance() {
		when(owner.getCryptoAllowances()).thenReturn(CRYPTO_ALLOWANCES);
		BalanceChange subject = BalanceChange.changingHbar(
				AccountAmount.newBuilder().setAmount(-45).setAccountID(a).setIsApproval(true).build(), payerID);

		assertEquals(OK, subject.checkAllowanceUsageUsing(owner));
	}

	@Test
	void happyPathWithSpenderIsHasAllowanceOnFungible() {
		when(owner.getFungibleTokenAllowances()).thenReturn(FUNGIBLE_ALLOWANCES);
		BalanceChange subject = BalanceChange.changingFtUnits(
				Id.fromGrpcToken(fungibleTokenID), fungibleTokenID,
				AccountAmount.newBuilder().setAmount(-80).setAccountID(a).setIsApproval(true).build(),
				payerID);

		assertEquals(OK, subject.checkAllowanceUsageUsing(owner));
	}

	@Test
	void happyPathWithSpenderIsHasAllowanceOnAllNFT() {
		when(owner.getNftAllowances()).thenReturn(NFT_ALLOWANCES);
		BalanceChange subject = BalanceChange.changingNftOwnership(
				Id.fromGrpcToken(fungibleTokenID), fungibleTokenID,
				NftTransfer.newBuilder()
						.setIsApproval(true)
						.setSenderAccountID(a)
						.setReceiverAccountID(b)
						.setSerialNumber(3L).build(),
				payerID);

		assertEquals(OK, subject.checkAllowanceUsageUsing(owner));
	}

	@Test
	void happyPathWithSpenderIsHasAllowanceOnSpecificNFT() {
		when(owner.getNftAllowances()).thenReturn(NFT_ALLOWANCES);
		BalanceChange subject = BalanceChange.changingNftOwnership(
				Id.fromGrpcToken(nonFungibleTokenID), nonFungibleTokenID,
				NftTransfer.newBuilder()
						.setIsApproval(true)
						.setSenderAccountID(a)
						.setReceiverAccountID(b)
						.setSerialNumber(2L).build(),
				payerID);

		assertEquals(OK, subject.checkAllowanceUsageUsing(owner));
	}
}
