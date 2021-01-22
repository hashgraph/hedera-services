package com.hedera.services.bdd.spec.infrastructure.providers.ops.token;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.listeners.TokenAccountRegistryRel;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

public class RandomTokenAssociation implements OpProvider {
	static final Logger log = LogManager.getLogger(RandomTokenAssociation.class);

	public static final int MAX_TOKENS_PER_OP = 5;
	public static final int DEFAULT_CEILING_NUM = 10_000;

	private int ceilingNum = DEFAULT_CEILING_NUM;

	private final RegistrySourcedNameProvider<TokenID> tokens;
	private final RegistrySourcedNameProvider<AccountID> accounts;
	private final RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels;

	private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
			TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT,
			TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED,
			TOKEN_WAS_DELETED,
			ACCOUNT_DELETED
	);

	public RandomTokenAssociation(
			RegistrySourcedNameProvider<TokenID> tokens,
			RegistrySourcedNameProvider<AccountID> accounts,
			RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels
	) {
		this.tokens = tokens;
		this.accounts = accounts;
		this.tokenRels = tokenRels;
	}

	public RandomTokenAssociation ceiling(int n) {
		ceilingNum = n;
		return this;
	}

	@Override
	public Optional<HapiSpecOperation> get() {
		if (tokenRels.numPresent() >= ceilingNum) {
			return Optional.empty();
		}

		var account = accounts.getQualifying();
		if (account.isEmpty()) {
			return Optional.empty();
		}

		int numTokensToTry = BASE_RANDOM.nextInt(MAX_TOKENS_PER_OP) + 1;
		Set<String> chosen = new HashSet<>();
		while (numTokensToTry-- > 0) {
			var token = tokens.getQualifyingExcept(chosen);
			token.ifPresent(chosen::add);
		}
		if (chosen.isEmpty()) {
			return Optional.empty();
		}
		String[] toUse = chosen.toArray(new String[0]);
		var op = tokenAssociate(account.get(), toUse)
				.hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
				.hasKnownStatusFrom(permissibleOutcomes);

		return Optional.of(op);
	}
}
