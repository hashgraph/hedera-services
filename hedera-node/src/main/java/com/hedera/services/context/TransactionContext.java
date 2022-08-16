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
package com.hedera.services.context;

import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.expiry.ExpiringEntity;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.services.utils.accessors.SwirldsTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Defines a type that manages transaction-specific context for a node. (That is, context built
 * while processing a consensus transaction.) Most of this context is ultimately captured by a
 * {@link ExpirableTxnRecord}, so the core responsibility of this type is to construct an
 * appropriate record in method {@code recordSoFar}.
 */
public interface TransactionContext {
    /**
     * Clear the context and processing history, initialize for a new consensus txn.
     *
     * @param accessor the consensus platform txn to manage context of.
     * @param consensusTime when the txn reached consensus.
     * @param submittingMember the member that submitted the txn to the network.
     */
    void resetFor(TxnAccessor accessor, Instant consensusTime, long submittingMember);

    /**
     * Checks if the payer is known to have an active signature (that is, whether the txn includes
     * enough valid cryptographic signatures to activate the payer's Hedera key).
     *
     * @return whether the payer sig is known active
     */
    boolean isPayerSigKnownActive();

    /**
     * The Hedera account id of the node that submitted the current txn.
     *
     * @return the account id
     */
    AccountID submittingNodeAccount();

    /**
     * The member id of the node that submitted the current txn.
     *
     * @return the member id
     */
    long submittingSwirldsMember();

    /**
     * Returns the Hedera account id paying for the current txn
     *
     * @return the ad
     * @throws IllegalStateException if there is no active txn
     */
    AccountID activePayer();

    default AccountID effectivePayer() {
        return isPayerSigKnownActive() ? activePayer() : submittingNodeAccount();
    }

    /**
     * If there is an active payer signature, returns the Hedera key used to sign.
     *
     * @return the Hedera key used for the active payer sig
     */
    JKey activePayerKey();

    /**
     * Gets the consensus time of the txn being processed.
     *
     * @return the instant of consensus for the current txn.
     */
    Instant consensusTime();

    /**
     * Gets the current status of the txn being processed. In general, the initial value should be
     * {@code UNKNOWN}, and any final status other than {@code SUCCESS} should indicate an invalid
     * transaction.
     *
     * @return the status of processing the current txn thus far.
     */
    ResponseCodeEnum status();

    /**
     * Constructs and gets a {@link ExpirableTxnRecord.Builder} which captures the history of
     * processing the current txn up to the time of the call.
     *
     * @return the historical record of processing the current txn thus far.
     */
    ExpirableTxnRecord.Builder recordSoFar();

    /**
     * Gets an accessor to the defined type {@link SignedTxnAccessor} currently being processed.
     *
     * @return accessor for the current txn.
     */
    TxnAccessor accessor();

    /**
     * Gets a platform transaction accessor to the defined type {@link SwirldsTxnAccessor} currently
     * being processed.
     *
     * @return accessor for the current txn.
     */
    SwirldsTxnAccessor swirldsTxnAccessor();

    /**
     * Gets the list of sidecars that the current top-level txn produced.
     *
     * @return list of all the sidecar record for the current txn.
     */
    List<TransactionSidecarRecord.Builder> sidecars();

    /**
     * Set a new status for the current txn's processing.
     *
     * @param status the new status of processing the current txn.
     */
    void setStatus(ResponseCodeEnum status);

    /**
     * Record that the current transaction created a file.
     *
     * @param id the created file.
     */
    void setCreated(FileID id);

    /**
     * Record that the current transaction created a crypto account.
     *
     * @param id the created account.
     */
    void setCreated(AccountID id);

    /**
     * Record that the current transaction targeted a smart contract.
     *
     * @param id the targeted contract.
     */
    void setTargetedContract(ContractID id);

    /**
     * Record that the current transaction created a consensus topic.
     *
     * @param id the created topic.
     */
    void setCreated(TopicID id);

    /**
     * Record that the current transaction created a scheduled transaction.
     *
     * @param id the created scheduled transaction
     */
    void setCreated(ScheduleID id);

