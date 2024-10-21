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
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
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

    List<TssMessageTransactionBody> getTssMessages(Bytes rosterHash);
}
