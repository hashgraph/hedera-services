package com.hedera.services.context;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.expiry.ExpiringEntity;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.AssessedCustomFee;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.SolidityFnResult;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

/**
 * Implements a transaction context using infrastructure known to be
 * available in the node context. This is the most convenient implementation,
 * since such infrastructure will often depend on an instance of
 * {@link TransactionContext}; and we risk circular dependencies if we
 * inject the infrastructure as dependencies here.
 *
 * @author Michael Tinker
 * @author Neeharika Sompalli
 */
public class AwareTransactionContext implements TransactionContext {
	private static final Logger log = LogManager.getLogger(AwareTransactionContext.class);

	static final JKey EMPTY_KEY;

	static {
		EMPTY_KEY = asFcKeyUnchecked(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build());
	}

	private final ServicesContext ctx;
	private TxnAccessor triggeredTxn = null;

	private static final Consumer<TxnReceipt.Builder> noopReceiptConfig = ignore -> { };
	private static final Consumer<ExpirableTxnRecord.Builder> noopRecordConfig = ignore -> { };

	private long submittingMember;
	private long otherNonThresholdFees;
	private byte[] hash;
	private boolean isPayerSigKnownActive;
	private Instant consensusTime;
	private TxnAccessor accessor;
	private ResponseCodeEnum statusSoFar;
	private List<ExpiringEntity> expiringEntities = new ArrayList<>();
	private Consumer<TxnReceipt.Builder> receiptConfig = noopReceiptConfig;
	private Consumer<ExpirableTxnRecord.Builder> recordConfig = noopRecordConfig;
	private List<TokenTransferList> explicitTokenTransfers;
	private List<AssessedCustomFee> assessedCustomFees;

	boolean hasComputedRecordSoFar;
	ExpirableTxnRecord.Builder recordSoFar = ExpirableTxnRecord.newBuilder();

