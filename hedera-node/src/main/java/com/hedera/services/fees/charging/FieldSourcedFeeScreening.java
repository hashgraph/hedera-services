package com.hedera.services.fees.charging;

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

import com.hedera.services.fees.FeeExemptions;
import com.hedera.services.fees.TxnFeeType;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;

import java.util.EnumMap;
import java.util.EnumSet;

/**
 * Implements a {@link TxnScopedFeeScreening} using an injected
 * {@link FeeExemptions} and fee information configured by collaborators
 * via the {@link FieldSourcedFeeScreening#setFor(TxnFeeType, long)}
 * method.
 *
 * @author Michael Tinker
 */
public class FieldSourcedFeeScreening implements TxnScopedFeeScreening {
	private boolean payerExemption;
	private BalanceCheck check;
	private final FeeExemptions exemptions;
	protected TxnAccessor accessor;
	EnumMap<TxnFeeType, Long> feeAmounts = new EnumMap<>(TxnFeeType.class);

	public FieldSourcedFeeScreening(FeeExemptions exemptions) {
		this.exemptions = exemptions;
	}

	public void setBalanceCheck(BalanceCheck check) {
		this.check = check;
	}

	public void resetFor(TxnAccessor accessor) {
		this.accessor = accessor;
		payerExemption = exemptions.hasExemptPayer(accessor);
	}

	public void setFor(TxnFeeType fee, long amount) {
		feeAmounts.put(fee, amount);
	}

	@Override
	public boolean canPayerAfford(EnumSet<TxnFeeType> fees) {
		return isPayerExempt() || check.canAfford(accessor.getPayer(), totalAmountOf(fees));
	}

	@Override
	public boolean isPayerWillingToCover(EnumSet<TxnFeeType> fees) {
		return isPayerExempt() || accessor.getTxn().getTransactionFee() >= totalAmountOf(fees);
	}

	@Override
	public boolean isPayerWillingnessCredible() {
		return isPayerExempt() || check.canAfford(accessor.getPayer(), accessor.getTxn().getTransactionFee());
	}

	protected boolean isPayerExempt() {
		return payerExemption;
	}

	@Override
	public boolean canParticipantAfford(AccountID participant, EnumSet<TxnFeeType> fees) {
		return check.canAfford(participant, totalAmountOf(fees));
	}

	protected long totalAmountOf(EnumSet<TxnFeeType> fees) {
		return fees.stream()
				.filter(fee -> feeAmounts.containsKey(fee))
				.mapToLong(feeAmounts::get)
				.sum();
	}
}
