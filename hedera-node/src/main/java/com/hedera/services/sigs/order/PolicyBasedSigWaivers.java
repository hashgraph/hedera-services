package com.hedera.services.sigs.order;

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.config.EntityNumbers;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.security.ops.SystemOpAuthorization.AUTHORIZED;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;

/**
 * Implementation of {@link SignatureWaivers} that waives signatures based on the
 * {@link com.hedera.services.security.ops.SystemOpAuthorization} status of the
 * transaction to which they apply.
 *
 * That is, it waives a signature if and only if the transaction is {@code AUTHORIZED}
 * by the injected {@link SystemOpPolicies}.
 *
 * There is one exception. Even though the treasury account {@code 0.0.2} <b>is</b>
 * authorized to update itself with a new key, the standard waiver does not apply
 * here, and a new key must sign; https://github.com/hashgraph/hedera-services/issues/1890
 * has details.
 */
public class PolicyBasedSigWaivers implements SignatureWaivers {
	private final AccountNumbers accountNums;
	private final SystemOpPolicies opPolicies;

	public PolicyBasedSigWaivers(EntityNumbers entityNums, SystemOpPolicies opPolicies) {
		this.opPolicies = opPolicies;

		this.accountNums = entityNums.accounts();
	}

	@Override
	public boolean isAppendFileWaclWaived(TransactionBody fileAppendTxn) {
		return opPolicies.check(fileAppendTxn, FileAppend) == AUTHORIZED;
	}

	@Override
	public boolean isTargetFileWaclWaived(TransactionBody fileUpdateTxn) {
		return opPolicies.check(fileUpdateTxn, FileUpdate) == AUTHORIZED;
	}

	@Override
	public boolean isNewFileWaclWaived(TransactionBody fileUpdateTxn) {
		return opPolicies.check(fileUpdateTxn, FileUpdate) == AUTHORIZED;
	}

	@Override
	public boolean isTargetAccountKeyWaived(TransactionBody cryptoUpdateTxn) {
		return opPolicies.check(cryptoUpdateTxn, CryptoUpdate) == AUTHORIZED;
	}

	@Override
	public boolean isNewAccountKeyWaived(TransactionBody cryptoUpdateTxn) {
		final var isAuthorized = opPolicies.check(cryptoUpdateTxn, CryptoUpdate) == AUTHORIZED;
		if (!isAuthorized) {
			return false;
		} else {
			final var targetNum = cryptoUpdateTxn.getCryptoUpdateAccount().getAccountIDToUpdate().getAccountNum();
			return targetNum != accountNums.treasury();
		}
	}
}
