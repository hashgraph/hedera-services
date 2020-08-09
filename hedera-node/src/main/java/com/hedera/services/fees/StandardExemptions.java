package com.hedera.services.fees;

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;

import static com.hedera.services.security.ops.SystemOpAuthorization.AUTHORIZED;

public class StandardExemptions implements FeeExemptions {
	private final AccountNumbers accountNums;
	private final SystemOpPolicies systemOpPolicies;

	public StandardExemptions(AccountNumbers accountNums, SystemOpPolicies systemOpPolicies) {
		this.accountNums = accountNums;
		this.systemOpPolicies = systemOpPolicies;
	}

	@Override
	public boolean hasExemptPayer(SignedTxnAccessor accessor) {
		if (isAlwaysExempt(accessor.getPayer().getAccountNum())) {
			return true;
		}
		return systemOpPolicies.check(accessor) == AUTHORIZED;
	}

	@Override
	public boolean isExemptFromRecordFees(AccountID id) {
		return isAlwaysExempt(id.getAccountNum());
	}

	private boolean isAlwaysExempt(long payerAccount) {
		return payerAccount == accountNums.treasury() || payerAccount == accountNums.systemAdmin();
	}
}
