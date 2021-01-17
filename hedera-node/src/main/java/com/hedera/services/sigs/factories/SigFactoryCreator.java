package com.hedera.services.sigs.factories;

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

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.utils.SignedTxnAccessor;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleEntityId.fromScheduleId;

public class SigFactoryCreator {
	public static Logger log = LogManager.getLogger(SigFactoryCreator.class);

	static final byte[] MISSING_SCHEDULED_TXN_BYTES = new byte[0];

	private final Supplier<FCMap<MerkleEntityId, MerkleSchedule>> scheduledTxns;

	public SigFactoryCreator(Supplier<FCMap<MerkleEntityId, MerkleSchedule>> scheduledTxns) {
		this.scheduledTxns = scheduledTxns;
	}

	public TxnScopedPlatformSigFactory createScopedFactory(SignedTxnAccessor accessor) {
		System.out.println("Function: " + accessor.getFunction());
		log.info("Function {}", accessor.getFunction());
		switch (accessor.getFunction()) {
			case ScheduleCreate:
				log.info("The scheduled body is {}", accessor.getTxn().getScheduleCreate().getTransactionBody());
				return new ScheduleBodySigningSigFactory(
						accessor.getTxnBytes(),
						accessor.getTxn().getScheduleCreate().getTransactionBody().toByteArray());
			case ScheduleSign:
				var schid = fromScheduleId(accessor.getTxn().getScheduleSign().getScheduleID());
				var curScheduledTxns = scheduledTxns.get();
				if (curScheduledTxns.containsKey(schid)) {
					return new ScheduleBodySigningSigFactory(
							accessor.getTxnBytes(),
							curScheduledTxns.get(schid).transactionBody());
				} else {
					/* We don't want to fail during signature expansion/rationalization; if
					this {@code ScheduleSign} txn references a non-existent scheduled txn,
					it will resolve to a meaningful error code later. */
					return new ScheduleBodySigningSigFactory(accessor.getTxnBytes(), MISSING_SCHEDULED_TXN_BYTES);
				}
			default:
				return new BodySigningSigFactory(accessor);
		}
	}
}
