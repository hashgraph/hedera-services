/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.sigs.metadata;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.sigs.order.LinkedRefs;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.*;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

/**
 * Defines a type able to look up metadata associated to the signing activities of any Hedera entity
 * (account, smart contract, file, topic, or token).
 */
public interface SigMetadataLookup {
    SafeLookupResult<FileSigningMetadata> fileSigningMetaFor(FileID id, @Nullable LinkedRefs linkedRefs);

    SafeLookupResult<TopicSigningMetadata> topicSigningMetaFor(TopicID id, @Nullable LinkedRefs linkedRefs);

    SafeLookupResult<TokenSigningMetadata> tokenSigningMetaFor(TokenID id, @Nullable LinkedRefs linkedRefs);

    SafeLookupResult<AccountSigningMetadata> accountSigningMetaFor(AccountID id, @Nullable LinkedRefs linkedRefs);

    SafeLookupResult<ScheduleSigningMetadata> scheduleSigningMetaFor(ScheduleID id, @Nullable LinkedRefs linkedRefs);

    SafeLookupResult<ContractSigningMetadata> aliasableContractSigningMetaFor(
            ContractID idOrAlias, @Nullable LinkedRefs linkedRefs);

    SafeLookupResult<AccountSigningMetadata> aliasableAccountSigningMetaFor(
            AccountID idOrAlias, @Nullable LinkedRefs linkedRefs);

    Instant sourceSignedAt();

    /**
     * Gets the unaliased form of an account. If the account is not an alias, returns the account.
     * Otherwise, returns the account to which the alias points.
     *
     * @param idOrAlias id of the account or alias
     * @param linkedRefs the linked references
     * @return the unaliased account
     */
    EntityNum unaliasedAccount(AccountID idOrAlias, @Nullable LinkedRefs linkedRefs);

    SafeLookupResult<AccountSigningMetadata> accountSigningMetaFor(ByteString alias, LinkedRefs linkedRefs);
}
