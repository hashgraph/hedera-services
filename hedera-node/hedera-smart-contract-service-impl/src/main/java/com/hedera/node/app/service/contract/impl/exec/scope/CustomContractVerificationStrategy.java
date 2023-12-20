/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.scope;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

public class CustomContractVerificationStrategy implements VerificationStrategy {
    private final long activeNumber;
    private final long contractNumber;
    private final Bytes activeAddress;
    private final boolean requiresDelegatePermission;
    private final ActiveContractVerificationStrategy.UseTopLevelSigs useTopLevelSigs;

    public enum UseTopLevelSigs {
        YES,
        NO
    }

    public CustomContractVerificationStrategy(
            final long activeNumber,
            final long contractNumber,
            @NonNull final Bytes activeAddress,
            final boolean requiresDelegatePermission,
            @NonNull final ActiveContractVerificationStrategy.UseTopLevelSigs useTopLevelSigs) {
        this.activeNumber = activeNumber;
        this.useTopLevelSigs = Objects.requireNonNull(useTopLevelSigs);
        this.contractNumber = contractNumber;
        this.activeAddress = Objects.requireNonNull(activeAddress);
        this.requiresDelegatePermission = requiresDelegatePermission;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Decision decideForPrimitive(@NonNull final Key key) {
        final var keyKind = key.key().kind();
        if (keyKind == Key.KeyOneOfType.CONTRACT_ID) {
            if (requiresDelegatePermission) {
                return Decision.INVALID;
            } else {
                return decisionFor(key.contractIDOrThrow());
            }
        } else if (keyKind == Key.KeyOneOfType.DELEGATABLE_CONTRACT_ID) {
            return decisionFor(key.delegatableContractIdOrThrow());
        } else {
            return useTopLevelSigs == ActiveContractVerificationStrategy.UseTopLevelSigs.YES
                    ? Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION
                    : Decision.INVALID;
        }
    }

    public long getActiveNumber() {
        return activeNumber;
    }

    public Bytes getActiveAddress() {
        return activeAddress;
    }

    public boolean requiresDelegatePermission() {
        return requiresDelegatePermission;
    }

    private Decision decisionFor(@NonNull final ContractID authorizedId) {
        if (authorizedId.hasContractNum()
                && authorizedId.contractNumOrThrow() == activeNumber
                && authorizedId.contractNumOrThrow() == contractNumber) {
            return Decision.VALID;
        } else if (authorizedId.hasEvmAddress() && activeAddress.equals(authorizedId.evmAddress())) {
            return Decision.VALID;
        } else {
            return Decision.INVALID;
        }
    }
}
