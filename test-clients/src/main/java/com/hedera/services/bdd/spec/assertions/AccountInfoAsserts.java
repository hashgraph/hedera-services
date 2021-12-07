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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.bdd.suites.HapiApiSuite.ONE_HBAR;
import static com.hedera.services.legacy.core.CommonUtils.calculateSolidityAddress;
import static com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AccountInfoAsserts extends BaseErroringAssertsProvider<AccountInfo> {
	public static AccountInfoAsserts accountWith() {
		return new AccountInfoAsserts();
	}

	public AccountInfoAsserts noChangesFromSnapshot(final String snapshot) {
		registerProvider((spec, o) -> {
			final var expected = spec.registry().getAccountInfo(snapshot);
			final var actual = (AccountInfo) o;
			assertEquals(expected, actual, "Changes occurred since snapshot '" + snapshot + "'");
		});
		return this;
	}

	public AccountInfoAsserts newAssociationsFromSnapshot(
			final String snapshot,
			final List<ExpectedTokenRel> newRels
	) {
		for (final var newRel : newRels) {
			registerProvider((spec, o) -> {
				final var baseline = spec.registry().getAccountInfo(snapshot);
				for (final var existingRel : baseline.getTokenRelationshipsList()) {
					assertFalse(newRel.matches(spec, existingRel),
							"Expected no existing rel to match " + newRel
									+ ", but " + existingRel + " did");
				}

				final var current = (AccountInfo) o;
				var someMatches = false;
				for (final var currentRel : current.getTokenRelationshipsList()) {
					someMatches |= newRel.matches(spec, currentRel);
				}
				assertTrue(someMatches, "Expected some new rel to match " + newRel + ", but none did");
			});
		}
		return this;
	}

	public AccountInfoAsserts accountId(String account) {
		registerProvider((spec, o) -> {
			assertEquals(spec.registry().getAccountID(account),
					((AccountInfo) o).getAccountID(),
					"Bad account Id!");
		});
		return this;
	}

	public AccountInfoAsserts proxy(String idLiteral) {
		registerProvider((spec, o) -> {
			assertEquals(HapiPropertySource.asAccount(idLiteral),
					((AccountInfo) o).getProxyAccountID(),
					"Bad proxy id!");
		});
		return this;
	}

	public AccountInfoAsserts solidityId(String cid) {
		registerProvider((spec, o) -> {
			AccountID id = spec.registry().getAccountID(cid);
			String solidityId = calculateSolidityAddress(0, id.getRealmNum(), id.getAccountNum());
			assertEquals(solidityId,
					((AccountInfo) o).getContractAccountID(),
					"Bad Solidity contract Id!");
		});
		return this;
	}

	public AccountInfoAsserts key(String key) {
		registerProvider((spec, o) -> {
			assertEquals(spec.registry().getKey(key), ((AccountInfo) o).getKey(), "Bad key!");
		});
		return this;
	}

	public AccountInfoAsserts receiverSigReq(Boolean isReq) {
		registerProvider((spec, o) -> {
			assertEquals(isReq, ((AccountInfo) o).getReceiverSigRequired(), "Bad receiver sig requirement!");
		});
		return this;
	}

	public AccountInfoAsserts isDeleted(Boolean isDead) {
		registerProvider((spec, o) -> {
			assertEquals(isDead, ((AccountInfo) o).getDeleted(), "Bad deletion status!");
		});
		return this;
	}

	public AccountInfoAsserts balance(long amount) {
		registerProvider((spec, o) -> {
			assertEquals(amount, ((AccountInfo) o).getBalance(), "Bad balance!");
		});
		return this;
	}

	public AccountInfoAsserts expectedBalanceWithChargedUsd(long amount, double expectedUsdToSubstract, double allowedPercentDiff) {
		registerProvider((spec, o) -> {
			var expectedTinyBarsToSubtract = expectedUsdToSubstract
					* 100
					* spec.ratesProvider().rates().getHbarEquiv() / spec.ratesProvider().rates().getCentEquiv()
					* ONE_HBAR;
			var expected = amount - expectedTinyBarsToSubtract;
			assertEquals(expected, ((AccountInfo) o).getBalance(), (allowedPercentDiff / 100.0) * expected, "Bad balance!");
		});
		return this;
	}

	public AccountInfoAsserts alias(ByteString alias) {
		registerProvider((spec, o) -> {
			assertEquals(alias, ((AccountInfo) o).getAlias(), "Bad Alias!");
		});
		return this;
	}

	public AccountInfoAsserts balanceLessThan(long amount) {
		registerProvider((spec, o) -> {
			long actual = ((AccountInfo) o).getBalance();
			String errorMessage = String.format("Bad balance! %s is not less than %s", actual, amount);
			assertTrue(actual < amount, errorMessage);
		});
		return this;
	}

	public AccountInfoAsserts memo(String memo) {
		registerProvider((spec, o) -> {
			assertEquals(memo, ((AccountInfo) o).getMemo(), "Bad memo!");
		});
		return this;
	}

	public AccountInfoAsserts balance(Function<HapiApiSpec, Function<Long, Optional<String>>> dynamicCondition) {
		registerProvider((spec, o) -> {
			Function<Long, Optional<String>> expectation = dynamicCondition.apply(spec);
			long actual = ((AccountInfo) o).getBalance();
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

	public AccountInfoAsserts sendThreshold(long amount) {
		registerProvider((spec, o) -> {
			assertEquals(amount, ((AccountInfo) o).getGenerateSendRecordThreshold(), "Bad send threshold!");
		});
		return this;
	}

	public AccountInfoAsserts receiveThreshold(long amount) {
		registerProvider((spec, o) -> {
			assertEquals(amount, ((AccountInfo) o).getGenerateReceiveRecordThreshold(),
					"Bad receive threshold!");
		});
		return this;
	}

	public AccountInfoAsserts expiry(long approxTime, long epsilon) {
		registerProvider((spec, o) -> {
			long expiry = ((AccountInfo) o).getExpirationTime().getSeconds();
			assertTrue(Math.abs(approxTime - expiry) <= epsilon,
					String.format("Expiry %d not in [%d, %d]!", approxTime, expiry - epsilon, expiry + epsilon));
		});
		return this;
	}

	public AccountInfoAsserts expiry(String registryEntry, long delta) {
		registerProvider((spec, o) -> {
			long expiry = ((AccountInfo) o).getExpirationTime().getSeconds();
			long expected = spec.registry().getAccountInfo(registryEntry).getExpirationTime().getSeconds() + delta;
			assertEquals(expected, expiry, "Bad expiry!");
		});
		return this;
	}

	public AccountInfoAsserts autoRenew(long period) {
		registerProvider((spec, o) -> {
			assertEquals(period, ((AccountInfo) o).getAutoRenewPeriod().getSeconds(),
					"Bad auto-renew period!");
		});
		return this;
	}
}
