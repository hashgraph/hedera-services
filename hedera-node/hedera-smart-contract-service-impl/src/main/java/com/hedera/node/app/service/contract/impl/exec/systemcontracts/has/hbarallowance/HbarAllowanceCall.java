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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hbarallowance;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasOnly;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class HbarAllowanceCall extends AbstractCall {

    private final AccountID owner;
    private final AccountID spender;

    public HbarAllowanceCall(
            @NonNull final HasCallAttempt attempt, @Nullable final AccountID owner, @NonNull final AccountID spender) {
        super(attempt.systemContractGasCalculator(), attempt.enhancement(), true);
        this.spender = requireNonNull(spender);
        this.owner = owner;
    }

    @Override
    public boolean allowsStaticFrame() {
        return true;
    }

    @NonNull
    @Override
    public PricedResult execute(final MessageFrame frame) {
        requireNonNull(frame);
        if (owner == null || nativeOperations().getAccount(owner) == null) {
            return reversionWith(
                    INVALID_ALLOWANCE_OWNER_ID, gasCalculator.canonicalGasRequirement(DispatchType.APPROVE));
        }
        final var gasRequirement = gasCalculator.viewGasRequirement();

        final var allowance = getAllowance(nativeOperations().getAccount(owner), spender);
        return gasOnly(successResult(encodedAllowanceOutput(allowance), gasRequirement), SUCCESS, false);
    }

    @NonNull
    private BigInteger getAllowance(@NonNull final Account ownerAccount, @NonNull final AccountID spenderID) {
        requireNonNull(ownerAccount);
        requireNonNull(spenderID);
        final var tokenAllowance = requireNonNull(ownerAccount).cryptoAllowances().stream()
                .filter(allowance -> allowance.spenderIdOrThrow().equals(spenderID))
                .findFirst();
        return BigInteger.valueOf(
                tokenAllowance.map(AccountCryptoAllowance::amount).orElse(0L));
    }

    @NonNull
    private ByteBuffer encodedAllowanceOutput(@NonNull final BigInteger allowance) {
        requireNonNull(allowance);
        return HbarAllowanceTranslator.HBAR_ALLOWANCE_PROXY
                .getOutputs()
                .encodeElements((long) SUCCESS.protoOrdinal(), allowance);
    }
}