	AwareTransactionContext(ServicesContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public void resetFor(TxnAccessor accessor, Instant consensusTime, long submittingMember) {
		this.accessor = accessor;
		this.consensusTime = consensusTime;
		this.submittingMember = submittingMember;
		this.triggeredTxn = null;
		this.expiringEntities.clear();

		otherNonThresholdFees = 0L;
		hash = accessor.getHash();
		statusSoFar = UNKNOWN;
		recordConfig = noopRecordConfig;
		receiptConfig = noopReceiptConfig;
		isPayerSigKnownActive = false;
		hasComputedRecordSoFar = false;
		explicitTokenTransfers = null;
		assessedCustomFees = null;

		ctx.narratedCharging().resetForTxn(accessor, submittingMember);

		recordSoFar.clear();
	}

	@Override
	public void setTokenTransferLists(List<TokenTransferList> tokenTransfers) {
		explicitTokenTransfers = tokenTransfers;
	}

	@Override
	public void setAssessedCustomFees(List<AssessedCustomFee> assessedCustomFees){
		this.assessedCustomFees = assessedCustomFees;
	}

	@Override
	public JKey activePayerKey() {
		return isPayerSigKnownActive
				? ctx.accounts().get(fromAccountId(accessor.getPayer())).getKey()
				: EMPTY_KEY;
	}

	@Override
	public AccountID activePayer() {
		if (!isPayerSigKnownActive) {
			throw new IllegalStateException("No active payer!");
		}
		return accessor().getPayer();
	}

	@Override
	public AccountID submittingNodeAccount() {
		try {
			return ctx.nodeInfo().accountOf(submittingMember);
		} catch (IllegalArgumentException e) {
			log.warn("No available Hedera account for member {}!", submittingMember, e);
			throw new IllegalStateException(String.format("Member %d must have a Hedera account!", submittingMember));
		}
	}

	@Override
	public long submittingSwirldsMember() {
		return submittingMember;
	}

	@Override
	public ExpirableTxnRecord recordSoFar() {
		final var receipt = receiptSoFar().build();

		recordSoFar = ctx.creator().buildExpiringRecord(
				otherNonThresholdFees,
				hash,
				accessor,
				consensusTime,
				receipt,
				explicitTokenTransfers,
				ctx,
				assessedCustomFees);

		recordConfig.accept(recordSoFar);
		hasComputedRecordSoFar = true;
		return recordSoFar.build();
	}

	TxnReceipt.Builder receiptSoFar() {
		final var receipt = TxnReceipt.newBuilder()
				.setExchangeRates(ctx.exchange().fcActiveRates())
				.setStatus(statusSoFar.name());
		receiptConfig.accept(receipt);
		return receipt;
	}

	@Override
	public Instant consensusTime() {
		return consensusTime;
	}

	@Override
	public ResponseCodeEnum status() {
		return statusSoFar;
	}

	@Override
	public TxnAccessor accessor() {
		return accessor;
	}

	@Override
	public void setStatus(ResponseCodeEnum status) {
		statusSoFar = status;
	}

	@Override
	public void setCreated(AccountID id) {
		receiptConfig = receipt -> receipt.setAccountId(EntityId.fromGrpcAccountId(id));
	}

	@Override
	public void setCreated(TokenID id) {
		receiptConfig = receipt -> receipt.setTokenId(EntityId.fromGrpcTokenId(id));
	}

	@Override
	public void setCreated(ScheduleID id) {
		receiptConfig = receipt -> receipt.setScheduleId(EntityId.fromGrpcScheduleId(id));
	}

	@Override
	public void setScheduledTxnId(TransactionID txnId) {
		receiptConfig = receiptConfig.andThen(receipt -> receipt.setScheduledTxnId(TxnId.fromGrpc(txnId)));
	}

	@Override
	public void setNewTotalSupply(long newTotalTokenSupply) {
		receiptConfig = receipt -> receipt.setNewTotalSupply(newTotalTokenSupply);
	}

	@Override
	public void setCreated(FileID id) {
		receiptConfig = receipt -> receipt.setFileId(EntityId.fromGrpcFileId(id));
	}

	@Override
	public void setCreated(ContractID id) {
		receiptConfig = receipt -> receipt.setContractId(EntityId.fromGrpcContractId(id));
	}

	@Override
	public void setCreated(TopicID id) {
		receiptConfig = receipt -> receipt.setTopicId(EntityId.fromGrpcTopicId(id));
	}

	@Override
	public void setTopicRunningHash(byte[] topicRunningHash, long sequenceNumber) {
		receiptConfig = receipt -> receipt
				.setTopicRunningHash(topicRunningHash)
				.setTopicSequenceNumber(sequenceNumber)
				.setRunningHashVersion(MerkleTopic.RUNNING_HASH_VERSION);
	}

	@Override
	public void addNonThresholdFeeChargedToPayer(long amount) {
		otherNonThresholdFees += amount;
	}

	public long getNonThresholdFeeChargedToPayer() {
		return otherNonThresholdFees;
	}

	@Override
	public void setCallResult(ContractFunctionResult result) {
		recordConfig = expiringRecord -> expiringRecord.setContractCallResult(SolidityFnResult.fromGrpc(result));
	}

	@Override
	public void setCreateResult(ContractFunctionResult result) {
		recordConfig = expiringRecord -> expiringRecord.setContractCreateResult(SolidityFnResult.fromGrpc(result));
	}

	@Override
	public boolean isPayerSigKnownActive() {
		return isPayerSigKnownActive;
	}

	@Override
	public void payerSigIsKnownActive() {
		isPayerSigKnownActive = true;
	}

	@Override
	public void trigger(TxnAccessor scopedAccessor) {
		if (accessor().isTriggeredTxn()) {
			throw new IllegalStateException("Unable to trigger txns in triggered txns");
		}
		triggeredTxn = scopedAccessor;
	}

	@Override
	public TxnAccessor triggeredTxn() {
		return triggeredTxn;
	}

	@Override
	public void addExpiringEntities(Collection<ExpiringEntity> entities) {
		expiringEntities.addAll(entities);
	}

	@Override
	public List<ExpiringEntity> expiringEntities() {
		return expiringEntities;
	}

	List<AssessedCustomFee> getAssessedCustomFees() {
		return assessedCustomFees;
	}
}
