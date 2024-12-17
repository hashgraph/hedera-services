/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.records;

import com.hedera.hapi.node.base.TokenAssociation;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code StreamBuilder} specialization for tracking the effects of a {@code TokenUpdate}
 * transaction.
 */
public interface TokenUpdateStreamBuilder extends TokenBaseStreamBuilder {
    /**
     * Adds the token relations that are created by auto associations.
     * This information is needed while building the transfer list, to set the auto association flag.
     * @param tokenAssociation the token association that is created by auto association
     * @return the builder
     */
    TokenUpdateStreamBuilder addAutomaticTokenAssociation(@NonNull TokenAssociation tokenAssociation);
}
