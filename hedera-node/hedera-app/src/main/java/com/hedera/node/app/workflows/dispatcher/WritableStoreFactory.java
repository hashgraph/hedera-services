/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.dispatcher;

import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;

/**
 * Factory for all writable stores.
 */
public interface WritableStoreFactory {
    /**
     * Get a {@link WritableTopicStore}.
     *
     * @return a new {@link WritableTopicStore}
     */
    WritableTopicStore createTopicStore();

    /**
     * Get a {@link WritableTokenStore}.
     *
     * @return a new {@link WritableTokenStore}
     */
    WritableTokenStore createTokenStore();

    /**
     * Get a {@link WritableTokenRelationStore}.
     *
     * @return a new {@link WritableTokenRelationStore}
     */
    public WritableTokenRelationStore createTokenRelStore();
}
