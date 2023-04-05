/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.contracts.execution;

import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.mono.contracts.execution.traceability.SolidityAction;
import com.hedera.node.app.service.mono.state.submerkle.EvmFnResult;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
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

/**
 * Model object holding all the necessary data to build and externalise the result of a single EVM
 * transaction
 */
public class TransactionProcessingResult extends HederaEvmTransactionProcessingResult {
    private Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges;
    private List<SolidityAction> actions;

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
        super(status, logs, gasUsed, sbhRefund, gasPrice, output, recipient, revertReason, haltReason);
        this.stateChanges = stateChanges;
        this.actions = actions;
    }

    /**
     * Adds a list of created contracts to be externalised as part of the {@link ExpirableTxnRecord}
     *
     * @param createdContracts the list of contractIDs created
     */
    public void setCreatedContracts(List<ContractID> createdContracts) {
        this.createdContracts = createdContracts;
    }

    public List<ContractID> getCreatedContracts() {
        return createdContracts;
    }

    public Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> getStateChanges() {
        return stateChanges;
    }

    public void setStateChanges(final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges) {
        this.stateChanges = stateChanges;
    }

    public List<SolidityAction> getActions() {
        return actions;
    }

    public void setActions(final List<SolidityAction> actions) {
        this.actions = actions;
    }

    /**
     * Converts the {@link TransactionProcessingResult} into {@link ContractFunctionResult} gRPC
     * model.
     *
     * @return the {@link ContractFunctionResult} model to externalise
     */
    public ContractFunctionResult toGrpc() {
        return EvmFnResult.fromCall(this).toGrpc();
    }
}
