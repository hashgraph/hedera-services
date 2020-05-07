package com.hedera.services.fees.charging;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.fees.TxnFeeType;
import com.hederahashgraph.api.proto.java.AccountID;

import java.util.EnumSet;

/**
 * Defines a type able to screen whether the payer, node, and/or participants
 * of a well-known transaction can afford various fees. (In the case of the
 * payer, also whether its advertised willingness to pay is sufficient.)
 *
 * @author Michael Tinker
 */
public interface TxnScopedFeeScreening {
	/**
	 * Flags if the payer of the in-scope txn can afford the given fees.
	 *
	 * @param fees the fees in question
	 * @return if the payer can afford them
	 */
	boolean canPayerAfford(EnumSet<TxnFeeType> fees);

	/**
	 * Flags if the payer of the in-scope txn is willing to pay the given fees.
	 *
	 * @param fees the fees in question
	 * @return if the payer is willing to pay them
	 */
	boolean isPayerWillingToCover(EnumSet<TxnFeeType> fees);
	/**
	 * Flags if the payer of the in-scope txn is able to pay all fees
	 * it has advertised willingness to supply.
	 *
	 * @return if the payer can afford to fund its advertised willingness
	 */
	boolean isPayerWillingnessCredible();

	/**
	 * Flags if the given participant in the in-scope txn can afford the given fees.
	 *
	 * @param fees the fees in question
	 * @return if the participant can afford them
	 */
	boolean canParticipantAfford(AccountID participant, EnumSet<TxnFeeType> fees);
}
