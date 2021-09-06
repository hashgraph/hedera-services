package com.hedera.services.txns.token.process;

import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NewRels {
	public static List<TokenRelationship> listFrom(Token provisionalToken, int maxTokensPerAccount) {
		final var treasury = provisionalToken.getTreasury();
		final Set<Id> associatedSoFar = new HashSet<>();
		final List<TokenRelationship> newRels = new ArrayList<>();

		associateGiven(maxTokensPerAccount, provisionalToken, treasury, associatedSoFar, newRels);

		for (final var customFee : provisionalToken.getCustomFees()) {
			if (customFee.requiresCollectorAutoAssociation()) {
				final var collector = customFee.getValidatedCollector();
				associateGiven(maxTokensPerAccount, provisionalToken, collector, associatedSoFar, newRels);
			}
			customFee.nullOutCollector();
		}

		return newRels;
	}

	private static void associateGiven(
			final int maxTokensPerAccount,
			final Token provisionalToken,
			final Account account,
			final Set<Id> associatedSoFar,
			final List<TokenRelationship> newRelations
	)  {
		final var accountId = account.getId();
		if (associatedSoFar.contains(accountId)) {
			return;
		}

		final var newRel = provisionalToken.newEnabledRelationship(account);
		account.associateWith(List.of(provisionalToken), maxTokensPerAccount, false);
		newRelations.add(newRel);
		associatedSoFar.add(accountId);
	}

	private NewRels() {
		throw new UnsupportedOperationException("Utility Class");
	}
}
