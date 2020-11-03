package com.hedera.services.bdd.spec.queries.crypto;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import org.junit.Assert;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public class ExpectedTokenRel {
	private final String token;

	private OptionalLong balance = OptionalLong.empty();
	private Optional<TokenKycStatus> kycStatus = Optional.empty();
	private Optional<TokenFreezeStatus> freezeStatus = Optional.empty();

	private ExpectedTokenRel(String token) {
		this.token = token;
	}

	public static ExpectedTokenRel relationshipWith(String token) {
		return new ExpectedTokenRel(token);
	}

	public static void assertNoUnexpectedRels(
			String account,
			List<String> expectedAbsent,
			List<TokenRelationship> actualRels,
			HapiApiSpec spec
	) {
		for (String unexpectedToken : expectedAbsent) {
			for (TokenRelationship actualRel : actualRels) {
				var unexpectedId = spec.registry().getTokenID(unexpectedToken);
				if (actualRel.getTokenId().equals(unexpectedId)) {
					Assert.fail(String.format(
							"Account '%s' should have had no relationship with token '%s'!",
							account,
							unexpectedToken));
				}
			}
		}
	}

	public static void assertExpectedRels(
			String account,
			List<ExpectedTokenRel> expectedRels,
			List<TokenRelationship> actualRels,
			HapiApiSpec spec
	) {
		for (ExpectedTokenRel rel : expectedRels) {
			boolean found = false;
			var expectedId = spec.registry().getTokenID(rel.getToken());
			for (TokenRelationship actualRel : actualRels) {
				if (actualRel.getTokenId().equals(expectedId)) {
					found = true;
					rel.getBalance().ifPresent(a -> Assert.assertEquals(a, actualRel.getBalance()));
					rel.getKycStatus().ifPresent(s -> Assert.assertEquals(s, actualRel.getKycStatus()));
					rel.getFreezeStatus().ifPresent(s -> Assert.assertEquals(s, actualRel.getFreezeStatus()));
				}
			}
			if (!found) {
				Assert.fail(String.format(
						"Account '%s' had no relationship with token '%s'!",
						account,
						rel.getToken()));
			}
		}
	}

	public ExpectedTokenRel balance(long expected) {
		balance = OptionalLong.of(expected);
		return this;
	}

	public ExpectedTokenRel kyc(TokenKycStatus expected) {
		kycStatus = Optional.of(expected);
		return this;
	}

	public ExpectedTokenRel freeze(TokenFreezeStatus expected) {
		freezeStatus = Optional.of(expected);
		return this;
	}

	public String getToken() {
		return token;
	}

	public OptionalLong getBalance() {
		return balance;
	}

	public Optional<TokenKycStatus> getKycStatus() {
		return kycStatus;
	}

	public Optional<TokenFreezeStatus> getFreezeStatus() {
		return freezeStatus;
	}
}
