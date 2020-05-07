package com.hedera.services.sigs.metadata.lookups;

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

import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hederahashgraph.api.proto.java.AccountID;

/**
 * Defines a simple type that is able to recover metadata about signing activity
 * associated with a given Hedera cryptocurrency account.
 *
 * @author Michael Tinker
 */
public interface AccountSigMetaLookup {
	/**
	 * Returns metadata for the given account's signing activity; e.g., whether
	 * the account must sign transactions in which it receives cryptocurrency.
	 *
	 * @param account the account to recover signing metadata for.
	 * @return the desired metadata.
	 * @throws Exception if no appropriate metadata exists.
	 */
	AccountSigningMetadata lookup(AccountID account) throws Exception;
}
