package com.hedera.services.bdd.spec.assertions;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.GrantedCryptoAllowance;
import com.hederahashgraph.api.proto.java.GrantedNftAllowance;
import com.hederahashgraph.api.proto.java.GrantedTokenAllowance;
import com.hederahashgraph.api.proto.java.Key;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.bdd.suites.HapiApiSuite.ONE_HBAR;
import static com.hederahashgraph.api.proto.java.GetAccountDetailsResponse.AccountDetails;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AccountDetailsAsserts extends BaseErroringAssertsProvider<AccountDetails> {
	public static AccountDetailsAsserts accountWith() {
		return new AccountDetailsAsserts();
	}

	public AccountDetailsAsserts noChangesFromSnapshot(final String snapshot) {
		registerProvider((spec, o) -> {
			final var expected = spec.registry().getAccountDetails(snapshot);
			final var actual = (AccountDetails) o;
			assertEquals(expected, actual, "Changes occurred since snapshot '" + snapshot + "'");
		});
		return this;
	}

	public AccountDetailsAsserts newAssociationsFromSnapshot(
			final String snapshot,
			final List<ExpectedTokenRel> newRels
	) {
		for (final var newRel : newRels) {
			registerProvider((spec, o) -> {
				final var baseline = spec.registry().getAccountDetails(snapshot);
				for (final var existingRel : baseline.getTokenRelationshipsList()) {
					assertFalse(newRel.matches(spec, existingRel),
							"Expected no existing rel to match " + newRel
									+ ", but " + existingRel + " did");
				}

				final var current = (AccountDetails) o;
				var someMatches = false;
				for (final var currentRel : current.getTokenRelationshipsList()) {
					someMatches |= newRel.matches(spec, currentRel);
				}
				assertTrue(someMatches, "Expected some new rel to match " + newRel + ", but none did");
			});
		}
		return this;
	}

	public AccountDetailsAsserts accountId(String account) {
		registerProvider((spec, o) -> {
			assertEquals(spec.registry().getAccountID(account),
					((AccountDetails) o).getAccountId(),
					"Bad account Id!");
		});
		return this;
	}

	public AccountDetailsAsserts proxy(String idLiteral) {
		registerProvider((spec, o) -> {
			assertEquals(HapiPropertySource.asAccount(idLiteral),
					((AccountDetails) o).getProxyAccountId(),
					"Bad proxy id!");
		});
		return this;
	}

	public AccountDetailsAsserts solidityId(String cid) {
		registerProvider((spec, o) -> {
			AccountID id = spec.registry().getAccountID(cid);
			final var solidityId = HapiPropertySource.asHexedSolidityAddress(id);
			assertEquals(solidityId,
					((AccountDetails) o).getContractAccountId(),
					"Bad Solidity contract Id!");
		});
		return this;
	}

	public AccountDetailsAsserts key(String key) {
		registerProvider((spec, o) -> {
			assertEquals(spec.registry().getKey(key), ((AccountDetails) o).getKey(),
					"Bad key!");
		});
		return this;
	}

	public AccountDetailsAsserts key(Key key) {
		registerProvider((spec, o) -> {
			assertEquals(key, ((AccountDetails) o).getKey(), "Bad key!");
		});
		return this;
	}

	public AccountDetailsAsserts receiverSigReq(Boolean isReq) {
		registerProvider((spec, o) -> {
			assertEquals(isReq, ((AccountDetails) o).getReceiverSigRequired(),
					"Bad receiver sig requirement!");
		});
		return this;
	}

	public AccountDetailsAsserts balance(long amount) {
		registerProvider((spec, o) -> {
			assertEquals(amount, ((AccountDetails) o).getBalance(), "Bad balance!");
		});
		return this;
	}

	public AccountDetailsAsserts expectedBalanceWithChargedUsd(
			final long amount,
			final double expectedUsdToSubstract,
			final double allowedPercentDiff
	) {
		registerProvider((spec, o) -> {
			var expectedTinyBarsToSubtract = expectedUsdToSubstract
					* 100
					* spec.ratesProvider().rates().getHbarEquiv() / spec.ratesProvider().rates().getCentEquiv()
					* ONE_HBAR;
			var expected = amount - expectedTinyBarsToSubtract;
			assertEquals(
					expected,
					((AccountDetails) o).getBalance(),
					(allowedPercentDiff / 100.0) * expected,
					"Unexpected balance");
		});
		return this;
	}

	public AccountDetailsAsserts hasAlias() {
		registerProvider((spec, o) -> {
			assertFalse(((AccountDetails) o).getAlias().isEmpty(), "Has no Alias!");
		});
		return this;
	}

	public AccountDetailsAsserts alias(String alias) {
		registerProvider((spec, o) -> {
			assertEquals(spec.registry().getKey(alias).toByteString(),
					((AccountDetails) o).getAlias(), "Bad Alias!");
		});
		return this;
	}

	public AccountDetailsAsserts noAlias() {
		registerProvider((spec, o) -> {
			assertTrue(((AccountDetails) o).getAlias().isEmpty(), "Bad Alias!");
		});
		return this;
	}

	public AccountDetailsAsserts balanceLessThan(long amount) {
		registerProvider((spec, o) -> {
			long actual = ((AccountDetails) o).getBalance();
			String errorMessage = String.format("Bad balance! %s is not less than %s", actual, amount);
			assertTrue(actual < amount, errorMessage);
		});
		return this;
	}

	public AccountDetailsAsserts memo(String memo) {
		registerProvider((spec, o) -> {
			assertEquals(memo, ((AccountDetails) o).getMemo(), "Bad memo!");
		});
		return this;
	}

	public AccountDetailsAsserts balance(Function<HapiApiSpec, Function<Long, Optional<String>>> dynamicCondition) {
		registerProvider((spec, o) -> {
			Function<Long, Optional<String>> expectation = dynamicCondition.apply(spec);
			long actual = ((AccountDetails) o).getBalance();
			Optional<String> failure = expectation.apply(actual);
			if (failure.isPresent()) {
				Assertions.fail("Bad balance! :: " + failure.get());
			}
		});
		return this;
	}

	public static Function<HapiApiSpec, Function<Long, Optional<String>>> changeFromSnapshot(
			String snapshot,
			Function<HapiApiSpec, Long> expDeltaFn
	) {
		return approxChangeFromSnapshot(snapshot, expDeltaFn, 0L);
	}

	public static Function<HapiApiSpec, Function<Long, Optional<String>>> changeFromSnapshot(
			String snapshot,
			long expDelta
	) {
		return approxChangeFromSnapshot(snapshot, expDelta, 0L);
	}

	public static Function<HapiApiSpec, Function<Long, Optional<String>>> approxChangeFromSnapshot(
			String snapshot,
			long expDelta,
			long epsilon
	) {
		return approxChangeFromSnapshot(snapshot, ignore -> expDelta, epsilon);
	}

	public static Function<HapiApiSpec, Function<Long, Optional<String>>> approxChangeFromSnapshot(
			String snapshot,
			Function<HapiApiSpec, Long> expDeltaFn,
			long epsilon
	) {
		return spec -> actual -> {
			long expDelta = expDeltaFn.apply(spec);
			long actualDelta = actual - spec.registry().getBalanceSnapshot(snapshot);
			if (Math.abs(actualDelta - expDelta) <= epsilon) {
				return Optional.empty();
			} else {
				return Optional.of(
						String.format("Expected balance change from '%s' to be <%d +/- %d>, was <%d>!",
								snapshot, expDelta, epsilon, actualDelta));
			}
		};
	}

	public AccountDetailsAsserts expiry(long approxTime, long epsilon) {
		registerProvider((spec, o) -> {
			long expiry = ((AccountDetails) o).getExpirationTime().getSeconds();
			assertTrue(Math.abs(approxTime - expiry) <= epsilon,
					String.format("Expiry %d not in [%d, %d]!", expiry, approxTime - epsilon, approxTime + epsilon));
		});
		return this;
	}

	public AccountDetailsAsserts expiry(String registryEntry, long delta) {
		registerProvider((spec, o) -> {
			long expiry = ((AccountDetails) o).getExpirationTime().getSeconds();
			long expected = spec.registry().getAccountDetails(registryEntry).getExpirationTime().getSeconds() + delta;
			assertEquals(expected, expiry, "Bad expiry!");
		});
		return this;
	}

	public AccountDetailsAsserts autoRenew(long period) {
		registerProvider((spec, o) -> {
			assertEquals(period, ((AccountDetails) o).getAutoRenewPeriod().getSeconds(),
					"Bad auto-renew period!");
		});
		return this;
	}

	public AccountDetailsAsserts noAllowances() {
		registerProvider((spec, o) -> {
			assertEquals(((AccountDetails) o).getGrantedCryptoAllowancesCount(), 0,
					"Bad CryptoAllowances count!");
			assertEquals(((AccountDetails) o).getGrantedTokenAllowancesCount(), 0,
					"Bad TokenAllowances count!");
			assertEquals(((AccountDetails) o).getGrantedNftAllowancesCount(), 0,
					"Bad NftAllowances count!");
		});
		return this;
	}

	public AccountDetailsAsserts cryptoAllowancesContaining(String spender, long allowance) {

		registerProvider((spec, o) -> {
			var cryptoAllowance = GrantedCryptoAllowance.newBuilder().setAmount(allowance)
					.setSpender(spec.registry().getAccountID(spender)).build();
			assertTrue(((AccountDetails) o).getGrantedCryptoAllowancesList().contains(
							cryptoAllowance),
					"Bad CryptoAllowances!");
		});
		return this;
	}

	public AccountDetailsAsserts tokenAllowancesContaining(String token, String spender, long allowance) {
		registerProvider((spec, o) -> {
			var tokenAllowance = GrantedTokenAllowance.newBuilder()
					.setAmount(allowance)
					.setTokenId(spec.registry().getTokenID(token))
					.setSpender(spec.registry().getAccountID(spender)).build();
			assertTrue(((AccountDetails) o).getGrantedTokenAllowancesList().contains(
							tokenAllowance),
					"Bad TokenAllowances!");
		});
		return this;
	}

	public AccountDetailsAsserts nftApprovedAllowancesContaining(String token, String spender) {
		registerProvider((spec, o) -> {
			var nftAllowance = GrantedNftAllowance.newBuilder()
					.setTokenId(spec.registry().getTokenID(token))
					.setSpender(spec.registry().getAccountID(spender))
					.build();
			assertTrue(
					((AccountDetails) o).getGrantedNftAllowancesList().contains(nftAllowance),
					"Bad NftAllowances!");
		});
		return this;
	}

	public AccountDetailsAsserts noCryptoAllowances() {
		registerProvider((spec, o) -> {
			assertTrue(((AccountDetails) o).getGrantedCryptoAllowancesList().isEmpty(),
					"Bad NftAllowances!");
		});
		return this;
	}

	public AccountDetailsAsserts noTokenAllowances(String owner) {
		registerProvider((spec, o) -> {
			assertTrue(((AccountDetails) o).getGrantedTokenAllowancesList().isEmpty(),
					"Bad NftAllowances!");
		});
		return this;
	}

	public AccountDetailsAsserts cryptoAllowancesCount(int count) {
		registerProvider((spec, o) -> {
			assertEquals(count, ((AccountDetails) o).getGrantedCryptoAllowancesCount(),
					"Bad CryptoAllowances!");
		});
		return this;
	}

	public AccountDetailsAsserts tokenAllowancesCount(int count) {
		registerProvider((spec, o) -> {
			assertEquals(count, ((AccountDetails) o).getGrantedTokenAllowancesCount(),
					"Bad TokenAllowances!");
		});
		return this;
	}

	public AccountDetailsAsserts nftApprovedForAllAllowancesCount(int count) {
		registerProvider((spec, o) -> {
			assertEquals(count, ((AccountDetails) o).getGrantedNftAllowancesCount(),
					"Bad NFTAllowances!");
		});
		return this;
	}
}
