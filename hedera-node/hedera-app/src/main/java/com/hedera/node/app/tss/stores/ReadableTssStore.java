/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss.stores;

import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssEncryptionKeyTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

public interface ReadableTssStore {
    /**
     * Get the TSS message for the given key.
     *
     * @param TssMessageMapKey The key to look up.
     * @return The TSS message, or null if not found.
     */
    TssMessageTransactionBody getMessage(@NonNull TssMessageMapKey TssMessageMapKey);

    /**
     * Check if a TSS message exists for the given key.
     *
     * @param tssMessageMapKey The key to check.
     * @return True if a TSS message exists for the given key, false otherwise.
     */
    boolean exists(@NonNull TssMessageMapKey tssMessageMapKey);

    /**
     * Get the TSS vote for the given key.
     *
     * @param tssVoteMapKey The key to look up.
     * @return The TSS vote, or null if not found.
     */
    TssVoteTransactionBody getVote(@NonNull TssVoteMapKey tssVoteMapKey);

    /**
     * Check if a TSS vote exists for the given key.
     *
     * @param tssVoteMapKey The key to check.
     * @return True if a TSS vote exists for the given key, false otherwise.
     */
    boolean exists(@NonNull TssVoteMapKey tssVoteMapKey);

    /**
     * Get the number of entries in the TSS message state.
     *
     * @return The number of entries in the tss message state.
     */
    long messageStateSize();

    /**
     * Get the list of Tss messages for the given roster hash.
     * @param rosterHash The roster hash to look up.
     * @return The list of Tss messages, or an empty list if not found.
     */
    List<TssMessageTransactionBody> getTssMessageBodies(Bytes rosterHash);

    /**
     * Get the list of Tss votes for the given roster hash.
     * @param rosterHash The roster hash to look up.
     * @return The list of Tss votes, or an empty list if not found.
     */
    List<TssVoteTransactionBody> getTssVoteBodies(Bytes rosterHash);

    /**
     * Get the Tss encryption key transaction body for the given node ID.
     * @param nodeID The node ID to look up.
     * @return The Tss encryption key transaction body, or null if not found.
     */
    @Nullable
    TssEncryptionKeyTransactionBody getTssEncryptionKey(final long nodeID);
}
