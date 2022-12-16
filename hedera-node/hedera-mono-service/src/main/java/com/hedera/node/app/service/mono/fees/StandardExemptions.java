/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.fees;

import static com.hedera.node.app.service.mono.txns.auth.SystemOpAuthorization.AUTHORIZED;

import com.hedera.node.app.service.mono.txns.auth.SystemOpPolicies;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StandardExemptions implements FeeExemptions {
    private final HederaAccountNumbers accountNums;
    private final SystemOpPolicies systemOpPolicies;

    @Inject
    public StandardExemptions(
            final HederaAccountNumbers accountNums, final SystemOpPolicies systemOpPolicies) {
        this.accountNums = accountNums;
        this.systemOpPolicies = systemOpPolicies;
    }

    @Override
    public boolean hasExemptPayer(TxnAccessor accessor) {
        if (isAlwaysExempt(accessor.getPayer().getAccountNum())) {
            return true;
        }
        return systemOpPolicies.checkAccessor(accessor) == AUTHORIZED;
    }

    private boolean isAlwaysExempt(long payerAccount) {
        return payerAccount == accountNums.treasury() || payerAccount == accountNums.systemAdmin();
    }
}
