package com.hedera.services.txns.crypto;

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

import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.tuple.Pair;

/**
 * A type able to perform one or more provisional account "auto-creations" from {@link BalanceChange}s
 * representing ℏ transfers to previously unused aliases; and then either submit the records of these
 * creations to a given {@link AccountRecordsHistorian}, <i>or</i> rollback any side-effects of these
 * provisional creations.
 */
public interface AutoCreationLogic {
	/**
	 * Sets the fee calculator to use for computing the auto-creation fees that are deducted from the
	 * ℏ being sent to a previously unused alias.
	 *
	 * @param feeCalculator
	 * 		the fee calculator to use
	 */
	void setFeeCalculator(FeeCalculator feeCalculator);

	/**
	 * Clears any state related to provisionally created accounts and their pending child records.
	 */
	void reset();

	/**
	 * Removes any aliases added to the {@link AliasManager} map as part of provisional creations.
	 *
	 * @return whether any aliases were removed
	 */
	boolean reclaimPendingAliases();

	/**
	 * Notifies the given {@link AccountRecordsHistorian} of the child records for any
	 * provisionally created accounts since the last call to {@link TopLevelAutoCreation#reset()}.
	 *
	 * @param recordsHistorian
	 * 		the records historian that should track the child records
	 */
	void submitRecordsTo(AccountRecordsHistorian recordsHistorian);

	/**
	 * Provisionally auto-creates an account in the given accounts ledger for the triggering balance change.
	 *
	 * Returns the amount deducted from the balance change as an auto-creation charge; or a failure code.
	 *
	 * <b>IMPORTANT:</b> If this change was to be part of a zero-sum balance change list, then after
	 * those changes are applied atomically, the returned fee must be given to the funding account!
	 *
	 * @param change
	 * 		a triggering change with unique alias
	 * @return the fee charged for the auto-creation if ok, a failure reason otherwise
	 */
	Pair<ResponseCodeEnum, Long> createFromTrigger(BalanceChange change);
}
