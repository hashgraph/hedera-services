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
package com.hedera.services.sigs.order;

import static com.hedera.services.txns.auth.SystemOpAuthorization.AUTHORIZED;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.config.EntityNumbers;
import com.hedera.services.txns.auth.SystemOpAuthorization;
import com.hedera.services.txns.auth.SystemOpPolicies;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of {@link SignatureWaivers} that waives signatures based on the {@link
 * SystemOpAuthorization} status of the transaction to which they apply.
 *
 * <p>That is, it waives a signature if and only if the transaction is {@code AUTHORIZED} by the
 * injected {@link SystemOpPolicies}.
 *
 * <p>There is one exception. Even though the treasury account {@code 0.0.2} <b>is</b> authorized to
 * update itself with a new key, the standard waiver does not apply here, and a new key must sign;
 * https://github.com/hashgraph/hedera-services/issues/1890 has details.
 */
@Singleton
public class PolicyBasedSigWaivers implements SignatureWaivers {
    private final AccountNumbers accountNums;
    private final SystemOpPolicies opPolicies;

    @Inject
    public PolicyBasedSigWaivers(EntityNumbers entityNums, SystemOpPolicies opPolicies) {
        this.opPolicies = opPolicies;

        this.accountNums = entityNums.accounts();
    }

    @Override
    public boolean isAppendFileWaclWaived(TransactionBody fileAppendTxn, final AccountID payer) {
        assertTypeExpectation(fileAppendTxn.hasFileAppend());
        return opPolicies.checkKnownTxn(fileAppendTxn, FileAppend, payer) == AUTHORIZED;
    }

    @Override
    public boolean isTargetFileWaclWaived(TransactionBody fileUpdateTxn, final AccountID payer) {
        assertTypeExpectation(fileUpdateTxn.hasFileUpdate());
        return opPolicies.checkKnownTxn(fileUpdateTxn, FileUpdate, payer) == AUTHORIZED;
    }

    @Override
    public boolean isNewFileWaclWaived(TransactionBody fileUpdateTxn, final AccountID payer) {
        return isTargetFileWaclWaived(fileUpdateTxn, payer);
    }

    @Override
    public boolean isTargetAccountKeyWaived(
            TransactionBody cryptoUpdateTxn, final AccountID payer) {
        assertTypeExpectation(cryptoUpdateTxn.hasCryptoUpdateAccount());
        return opPolicies.checkKnownTxn(cryptoUpdateTxn, CryptoUpdate, payer) == AUTHORIZED;
    }

    @Override
    public boolean isNewAccountKeyWaived(TransactionBody cryptoUpdateTxn, final AccountID payer) {
        assertTypeExpectation(cryptoUpdateTxn.hasCryptoUpdateAccount());
        final var isAuthorized =
                opPolicies.checkKnownTxn(cryptoUpdateTxn, CryptoUpdate, payer) == AUTHORIZED;
        if (!isAuthorized) {
            return false;
        } else {
            final var targetNum =
                    cryptoUpdateTxn.getCryptoUpdateAccount().getAccountIDToUpdate().getAccountNum();
            return targetNum != accountNums.treasury();
        }
    }

    private void assertTypeExpectation(boolean isExpectedType) {
        if (!isExpectedType) {
            throw new IllegalArgumentException("Given transaction is not of the expected type");
        }
    }
}
