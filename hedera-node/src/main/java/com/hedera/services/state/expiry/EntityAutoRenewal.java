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
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.state.expiry.renewal.RenewalFeeHelper;
import com.hedera.services.state.expiry.renewal.RenewalHelper;
import com.hedera.services.state.expiry.renewal.RenewalProcess;
import com.hedera.services.state.merkle.MerkleAccount;

import java.time.Instant;

public class EntityAutoRenewal {
	private static final com.hederahashgraph.api.proto.java.Transaction EMPTY =
			com.hederahashgraph.api.proto.java.Transaction.getDefaultInstance();

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
		final int maxEntitiesToTouch = (int)dynamicProps.autoRenewMaxNumberOfEntitiesToRenewOrDelete();
		final int maxEntitiesToScan = (int)dynamicProps.autoRenewNumberOfEntitiesToScan();

		renewalProcess.beginRenewalCycle(instantNow);

		int entitiesTouched = 0;
		long scanNum = ctx.lastScannedEntity();
		for (int i = 1; i <= maxEntitiesToScan; i++) {
			scanNum++;
			if (scanNum == wrapNum) {
				scanNum = firstEntityToScan;
			}
			if (renewalProcess.process(scanNum)) {
				entitiesTouched++;
			}
//			final var classification = helper.classify(scanNum, now);

//			AccountID accountID = accountBuilder
//					.setAccountNum(scanNum)
//					.build();
//			if (backingAccounts.contains(accountID)) {
//				var merkleAccount = backingAccounts.getRef(accountID);
//				long expiry = merkleAccount.getExpiry();
//				if (expiry <= consensusTime.getEpochSecond()) {
//					numberOfEntitiesRenewedOrDeleted++;
//					long balance = merkleAccount.getBalance();
//					long newExpiry = expiry;
//					long fee = 100_000_000L;
//					if (0 == balance) {
//						backingAccounts.remove(accountID);
//					} else {
//						// Check the remaining balance to adjust the extension
//						long autoRenewSecs = merkleAccount.getAutoRenewSecs();
//						newExpiry = expiry + autoRenewSecs;
//						merkleAccount.setExpiry(newExpiry);
//						feeCollectorBalance += fee;
//						balance -= fee;
//						setBalance(merkleAccount, balance);
//					}
//					Instant actionTime = consensusTime.plusNanos(numberOfEntitiesRenewedOrDeleted);
//					var record = (0 == balance)
//							? EntityRemovalRecord.generatedFor(accountID, actionTime, accountID)
//							: AutoRenewalRecord.generatedForCrypto(accountID, actionTime, accountID, fee, newExpiry, feeCollector);
//					var recordStreamObject = new RecordStreamObject(record, EMPTY, actionTime);
//					ctx.updateRecordRunningHash(recordStreamObject.getRunningHash());
//					ctx.recordStreamManager().addRecordStreamObject(recordStreamObject);
//				}
//			}
			if (entitiesTouched >= maxEntitiesToTouch) {
				break;
			}
		}
//		setBalance(merkleFeeCollector, feeCollectorBalance);
//		backingAccounts.flushMutableRefs();
		renewalProcess.endRenewalCycle();
		ctx.updateLastScannedEntity(scanNum);
	}
}
