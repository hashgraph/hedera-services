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

package com.hedera.services.bdd.spec.utilops.streams.assertions;

import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.List;

public class ValidContractIdsAssertion implements RecordStreamAssertion {
    @Override
    public boolean isApplicableToSidecar(TransactionSidecarRecord sidecar) {
        return true;
    }

    @Override
    public boolean testSidecar(TransactionSidecarRecord sidecar) throws AssertionError {
        System.out.println("Validating contract ids in sidecar " + sidecar);
        switch (sidecar.getSidecarRecordsCase()) {
            case STATE_CHANGES -> validateStateChangeIds(
                    sidecar.getStateChanges().getContractStateChangesList());
            case ACTIONS -> validateActionIds(sidecar.getActions().getContractActionsList());
            case BYTECODE -> validateBytecodeIds(sidecar.getBytecode());
            case SIDECARRECORDS_NOT_SET -> {
                // No-op
            }
        }
        return true;
    }

    private void validateStateChangeIds(List<ContractStateChange> stateChanges) {
        for (final var change : stateChanges) {
            if (change.hasContractId()) {
                assertValid(change.getContractId());
            }
        }
    }

    private void validateActionIds(List<ContractAction> actions) {
        for (final var action : actions) {
            System.out.println("VALIDATING " + action);
            if (action.hasCallingAccount()) {
                assertValid(action.getCallingAccount());
            } else if (action.hasCallingContract()) {
                assertValid(action.getCallingContract());
            }

            if (action.hasRecipientAccount()) {
                assertValid(action.getRecipientAccount());
            } else if (action.hasRecipientContract()) {
                assertValid(action.getRecipientContract());
            }
        }
    }

    private void validateBytecodeIds(ContractBytecode bytecode) {
        assertValid(bytecode.getContractId());
    }

    private void assertValid(ContractID id) {
        final var isValid = isValid(id.getShardNum(), id.getRealmNum(), id.getContractNum());
        if (!isValid) {
            throw new AssertionError("Contract id "
                    + String.format("%d.%d.%d", id.getShardNum(), id.getRealmNum(), id.getContractNum())
                    + " is not valid");
        }
    }

    private void assertValid(AccountID id) {
        final var isValid = isValid(id.getShardNum(), id.getRealmNum(), id.getAccountNum());
        if (!isValid) {
            throw new AssertionError("Account id "
                    + String.format("%d.%d.%d", id.getShardNum(), id.getRealmNum(), id.getAccountNum())
                    + " is not valid");
        }
    }

    private boolean isValid(long shard, long realm, long num) {
        return shard == 0L && realm == 0L && num >= 1;
    }
}
