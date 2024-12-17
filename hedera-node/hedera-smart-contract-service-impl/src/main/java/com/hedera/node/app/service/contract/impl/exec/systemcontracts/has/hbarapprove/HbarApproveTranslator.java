/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hbarapprove;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates {@code hbarApprove()} calls to the HAS system contract.
 */
@Singleton
public class HbarApproveTranslator extends AbstractCallTranslator<HasCallAttempt> {

    /** Selector for hbarApprove(address,int256) method. */
    public static final Function HBAR_APPROVE_PROXY = new Function("hbarApprove(address,int256)", ReturnTypes.INT_64);

    /** Selector for hbarApprove(address,address,int256) method. */
    public static final Function HBAR_APPROVE = new Function("hbarApprove(address,address,int256)", ReturnTypes.INT_64);

    /**
     * Default constructor for injection.
     */
    @Inject
    public HbarApproveTranslator() {
        // Dagger2
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HasCallAttempt attempt) {
        requireNonNull(attempt);
        return attempt.isSelector(HBAR_APPROVE, HBAR_APPROVE_PROXY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call callFrom(@NonNull final HasCallAttempt attempt) {
        requireNonNull(attempt);

        if (attempt.isSelector(HBAR_APPROVE)) {
            return new HbarApproveCall(attempt, bodyForApprove(attempt));
        } else if (attempt.isSelector(HBAR_APPROVE_PROXY)) {
            return new HbarApproveCall(attempt, bodyForApproveProxy(attempt));
        }
        return null;
    }

    @NonNull
    private TransactionBody bodyForApprove(@NonNull final HasCallAttempt attempt) {
        requireNonNull(attempt);
        final var call = HBAR_APPROVE.decodeCall(attempt.inputBytes());
        var owner = attempt.addressIdConverter().convert(call.get(0));
        var spender = attempt.addressIdConverter().convert(call.get(1));

        return bodyOf(cryptoApproveTransactionBody(owner, spender, call.get(2)));
    }

    @NonNull
    private TransactionBody bodyForApproveProxy(@NonNull final HasCallAttempt attempt) {
        requireNonNull(attempt);
        final var call = HBAR_APPROVE_PROXY.decodeCall(attempt.inputBytes());
        final var owner = attempt.redirectAccount() == null
                ? attempt.senderId()
                : attempt.redirectAccount().accountId();
        final var spender = attempt.addressIdConverter().convert(call.get(0));

        return bodyOf(cryptoApproveTransactionBody(owner, spender, call.get(1)));
    }

    @NonNull
    private CryptoApproveAllowanceTransactionBody cryptoApproveTransactionBody(
            @NonNull final AccountID owner, @NonNull final AccountID operatorId, @NonNull final BigInteger amount) {
        requireNonNull(owner);
        requireNonNull(operatorId);
        requireNonNull(amount);
        return CryptoApproveAllowanceTransactionBody.newBuilder()
                .cryptoAllowances(CryptoAllowance.newBuilder()
                        .owner(owner)
                        .spender(operatorId)
                        .amount(amount.longValueExact())
                        .build())
                .build();
    }

    @NonNull
    private TransactionBody bodyOf(
            @NonNull final CryptoApproveAllowanceTransactionBody approveAllowanceTransactionBody) {
        return TransactionBody.newBuilder()
                .cryptoApproveAllowance(approveAllowanceTransactionBody)
                .build();
    }
}
