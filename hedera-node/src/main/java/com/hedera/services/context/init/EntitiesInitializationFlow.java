/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.init;

import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.state.logic.NetworkCtxManager;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class EntitiesInitializationFlow {
    private static final Logger log = LogManager.getLogger(EntitiesInitializationFlow.class);

    private final ExpiryManager expiries;
    private final NetworkCtxManager networkCtxManager;
    private final SigImpactHistorian sigImpactHistorian;

    @Inject
    public EntitiesInitializationFlow(
            final ExpiryManager expiries,
            final SigImpactHistorian sigImpactHistorian,
            final NetworkCtxManager networkCtxManager) {
        this.expiries = expiries;
        this.sigImpactHistorian = sigImpactHistorian;
        this.networkCtxManager = networkCtxManager;
    }

    public void run() {
        expiries.reviewExistingPayerRecords();
        log.info("Payer records reviewed");
        /* Use any entities stored in state to rebuild queue of expired entities. */
        expiries.reviewExistingShortLivedEntities();
        log.info("Short-lived entities reviewed");

        sigImpactHistorian.invalidateCurrentWindow();
        log.info("Signature impact history invalidated");

        // Re-initialize the "observable" system files; that is, the files which have
        // associated callbacks managed by the SysFilesCallback object. We explicitly
        // re-mark the files are not loaded here, in case this is a reconnect.
        networkCtxManager.setObservableFilesNotLoaded();
        networkCtxManager.loadObservableSysFilesIfNeeded();
    }
}
