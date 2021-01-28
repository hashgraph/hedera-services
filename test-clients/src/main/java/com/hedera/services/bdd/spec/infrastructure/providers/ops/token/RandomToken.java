package com.hedera.services.bdd.spec.infrastructure.providers.ops.token;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenCreate;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;

public class RandomToken implements OpProvider {
	private static final int FREEZE_KEY_INDEX = 4;
	private static final List<BiConsumer<HapiTokenCreate, String>> KEY_SETTERS = List.of(
			HapiTokenCreate::kycKey,
			HapiTokenCreate::wipeKey,
			HapiTokenCreate::adminKey,
			HapiTokenCreate::supplyKey,
			HapiTokenCreate::freezeKey);

	public static final int DEFAULT_CEILING_NUM = 100;
	public static final int DEFAULT_MAX_STRING_LEN = 100;
	public static final long DEFAULT_MAX_SUPPLY = 1_000;

	private int ceilingNum = DEFAULT_CEILING_NUM;
	private double kycKeyProb = 0.5;
	private double wipeKeyProb = 0.5;
	private double adminKeyProb = 0.5;
	private double supplyKeyProb = 0.5;
	private double freezeKeyProb = 0.5;
	private double autoRenewProb = 0.5;

	private final AtomicInteger opNo = new AtomicInteger();
	private final EntityNameProvider<Key> keys;
	private final RegistrySourcedNameProvider<TokenID> tokens;
	private final RegistrySourcedNameProvider<AccountID> accounts;

	private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
			/* Auto-renew account might be deleted by the time our TokenCreate reaches consensus */
			INVALID_AUTORENEW_ACCOUNT,
			/* The randomly chosen treasury might already have tokens.maxPerAccount associated tokens */
			TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED,
			/* Treasury account might be deleted by the time our TokenCreate reaches consensus */
			INVALID_TREASURY_ACCOUNT_FOR_TOKEN
	);

	public RandomToken ceiling(int n) {
		ceilingNum = n;
		return this;
	}

	public RandomToken(
			EntityNameProvider<Key> keys,
			RegistrySourcedNameProvider<TokenID> tokens,
			RegistrySourcedNameProvider<AccountID> accounts
	) {
		this.keys = keys;
		this.tokens = tokens;
		this.accounts = accounts;
	}

	@Override
	public Optional<HapiSpecOperation> get() {
		if (tokens.numPresent() >= ceilingNum) {
			return Optional.empty();
		}

		int id = opNo.getAndIncrement();
		HapiTokenCreate op = tokenCreate(my("token" + id))
				.hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
				.hasKnownStatusFrom(permissibleOutcomes);

		randomlyConfigureKeys(op);
		randomlyConfigureSupply(op);
		randomlyConfigureAutoRenew(op);
		randomlyConfigureStrings(op);

		return Optional.of(op);
	}

	private void randomlyConfigureStrings(HapiTokenCreate op) {
		op.name(randomUppercase(1 + BASE_RANDOM.nextInt(DEFAULT_MAX_STRING_LEN)));
		op.symbol(randomUppercase(1 + BASE_RANDOM.nextInt(DEFAULT_MAX_STRING_LEN)));
	}

	private void randomlyConfigureSupply(HapiTokenCreate op) {
		op.initialSupply(BASE_RANDOM.nextLong(0, DEFAULT_MAX_SUPPLY));
		op.decimals(BASE_RANDOM.nextInt(0, Integer.MAX_VALUE));
	}

	private void randomlyConfigureAutoRenew(HapiTokenCreate op) {
		if (BASE_RANDOM.nextDouble() < autoRenewProb) {
			var account = accounts.getQualifying();
			account.ifPresent(op::autoRenewAccount);
		}
	}

	private void randomlyConfigureKeys(HapiTokenCreate op) {
		double[] probs = new double[] { kycKeyProb, wipeKeyProb, adminKeyProb, supplyKeyProb, freezeKeyProb };

		for (int i = 0; i < probs.length; i++) {
			if (BASE_RANDOM.nextDouble() < probs[i]) {
				var key = keys.getQualifying();
				if (key.isPresent()) {
					if (i == FREEZE_KEY_INDEX) {
						op.freezeDefault(BASE_RANDOM.nextBoolean());
					}
					KEY_SETTERS.get(i).accept(op, key.get());
				}
			}
		}
	}

	private String my(String opName) {
		return unique(opName, RandomToken.class);
	}
}
