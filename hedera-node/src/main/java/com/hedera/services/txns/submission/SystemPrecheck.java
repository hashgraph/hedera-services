package com.hedera.services.txns.submission;

import com.hedera.services.context.domain.security.HapiOpPermissions;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.throttling.TransactionThrottling;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.function.LongPredicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Tests if the network can be expected to handle the given {@code TransactionBody} if it
 * does reach consensus---that is, if the requested HAPI function is enabled on the network,
 * the payer has the required privileges to use it, and its throttle bucket(s) have capacity.
 *
 * For more details, please see https://github.com/hashgraph/hedera-services/blob/master/docs/transaction-prechecks.md
 */
public class SystemPrecheck {
	private static final LongPredicate IS_THROTTLE_EXEMPT = num -> num >= 1 && num <= 100L;

	private final SystemOpPolicies systemOpPolicies;
	private final HapiOpPermissions hapiOpPermissions;
	private final TransactionThrottling txnThrottling;

	public SystemPrecheck(
			SystemOpPolicies systemOpPolicies,
			HapiOpPermissions hapiOpPermissions,
			TransactionThrottling txnThrottling
	) {
		this.txnThrottling = txnThrottling;
		this.systemOpPolicies = systemOpPolicies;
		this.hapiOpPermissions = hapiOpPermissions;
	}

	ResponseCodeEnum screen(SignedTxnAccessor accessor) {
		final var payer = accessor.getPayer();

		final var permissionStatus = hapiOpPermissions.permissibilityOf(accessor.getFunction(), payer);
		if (permissionStatus != OK) {
			return permissionStatus;
		}

		final var privilegeStatus = systemOpPolicies.check(accessor).asStatus();
		if (privilegeStatus != OK) {
			return privilegeStatus;
		}

		if (IS_THROTTLE_EXEMPT.test(payer.getAccountNum())) {
			return OK;
		}

		return txnThrottling.shouldThrottle(accessor.getTxn()) ? BUSY : OK;
	}
}
