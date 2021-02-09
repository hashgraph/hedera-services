package com.hedera.services.fees;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;

import static com.hedera.services.security.ops.SystemOpAuthorization.AUTHORIZED;

public class StandardExemptions implements FeeExemptions {
	private final AccountNumbers accountNums;
	private final SystemOpPolicies systemOpPolicies;

	public StandardExemptions(AccountNumbers accountNums, SystemOpPolicies systemOpPolicies) {
		this.accountNums = accountNums;
		this.systemOpPolicies = systemOpPolicies;
	}

	@Override
	public boolean hasExemptPayer(TxnAccessor accessor) {
		if (isAlwaysExempt(accessor.getPayer().getAccountNum())) {
			return true;
		}
		return systemOpPolicies.check(accessor) == AUTHORIZED;
	}

	private boolean isAlwaysExempt(long payerAccount) {
		return payerAccount == accountNums.treasury() || payerAccount == accountNums.systemAdmin();
	}
}