    /**
     * Record that the current transaction references a particular scheduled transaction.
     *
     * @param txnId the id of the referenced scheduled transaction
     */
    void setScheduledTxnId(TransactionID txnId);

    /**
     * Record that the current transaction called a smart contract with a specified result.
     *
     * @param result the result of the contract call
     */
    void setCallResult(EvmFnResult result);

    /**
     * Record that the current transaction produced sidecar records which will be externalized in
     * sidecar files in the record stream.
     *
     * @param sidecar a single sidecar record associated with the current top-level txn
     */
    void addSidecarRecord(TransactionSidecarRecord.Builder sidecar);

    /**
     * Add call context information to an already set call result
     *
     * @param callContext the context to add to the call result
     */
    void updateForEvmCall(EthTxData callContext, EntityId senderId);

    /**
     * Record that the current transaction created a smart contract with a specified result.
     *
     * @param result the result of the contract creation.
     */
    void setCreateResult(EvmFnResult result);

    /**
     * Record that an additional fee was deducted from the payer of the current txn.
     *
     * @param amount the extra amount deducted from the current txn's payer.
     */
    void addFeeChargedToPayer(long amount);

    /**
     * Record that the payer of the current txn is known to have an active signature (that is, the
     * txn includes enough valid cryptographic signatures to activate the payer's Hedera key).
     */
    void payerSigIsKnownActive();

    /**
     * Update the topic's running hash and sequence number.
     *
     * @param runningHash the running hash of the topic.
     * @param sequenceNumber the sequence number of the topic.
     */
    void setTopicRunningHash(byte[] runningHash, long sequenceNumber);

    /**
     * Sets a triggered TxnAccessor for execution
     *
     * @param accessor the accessor which will be triggered
     */
    void trigger(TxnAccessor accessor);

    /**
     * Returns a triggered TxnAccessor
     *
     * @return a triggered TxnAccessor
     */
    TxnAccessor triggeredTxn();

    /**
     * Adds a collection of {@link ExpiringEntity} to be later tracked for purging when expired
     *
     * @param expiringEntities the information about entities which will be tracked for future purge
     */
    void addExpiringEntities(Collection<ExpiringEntity> expiringEntities);

    /**
     * Gets all expiring entities to the defined type {@link ExpiringEntity} currently being
     * processed.
     *
     * @return {@code List<ExpiringEntity>} for the current expiring entities.
     */
    List<ExpiringEntity> expiringEntities();

    /**
     * Set the assessed custom fees as a result of the active transaction. It is used for {@link
     * ExpirableTxnRecord}.
     *
     * @param assessedCustomFees the assessed custom fees
     */
    void setAssessedCustomFees(List<FcAssessedCustomFee> assessedCustomFees);

    /**
     * Verifies if the transaction context for the currently executing transaction has a contract
     * result
     *
     * @return true if there is a ContractCall or ContractCreate result attached to the context
     */
    boolean hasContractResult();

    /**
     * Extracts the amount of gas used by the currently executing transaction.
     *
     * @return long - the amount of gas used for the TX execution
     */
    long getGasUsedForContractTxn();

    /**
     * Records the beneficiary of an account (or contract) deleted in the current transaction.
     *
     * @param accountNum the number of a deleted account
     * @param beneficiaryNum the number of its beneficiary
     */
    void recordBeneficiaryOfDeleted(long accountNum, long beneficiaryNum);

    /**
     * Given the number of an account (or contract) deleted in the current transaction, returns the
     * number of its designated beneficiary. Used by the {@link
     * com.hedera.services.ledger.interceptors.StakingAccountsCommitInterceptor} to redirect reward
     * payments that would have otherwise been received by deleted accounts.
     *
     * @param accountNum the number of a deleted account
     * @return the number of its beneficiary
     * @throws IllegalArgumentException if the given account number has no recorded beneficiary
     */
    long getBeneficiaryOfDeleted(long accountNum);

    /**
     * Gives the number of accounts (or contracts) deleted in the current transaction.
     *
     * @return the number of deleted entities
     */
    int numDeletedAccountsAndContracts();
}
