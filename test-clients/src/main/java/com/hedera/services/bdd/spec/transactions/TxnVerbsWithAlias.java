package com.hedera.services.bdd.spec.transactions;

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

import com.hedera.services.bdd.spec.queries.crypto.ReferenceType;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoDelete;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoUpdate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenAssociate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenDissociate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenFreeze;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenKycGrant;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenKycRevoke;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenUnfreeze;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenWipe;

import java.util.List;

public class TxnVerbsWithAlias {
	public static HapiCryptoDelete cryptoDeleteAliased(final String alias) {
		return new HapiCryptoDelete(alias, ReferenceType.ALIAS_KEY_NAME);
	}

	public static HapiCryptoUpdate cryptoUpdateAliased(final String alias) {
		return new HapiCryptoUpdate(alias, ReferenceType.ALIAS_KEY_NAME);
	}

	public static HapiTokenAssociate tokenAssociateAliased(final String alias, String... tokens) {
		return new HapiTokenAssociate(alias, ReferenceType.ALIAS_KEY_NAME, List.of(tokens));
	}

	public static HapiTokenAssociate tokenAssociateAliased(final String alias, List<String> tokens) {
		return new HapiTokenAssociate(alias, ReferenceType.ALIAS_KEY_NAME, tokens);
	}

	public static HapiTokenDissociate tokenDissociateAliased(final String alias, String... tokens) {
		return new HapiTokenDissociate(alias, ReferenceType.ALIAS_KEY_NAME, List.of(tokens));
	}

	public static HapiTokenDissociate tokenDissociateAliased(final String alias, List<String> tokens) {
		return new HapiTokenDissociate(alias, ReferenceType.ALIAS_KEY_NAME, tokens);
	}

	public static HapiTokenFreeze tokenFreezeAliased(String token, String account) {
		return new HapiTokenFreeze(token, account, ReferenceType.ALIAS_KEY_NAME);
	}

	public static HapiTokenUnfreeze tokenUnfreezeAliased(String token, String account) {
		return new HapiTokenUnfreeze(token, account, ReferenceType.ALIAS_KEY_NAME);
	}

	public static HapiTokenKycGrant grantTokenKycAliased(String token, String account) {
		return new HapiTokenKycGrant(token, account, ReferenceType.ALIAS_KEY_NAME);
	}

	public static HapiTokenKycRevoke revokeTokenKycAliased(String token, String account) {
		return new HapiTokenKycRevoke(token, account, ReferenceType.ALIAS_KEY_NAME);
	}

	public static HapiTokenWipe wipeTokenAccountAliased(String token, String account, long amount) {
		return new HapiTokenWipe(token, account, amount, ReferenceType.ALIAS_KEY_NAME);
	}

	public static HapiTokenWipe wipeTokenAccountAliased(String token, String account, List<Long> serialNumbers) {
		return new HapiTokenWipe(token, account, serialNumbers, ReferenceType.ALIAS_KEY_NAME);
	}
}
