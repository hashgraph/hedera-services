package com.hedera.services.context.init;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.ServicesState;
import com.hedera.services.context.ServicesContext;
import com.swirlds.blob.BinaryObjectStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * Static helper to run the initialization sequence for a {@link ServicesContext}
 * given a newly initialized {@link ServicesState}.
 */
public class InitializationFlow {
	private static final Logger log = LogManager.getLogger(InitializationFlow.class);

	private static Supplier<BinaryObjectStore> blobStoreSupplier = BinaryObjectStore::getInstance;

	public static void accept(ServicesState state, ServicesContext ctx) {
		ctx.update(state);
		log.info("Context updated with working state");
		ctx.setRecordsInitialHash(state.runningHashLeaf().getRunningHash().getHash());
		log.info("Record running hash initialized");

		/* Set the primitive state in the context and signal the managing stores (if
		 * they are already constructed) to rebuild their auxiliary views of the state.
		 * All the initialization that follows will be a function of the primitive state. */
		ctx.rebuildBackingStoresIfPresent();
		log.info("Backing stores rebuilt; now rebuilding their internal views");
		ctx.rebuildStoreViewsIfPresent();
		log.info("Store internal views rebuilt; now rebuilding unique token views");
		ctx.uniqTokenViewsManager().rebuildNotice(state.tokens(), state.uniqueTokens());
		log.info("Unique token views rebuilt; now reviewing existing payer records");

		/* Use any payer records stored in state to rebuild the recent transaction
		 * history. This history has two main uses: Purging expired records, and
		 * classifying duplicate transactions. */
		ctx.expiries().reviewExistingPayerRecords();
		log.info("Payer records reviewed; now reviewing existing short-lived entities");
		/* Use any entities stored in state to rebuild queue of expired entities. */
		ctx.expiries().reviewExistingShortLivedEntities();
		log.info("Short-lived entities reviewed");

		/* Re-initialize the "observable" system files; that is, the files which have
	 	associated callbacks managed by the SysFilesCallback object. We explicitly
	 	re-mark the files are not loaded here, in case this is a reconnect. (During a
	 	reconnect the blob store might still be reloading, and we will finish loading
	 	the observable files in the ServicesMain.init method.) */
		ctx.networkCtxManager().setObservableFilesNotLoaded();
		if (!blobStoreSupplier.get().isInitializing()) {
			log.info("Blob store is ready, loading observable system files");
			ctx.networkCtxManager().loadObservableSysFilesIfNeeded();
			log.info("Finished loading observable system files");
		}
	}

	static void setBlobStoreSupplier(Supplier<BinaryObjectStore> blobStoreSupplier) {
		InitializationFlow.blobStoreSupplier = blobStoreSupplier;
	}
}
