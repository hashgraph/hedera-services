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
package com.hedera.evm.execution;

import com.hedera.evm.execution.traceability.SolidityAction;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.log.Log;

public class TransactionProcessingResult {
    /** The status of the transaction after being processed. */
    public enum Status {
        /** The transaction was successfully processed. */
        SUCCESSFUL,

        /** The transaction failed to be completely processed. */
        FAILED
    }

    private final long gasUsed;
    private final long sbhRefund;
    private final long gasPrice;
    private final Status status;
    private final Bytes output;
    private final List<Log> logs;
    private final Optional<Bytes> revertReason;
    private final Optional<Address> recipient;
    private final Optional<ExceptionalHaltReason> haltReason;
    private final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges;
    private final List<SolidityAction> actions;

    private List<ContractID> createdContracts = Collections.emptyList();

    public static TransactionProcessingResult failed(
            final long gasUsed,
            final long sbhRefund,
            final long gasPrice,
            final Optional<Bytes> revertReason,
            final Optional<ExceptionalHaltReason> haltReason,
            final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges,
            final List<SolidityAction> actions) {
        return new TransactionProcessingResult(
                Status.FAILED,
                Collections.emptyList(),
                gasUsed,
                sbhRefund,
                gasPrice,
                Bytes.EMPTY,
                Optional.empty(),
                revertReason,
                haltReason,
                stateChanges,
                actions);
    }

    public static TransactionProcessingResult successful(
            final List<Log> logs,
            final long gasUsed,
            final long sbhRefund,
            final long gasPrice,
            final Bytes output,
            final Address recipient,
            final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges,
            final List<SolidityAction> actions) {
        return new TransactionProcessingResult(
                Status.SUCCESSFUL,
                logs,
                gasUsed,
                sbhRefund,
                gasPrice,
                output,
                Optional.of(recipient),
                Optional.empty(),
                Optional.empty(),
                stateChanges,
                actions);
    }

    private TransactionProcessingResult(
            final Status status,
            final List<Log> logs,
            final long gasUsed,
            final long sbhRefund,
            final long gasPrice,
            final Bytes output,
            final Optional<Address> recipient,
            final Optional<Bytes> revertReason,
            final Optional<ExceptionalHaltReason> haltReason,
            final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges,
            final List<SolidityAction> actions) {
        this.logs = logs;
        this.output = output;
        this.status = status;
        this.gasUsed = gasUsed;
        this.sbhRefund = sbhRefund;
        this.gasPrice = gasPrice;
        this.recipient = recipient;
        this.haltReason = haltReason;
        this.revertReason = revertReason;
        this.stateChanges = stateChanges;
        this.actions = actions;
    }
}
