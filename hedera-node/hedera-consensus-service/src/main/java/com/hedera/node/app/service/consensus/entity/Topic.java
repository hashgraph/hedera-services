/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.entity;

import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

/** An entity represents a Consensus Topic. */
public interface Topic {
    /**
     * Topic's shard number
     *
     * @return shard number
     */
    long shardNumber();
    /**
     * Topic's realm number
     *
     * @return realm number
     */
    long realmNumber();
    /**
     * Topic's number
     *
     * @return topic number
     */
    long topicNumber();

    Optional<HederaKey> getAdminKey();

    Optional<HederaKey> getSubmitKey();

    String memo();

    long autoRenewAccountNumber();

    long autoRenewSecs();

    long expiry();

    boolean deleted();

    long sequenceNumber();

    /**
     * Creates an AccountBuilder that clones all state in this instance, allowing the user to
     * override only the specific state that they choose to override.
     *
     * @return A non-null builder pre-initialized with all state in this instance.
     */
    @NonNull
    TopicBuilder copy();
}
