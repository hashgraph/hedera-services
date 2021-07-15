package com.hedera.services.grpc.marshalling;

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.NftTransfer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BalanceChangeManagerTest {
	private BalanceChangeManager subject;

	@BeforeEach
	void setUp() {
		subject = new BalanceChangeManager(startingList, 2);
	}

	@Test
	void understandsTriggerCandidates() {
		// expect:
		assertSame(firstFungibleTrigger, subject.nextTriggerCandidate());
		assertSame(secondFungibleTrigger, subject.nextTriggerCandidate());
		assertSame(firstNonFungibleTrigger, subject.nextTriggerCandidate());
		assertNull(subject.nextTriggerCandidate());
	}

	@Test
	void changesSoFarAreSized() {
		// expect:
		assertEquals(7, subject.changesSoFar());
	}

	@Test
	void includesChangeAsExpected() {
		// when:
		subject.includeChange(miscHbarAdjust);
		// and:
		final var newChanges = subject.getChangesSoFar();

		// then:
		assertEquals(8, newChanges.size());
		assertSame(miscHbarAdjust, newChanges.get(7));
		assertSame(miscHbarAdjust, subject.changeFor(misc, Id.MISSING_ID));
	}

	@Test
	void failsHardOnRepeatedInclusion() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.includeChange(payerHbarAdjust));
		assertThrows(IllegalArgumentException.class, () -> subject.includeChange(firstFungibleTrigger));
	}

	@Test
	void existingChangesAreIndexed() {
		// expect:
		assertSame(payerHbarAdjust, subject.changeFor(payer, Id.MISSING_ID));
		assertSame(fundingHbarAdjust, subject.changeFor(funding, Id.MISSING_ID));
		assertSame(firstFungibleTrigger, subject.changeFor(payer, firstFungibleTokenId));
		assertSame(firstFungibleNonTrigger, subject.changeFor(funding, firstFungibleTokenId));
		assertSame(secondFungibleTrigger, subject.changeFor(payer, secondFungibleTokenId));
		assertSame(secondFungibleNonTrigger, subject.changeFor(funding, secondFungibleTokenId));
		assertNull(subject.changeFor(payer, nonFungibleTokenId));
	}

	private final long amountOfFirstFungibleDebit = 1_000L;
	private final long amountOfSecondFungibleDebit = 2_000L;
	private final Id misc = new Id(1, 1, 2);
	private final Id payer = new Id(0, 1, 2);
	private final Id funding = new Id(0, 0, 98);
	private final Id firstFungibleTokenId = new Id(1, 2, 3);
	private final Id nonFungibleTokenId = new Id(7, 4, 7);
	private final Id secondFungibleTokenId = new Id(3, 2, 1);
	private final AccountAmount firstFungibleDebit = AccountAmount.newBuilder()
			.setAccountID(payer.asGrpcAccount())
			.setAmount(-amountOfFirstFungibleDebit)
			.build();
	private final AccountAmount firstFungibleCredit = AccountAmount.newBuilder()
			.setAccountID(funding.asGrpcAccount())
			.setAmount(+amountOfFirstFungibleDebit)
			.build();
	private final AccountAmount secondFungibleDebit = AccountAmount.newBuilder()
			.setAccountID(payer.asGrpcAccount())
			.setAmount(-amountOfSecondFungibleDebit)
			.build();
	private final AccountAmount secondFungibleCredit = AccountAmount.newBuilder()
			.setAccountID(funding.asGrpcAccount())
			.setAmount(+amountOfSecondFungibleDebit)
			.build();
	private final BalanceChange firstFungibleTrigger = BalanceChange.changingFtUnits(
			firstFungibleTokenId, firstFungibleTokenId.asGrpcToken(), firstFungibleDebit);
	private final BalanceChange firstFungibleNonTrigger = BalanceChange.changingFtUnits(
			firstFungibleTokenId, firstFungibleTokenId.asGrpcToken(), firstFungibleCredit);
	private final BalanceChange secondFungibleTrigger = BalanceChange.changingFtUnits(
			secondFungibleTokenId, secondFungibleTokenId.asGrpcToken(), secondFungibleDebit);
	private final BalanceChange secondFungibleNonTrigger = BalanceChange.changingFtUnits(
			secondFungibleTokenId, secondFungibleTokenId.asGrpcToken(), secondFungibleCredit);
	private final NftTransfer firstOwnershipChange = NftTransfer.newBuilder()
			.setSenderAccountID(payer.asGrpcAccount())
			.setReceiverAccountID(funding.asGrpcAccount())
			.build();
	private final BalanceChange firstNonFungibleTrigger = BalanceChange.changingNftOwnership(
			nonFungibleTokenId, nonFungibleTokenId.asGrpcToken(), firstOwnershipChange);
	private final BalanceChange secondNonFungibleTrigger = BalanceChange.changingNftOwnership(
			nonFungibleTokenId, nonFungibleTokenId.asGrpcToken(), firstOwnershipChange);
	private final BalanceChange payerHbarAdjust = BalanceChange.hbarAdjust(payer, -1_234_567);
	private final BalanceChange fundingHbarAdjust = BalanceChange.hbarAdjust(funding, +1_234_567);
	private final BalanceChange miscHbarAdjust = BalanceChange.hbarAdjust(misc, +1);

	private final List<BalanceChange> startingList = new ArrayList<>();
	{
		startingList.add(payerHbarAdjust);
		startingList.add(fundingHbarAdjust);
		startingList.add(firstFungibleTrigger);
		startingList.add(firstFungibleNonTrigger);
		startingList.add(secondFungibleTrigger);
		startingList.add(secondFungibleNonTrigger);
		startingList.add(firstNonFungibleTrigger);
	}
}