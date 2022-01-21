/*
 * -
 *  * ‌
 *  * Hedera Services Node
 *  * ​
 *  * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  * ​
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ‍
 *
 */

package com.hedera.services.ledger.accounts;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

/**
 * For an accountID represented using alias, look up and return the resolved accountID with accountNum.
 * If a corresponding accountNumber exists for given alias the ResponseCode returned will be OK.
 * Else if the alias is invalid returns error response given, with the given aliasedID as resolved ID.
 */
public record AliasLookup(AccountID resolvedId, ResponseCodeEnum response) {
	public static AliasLookup of(final AccountID resolvedId, final ResponseCodeEnum response) {
		return new AliasLookup(resolvedId, response);
	}
}
