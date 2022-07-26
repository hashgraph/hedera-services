/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.txns.submission;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetAccountDetails;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NetworkGetExecutionTime;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.context.domain.security.HapiOpPermissions;
import com.hedera.services.throttling.TransactionThrottling;
import com.hedera.services.txns.auth.SystemOpPolicies;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Tests if the network can be expected to handle the given {@code TransactionBody} if it does reach
 * consensus---that is, if the requested HAPI function is enabled on the network, the payer has the
 * required privileges to use it, and its throttle bucket(s) have capacity.
 *
 * <p>For more details, please see
 * https://github.com/hashgraph/hedera-services/blob/master/docs/transaction-prechecks.md
 */
@Singleton
public class SystemPrecheck {
    public static final List<HederaFunctionality> RESTRICTED_FUNCTIONALITIES =
            List.of(NetworkGetExecutionTime, GetAccountDetails);

    private final SystemOpPolicies systemOpPolicies;
    private final HapiOpPermissions hapiOpPermissions;
    private final TransactionThrottling txnThrottling;

    @Inject
    public SystemPrecheck(
            SystemOpPolicies systemOpPolicies,
            HapiOpPermissions hapiOpPermissions,
            TransactionThrottling txnThrottling) {
        this.txnThrottling = txnThrottling;
        this.systemOpPolicies = systemOpPolicies;
        this.hapiOpPermissions = hapiOpPermissions;
    }

    ResponseCodeEnum screen(SignedTxnAccessor accessor) {
        final var payer = accessor.getPayer();

        final var permissionStatus =
                hapiOpPermissions.permissibilityOf(accessor.getFunction(), payer);
        if (permissionStatus != OK) {
            return permissionStatus;
        }

        final var privilegeStatus = systemOpPolicies.checkAccessor(accessor).asStatus();
        if (privilegeStatus != OK) {
            return privilegeStatus;
        }

        return txnThrottling.shouldThrottle(accessor) ? BUSY : OK;
    }
}
