package com.hedera.services.state.expiry;

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

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.expiry.renewal.RenewalProcess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

public class EntityAutoRenewal {
	private static final Logger log = LogManager.getLogger(EntityAutoRenewal.class);

	private final long firstEntityToScan;
	private final RenewalProcess renewalProcess;
	private final ServicesContext ctx;
	private final GlobalDynamicProperties dynamicProps;

	public EntityAutoRenewal(
			HederaNumbers hederaNumbers,
			RenewalProcess renewalProcess,
			ServicesContext ctx,
			GlobalDynamicProperties dynamicProps
	) {
		this.ctx = ctx;
		this.renewalProcess = renewalProcess;
		this.dynamicProps = dynamicProps;

		this.firstEntityToScan = hederaNumbers.numReservedSystemEntities() + 1;
	}

	public void execute(Instant instantNow) {
		if (!dynamicProps.autoRenewEnabled()) {
			return;
		}

		final long wrapNum = ctx.seqNo().current();
		if (wrapNum == firstEntityToScan) {
			/* No non-system entities in the system, can abort */
			return;
		}

		final int maxEntitiesToTouch = dynamicProps.autoRenewMaxNumberOfEntitiesToRenewOrDelete();
		final int maxEntitiesToScan = dynamicProps.autoRenewNumberOfEntitiesToScan();

		renewalProcess.beginRenewalCycle(instantNow);

		int i = 1, entitiesTouched = 0;
		long scanNum = ctx.lastScannedEntity();

		log.debug("Auto-renew scan beginning at {}, wrapping at {}", scanNum, wrapNum);
		log.debug("	- BEFORE #'s are (accounts={}, tokenRels={})",
				() -> ctx.accounts().size(), () -> ctx.tokenAssociations().size());
		for (; i <= maxEntitiesToScan; i++) {
			scanNum++;
			if (scanNum == wrapNum) {
				scanNum = firstEntityToScan;
			}
			if (renewalProcess.process(scanNum)) {
				entitiesTouched++;
			}
			if (entitiesTouched >= maxEntitiesToTouch) {
				break;
			}
		}
		log.debug("Finished auto-renew scan of {} entities ({} touched) -> {} was last", i, entitiesTouched, scanNum);
		log.debug("	- AFTER #'s are (accounts={}, tokenRels={})",
				() -> ctx.accounts().size(), () -> ctx.tokenAssociations().size());

		renewalProcess.endRenewalCycle();
		ctx.updateLastScannedEntity(scanNum);
	}
}
