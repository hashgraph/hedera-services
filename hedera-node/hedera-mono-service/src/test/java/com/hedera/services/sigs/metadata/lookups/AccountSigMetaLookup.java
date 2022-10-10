/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.sigs.metadata.lookups;

import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.sigs.metadata.SafeLookupResult;
import com.hederahashgraph.api.proto.java.AccountID;

/**
 * Defines a simple type that is able to recover metadata about signing activity associated with a
 * given Hedera cryptocurrency account.
 */
public interface AccountSigMetaLookup {
    SafeLookupResult<AccountSigningMetadata> safeLookup(AccountID id);

    SafeLookupResult<AccountSigningMetadata> aliasableSafeLookup(AccountID idOrAlias);
}
