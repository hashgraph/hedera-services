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

package com.hedera.node.app.workflows.handle.flow.infra;

import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class PreHandleLogic {
    private static final Logger logger = LogManager.getLogger(PreHandleLogic.class);
    final PreHandleWorkflow preHandleWorkflow;
    final SolvencyPreCheck solvencyPreCheck;

    @Inject
    public PreHandleLogic(
            @NonNull final PreHandleWorkflow preHandleWorkflow, @NonNull final SolvencyPreCheck solvencyPreCheck) {
        this.preHandleWorkflow = preHandleWorkflow;
        this.solvencyPreCheck = solvencyPreCheck;
    }

    /*
     * This method gets all the verification data for the current transaction. If pre-handle was previously ran
     * successfully, we only add the missing keys. If it did not run or an error occurred, we run it again.
     * If there is a due diligence error, this method will return a CryptoTransfer to charge the node along with
     * its verification data.
     */
    @NonNull
    public PreHandleResult getCurrentPreHandleResult(
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn,
            final ReadableStoreFactory storeFactory) {
        final var metadata = platformTxn.getMetadata();
        final PreHandleResult previousResult;
        if (metadata instanceof PreHandleResult result) {
            previousResult = result;
        } else {
            // This should be impossible since the Platform contract guarantees that SwirldState.preHandle()
            // is always called before SwirldState.handleTransaction(); and our preHandle() implementation
            // always sets the metadata to a PreHandleResult
            logger.error(
                    "Received transaction without PreHandleResult metadata from node {} (was {})",
                    creator.nodeId(),
                    metadata);
            previousResult = null;
        }
        // We do not know how long transactions are kept in memory. Clearing metadata to avoid keeping it for too long.
        platformTxn.setMetadata(null);
        return preHandleWorkflow.preHandleTransaction(
                creator.accountId(),
                storeFactory,
                storeFactory.getStore(ReadableAccountStore.class),
                platformTxn,
                previousResult);
    }
}
