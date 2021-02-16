package com.hedera.services.bdd.spec.infrastructure.providers.ops.token;

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

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenUpdate;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;

public class RandomTokenUpdate implements OpProvider {
	private static final int FREEZE_KEY_INDEX = 4;
	private static final List<BiConsumer<HapiTokenUpdate, String>> KEY_SETTERS = List.of(
			HapiTokenUpdate::kycKey,
			HapiTokenUpdate::wipeKey,
			HapiTokenUpdate::adminKey,
			HapiTokenUpdate::supplyKey,
			HapiTokenUpdate::freezeKey);

	public static final int DEFAULT_CEILING_NUM = 100;
	public static final int DEFAULT_MAX_STRING_LEN = 100;
	public static final long DEFAULT_MAX_SUPPLY = 1_000;

	private double kycKeyUpdateProb = 0.5;
	private double wipeKeyUpdateProb = 0.5;
	private double adminKeyUpdateProb = 0.5;
	private double supplyKeyUpdateProb = 0.5;
	private double freezeKeyUpdateProb = 0.5;
	private double autoRenewUpdateProb = 0.5;

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


	public RandomTokenUpdate(
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
		Optional<String> token = tokens.getQualifying();
		if (token.isEmpty())	{
			return Optional.empty();
		}

		HapiTokenUpdate op = tokenUpdate(token.get())
				.hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
				.hasKnownStatusFrom(permissibleOutcomes);

//		var prefix = randomlyConfigureKeys(op);
		//op.setTokenPrefix(prefix);

		randomlyConfigureSupply(op);  // possiblyUpdateSupply(op);
		randomlyConfigureAutoRenew(op); // possiblyUpdateAutoRenew(op);
		randomlyConfigureStrings(op);  // possiblyUpdateSymbol(op);
		// possiblyUpdateKeys(op);
		return Optional.of(op);
	}

	private void randomlyConfigureStrings(HapiTokenUpdate op) {
		op.name(randomUppercase(1 + BASE_RANDOM.nextInt(DEFAULT_MAX_STRING_LEN)));
		op.symbol(randomUppercase(1 + BASE_RANDOM.nextInt(DEFAULT_MAX_STRING_LEN)));
	}

	private void randomlyConfigureSupply(HapiTokenUpdate op) {
//		op.initialSupply(BASE_RANDOM.nextLong(0, DEFAULT_MAX_SUPPLY));
//		op.decimals(BASE_RANDOM.nextInt(0, Integer.MAX_VALUE));
	}

	private void randomlyConfigureAutoRenew(HapiTokenUpdate op) {
		if (BASE_RANDOM.nextDouble() < autoRenewUpdateProb) {
			var account = accounts.getQualifying();
			account.ifPresent(op::autoRenewAccount);
		}
	}

	private static final int kycFlagIndex = 1;
	private static final int wipeFlagIndex = 2;
	private static final int adminFlagIndex = 3;
	private static final int supplyFlagIndex = 4;
	private static final int freezeFlagIndex = 5;

	static boolean wasCreatedWithFreeze(String token) {
		return token.charAt(freezeFlagIndex) == 'Y';
	}

	static boolean wasCreatedWithWipe(String token) {
		return token.charAt(wipeFlagIndex) == 'Y';
	}

	static boolean wasCreatedWithAdmin(String token) {
		return token.charAt(adminFlagIndex) == 'Y';
	}

	static boolean wasCreatedWithSupply(String token) {
		return token.charAt(supplyFlagIndex) == 'Y';
	}

	static boolean wasCreatedWithKyc(String token) {
		return token.charAt(kycFlagIndex) == 'Y';
	}

//	private String randomlyConfigureKeys(HapiTokenUpdate op) {
//		double[] probs = new double[] { kycKeyUpdateProb, wipeKeyUpdateProb, adminKeyUpdateProb, supplyKeyUpdateProb, freezeKeyUpdateProb };
//
//		var sb = new StringBuilder("[");
//		for (int i = 0; i < probs.length; i++) {
//			if (BASE_RANDOM.nextDouble() < probs[i]) {
//				var key = keys.getQualifying();
//				if (key.isPresent()) {
//					if (i == FREEZE_KEY_INDEX) {
//						op.freezeDefault(BASE_RANDOM.nextBoolean());
//					}
//					KEY_SETTERS.get(i).accept(op, key.get());
//					sb.append("Y");
//				} else {
//					sb.append("N");
//				}
//			} else {
//				sb.append("N");
//			}
//		}
//		return sb.append("]").toString();
//	}

	private void possiblyUpdateFreezeKey(HapiTokenUpdate op) {
		if (BASE_RANDOM.nextDouble() < 0.5) {
			var key = keys.getQualifying();
			if (key.isPresent()) {
				op.freezeKey(key.get());
			}
		}
	}


	private String my(String opName) {
		return unique(opName, RandomTokenUpdate.class);
	}
}
