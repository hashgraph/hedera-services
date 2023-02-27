/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system.events;

import com.swirlds.common.system.ReachedConsensus;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import java.util.Iterator;

/**
 * An event that has reached consensus.
 * <p>
 * IMPORTANT: Although this interface is not sealed, it should only be implemented by internal classes. This
 * interface may be changed at any time, in any way, without notice or prior deprecation. Third parties should NOT
 * implement this interface.
 */
public interface ConsensusEvent extends Event, ReachedConsensus {

    /**
     * Returns an iterator over the application events in this transaction, which have all reached consensus. Each
     * invocation returns a new iterator over the same transactions. This method is thread safe.
     *
     * @return a consensus transaction iterator
     */
    Iterator<ConsensusTransaction> consensusTransactionIterator();
}
