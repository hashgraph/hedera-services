/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.evm.contracts.execution;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.log.Log;

/**
 * Model object holding all the necessary data to build and externalise the result of a single EVM
 * transaction
 */
public class HederaEvmTransactionProcessingResult {
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

    public static HederaEvmTransactionProcessingResult failed(
            final long gasUsed,
            final long sbhRefund,
            final long gasPrice,
            final Optional<Bytes> revertReason,
            final Optional<ExceptionalHaltReason> haltReason) {
        return new HederaEvmTransactionProcessingResult(
                Status.FAILED,
                Collections.emptyList(),
                gasUsed,
                sbhRefund,
                gasPrice,
                Bytes.EMPTY,
                Optional.empty(),
                revertReason,
                haltReason);
    }

    public static HederaEvmTransactionProcessingResult successful(
            final List<Log> logs,
            final long gasUsed,
            final long sbhRefund,
            final long gasPrice,
            final Bytes output,
            final Address recipient) {
        return new HederaEvmTransactionProcessingResult(
                Status.SUCCESSFUL,
                logs,
                gasUsed,
                sbhRefund,
                gasPrice,
                output,
                Optional.of(recipient),
                Optional.empty(),
                Optional.empty());
    }

    protected HederaEvmTransactionProcessingResult(
            final Status status,
            final List<Log> logs,
            final long gasUsed,
            final long sbhRefund,
            final long gasPrice,
            final Bytes output,
            final Optional<Address> recipient,
            final Optional<Bytes> revertReason,
            final Optional<ExceptionalHaltReason> haltReason) {
        this.logs = logs;
        this.output = output;
        this.status = status;
        this.gasUsed = gasUsed;
        this.sbhRefund = sbhRefund;
        this.gasPrice = gasPrice;
        this.recipient = recipient;
        this.haltReason = haltReason;
        this.revertReason = revertReason;
    }

    /**
     * Returns whether or not the transaction was successfully processed.
     *
     * @return {@code true} if the transaction was successfully processed; otherwise {@code false}
     */
    public boolean isSuccessful() {
        return status == Status.SUCCESSFUL;
    }

    public long getGasPrice() {
        return gasPrice;
    }

    public long getGasUsed() {
        return gasUsed;
    }

    public long getSbhRefund() {
        return sbhRefund;
    }

    public Bytes getOutput() {
        return output;
    }

    public List<Log> getLogs() {
        return logs;
    }

    public Optional<Address> getRecipient() {
        return recipient;
    }

    /**
     * Returns the exceptional halt reason
     *
     * @return the halt reason
     */
    public Optional<ExceptionalHaltReason> getHaltReason() {
        return haltReason;
    }

    public Optional<Bytes> getRevertReason() {
        return revertReason;
    }
}
