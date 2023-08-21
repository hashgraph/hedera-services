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

package com.hedera.node.app.workflows.handle;

import com.hedera.node.app.service.token.records.StakingContext;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Interface responsible for running any actions that need to happen at the end of
 * transaction handling. The reason it's called a consensus time hook is because
 * the actions are (possibly) triggered by checking the previous transaction's
 * consensus time against the consensus time of the current transaction.
 */
public interface ConsensusTimeHook {
    /**
     * Processing hook to run at the end of each transaction. There are certain actions that need
     * to be taken once we have a new consensus timestamp. Any such actions should be done here
     *
     * @param context the {@code StakingContext} context of the transaction being processed
     */
    void process(@NonNull final StakingContext context);
}
