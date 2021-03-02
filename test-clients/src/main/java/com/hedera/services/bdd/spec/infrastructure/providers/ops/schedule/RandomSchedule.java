package com.hedera.services.bdd.spec.infrastructure.providers.ops.schedule;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.LookupUtils;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleCreate;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomTransfer.DEFAULT_NUM_STABLE_ACCOUNTS;
import static com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomTransfer.stableAccounts;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.suites.HapiApiSuite.A_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiApiSuite.DEFAULT_PAYER;
import static java.util.stream.Collectors.toList;

public class RandomSchedule implements OpProvider {
	private final AtomicInteger opNo = new AtomicInteger();
	private final RegistrySourcedNameProvider<ScheduleID> schedules;
	private final EntityNameProvider<AccountID> accounts;
	private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd();

	private int numStableAccounts = DEFAULT_NUM_STABLE_ACCOUNTS;
	static final long INITIAL_BALANCE = 1_000_000_000L;
	public static final int DEFAULT_CEILING_NUM = 100;
	private int ceilingNum = DEFAULT_CEILING_NUM;

	public RandomSchedule(
			RegistrySourcedNameProvider<ScheduleID> schedules,
			EntityNameProvider<AccountID> accounts
	) {
		this.schedules = schedules;
		this.accounts = accounts;
	}

	public RandomSchedule ceiling(int n) {
		ceilingNum = n;
		return this;
	}

	@Override
	public List<HapiSpecOperation> suggestedInitializers() {
		return stableAccounts(numStableAccounts).stream()
				.map(account ->
						cryptoCreate(my(account))
								.noLogging()
								.balance(INITIAL_BALANCE)
								.deferStatusResolution()
								.payingWith(UNIQUE_PAYER_ACCOUNT)
				)
				.collect(toList());
	}
	@Override
	public Optional<HapiSpecOperation> get() {
		if (schedules.numPresent() >= ceilingNum) {
			return Optional.empty();
		}
		final var involved = LookupUtils.twoDistinct(accounts);
		if (involved.isEmpty()) {
			return Optional.empty();
		}
		int id = opNo.getAndIncrement();

		String from = involved.get().getKey(), to = involved.get().getValue();

		HapiScheduleCreate op = scheduleCreate("schedule" + id,
				cryptoTransfer(tinyBarsFromTo( from,to, 1))
				.signedBy(from)
		)
				.signedBy(DEFAULT_PAYER)
				.fee(A_HUNDRED_HBARS)
				.inheritingScheduledSigs()
				.memo("randomlycreated" + id)
				.hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
				.hasKnownStatusFrom(permissibleOutcomes);
		return Optional.of(op);
	}

	private String my(String opName) {
		return unique(opName, RandomSchedule.class);
	}
}
