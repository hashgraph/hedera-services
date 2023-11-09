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

/**
 * A {@link VerificationStrategy} that verifies signatures from a single active contract. This is the
 * verification strategy used within the EVM to check receiver signature requirements.
 */
public class ActiveContractVerificationStrategy implements VerificationStrategy {
    private final long activeNumber;
    private final Bytes activeAddress;
    private final boolean requiresDelegatePermission;
    private final UseTopLevelSigs useTopLevelSigs;

    public enum UseTopLevelSigs {
        YES,
        NO
    }

    public ActiveContractVerificationStrategy(
            final long activeNumber,
            @NonNull final Bytes activeAddress,
            final boolean requiresDelegatePermission,
            @NonNull final UseTopLevelSigs useTopLevelSigs) {
        this.activeNumber = activeNumber;
        this.useTopLevelSigs = Objects.requireNonNull(useTopLevelSigs);
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
            return useTopLevelSigs == UseTopLevelSigs.YES
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
        if (authorizedId.hasContractNum() && authorizedId.contractNumOrThrow() == activeNumber) {
            return Decision.VALID;
        } else if (authorizedId.hasEvmAddress() && activeAddress.equals(authorizedId.evmAddress())) {
            return Decision.VALID;
        } else {
            return Decision.INVALID;
        }
    }
}
