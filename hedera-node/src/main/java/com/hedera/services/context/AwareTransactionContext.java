package com.hedera.services.context;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.Address;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromString;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static com.hedera.services.utils.MiscUtils.canonicalDiffRepr;
import static com.hedera.services.utils.MiscUtils.readableTransferList;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

/**
 * Implements a transaction context using infrastructure known to be
 * available in the node context. This is the most convenient implementation,
 * since such infrastructure will often depend on an instance of
 * {@link TransactionContext}; and we risk circular dependencies if we
 * inject the infrastructure as dependencies here.
 *
 * @author Michael Tinker
 */
public class AwareTransactionContext implements TransactionContext {
	private static final Logger log = LogManager.getLogger(AwareTransactionContext.class);

	public static final JKey EMPTY_KEY;

	static {
		EMPTY_KEY = asFcKeyUnchecked(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build());
	}

	private final ServicesContext ctx;
	private TxnAccessor triggeredTxn = null;

	private final Consumer<TransactionRecord.Builder> noopRecordConfig = ignore -> {
	};
	private final Consumer<TransactionReceipt.Builder> noopReceiptConfig = ignore -> {
	};

	private long submittingMember;
	private long otherNonThresholdFees;
	private boolean isPayerSigKnownActive;
	private Instant consensusTime;
	private Timestamp consensusTimestamp;
	private ByteString hash;
	private ResponseCodeEnum statusSoFar;
	private TxnAccessor accessor;
	private Consumer<TransactionRecord.Builder> recordConfig = noopRecordConfig;
	private Consumer<TransactionReceipt.Builder> receiptConfig = noopReceiptConfig;

	boolean hasComputedRecordSoFar;
	TransactionRecord.Builder recordSoFar = TransactionRecord.newBuilder();

	public AwareTransactionContext(ServicesContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public void resetFor(TxnAccessor accessor, Instant consensusTime, long submittingMember) {
		this.accessor = accessor;
		this.consensusTime = consensusTime;
		this.submittingMember = submittingMember;
		this.triggeredTxn = null;

		otherNonThresholdFees = 0L;
		hash = accessor.getHash();
		statusSoFar = UNKNOWN;
		consensusTimestamp = asTimestamp(consensusTime);
		recordConfig = noopRecordConfig;
		receiptConfig = noopReceiptConfig;
		isPayerSigKnownActive = false;
		hasComputedRecordSoFar = false;

		ctx.charging().resetFor(accessor, submittingNodeAccount());
		recordSoFar.clear();
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
			Address member = ctx.addressBook().getAddress(submittingMember);
			String memo = member.getMemo();
			return accountParsedFromString(memo);
		} catch (Exception e) {
			log.warn("No available Hedera account for member {}!", submittingMember, e);
			throw new IllegalStateException(String.format("Member %d must have a Hedera account!", submittingMember));
		}
	}

	@Override
	public long submittingSwirldsMember() {
		return submittingMember;
	}

	@Override
	public TransactionRecord recordSoFar() {
		long amount = ctx.charging().totalFeesChargedToPayer() + otherNonThresholdFees;

		if (log.isDebugEnabled()) {
			logItemized();
		}
		recordSoFar
				.setMemo(accessor.getTxn().getMemo())
				.setReceipt(receiptSoFar())
				.setTransferList(ctx.ledger().netTransfersInTxn())
				.setTransactionID(accessor.getTxnId())
				.setTransactionFee(amount)
				.setTransactionHash(hash)
				.setConsensusTimestamp(consensusTimestamp)
				.addAllTokenTransferLists(ctx.ledger().netTokenTransfersInTxn());
		if (accessor.isTriggeredTxn()) {
			recordSoFar.setScheduleRef(accessor.getScheduleRef());
		}

		recordConfig.accept(recordSoFar);
		hasComputedRecordSoFar = true;

		return recordSoFar.build();
	}

	@Override
	public TransactionRecord updatedRecordGiven(TransferList listWithNewFees) {
		if (!hasComputedRecordSoFar) {
			throw new IllegalStateException(String.format(
					"No record exists to be updated with '%s'!",
					readableTransferList(listWithNewFees)));
		}

		long amount = ctx.charging().totalFeesChargedToPayer() + otherNonThresholdFees;
		recordSoFar.setTransferList(listWithNewFees).setTransactionFee(amount);

		return recordSoFar.build();
	}

	private void logItemized() {
		Transaction signedTxn = accessor().getSignedTxn4Log();
		String readableTransferList = readableTransferList(itemizedRepresentation());
		log.debug(
				"Transfer list with itemized fees for {} is {}",
				signedTxn,
				readableTransferList);
	}

	TransferList itemizedRepresentation() {
		TransferList canonicalRepr = ctx.ledger().netTransfersInTxn();
		TransferList itemizedFees = ctx.charging().itemizedFees();

		List<AccountAmount> nonFeeAdjustments =
				canonicalDiffRepr(canonicalRepr.getAccountAmountsList(), itemizedFees.getAccountAmountsList());
		return itemizedFees.toBuilder()
				.addAllAccountAmounts(nonFeeAdjustments)
				.build();
	}

	private TransactionReceipt.Builder receiptSoFar() {
		TransactionReceipt.Builder receipt = TransactionReceipt.newBuilder()
				.setExchangeRate(ctx.exchange().activeRates())
				.setStatus(statusSoFar);
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
		receiptConfig = receipt -> receipt.setAccountID(id);
	}

	@Override
	public void setCreated(TokenID id) {
		receiptConfig = receipt -> receipt.setTokenID(id);
	}

	@Override
	public void setCreated(ScheduleID id) {
		receiptConfig = receipt -> receipt.setScheduleID(id);
	}

	@Override
	public void setNewTotalSupply(long newTotalTokenSupply) {
		receiptConfig = receipt -> receipt.setNewTotalSupply(newTotalTokenSupply);
	}

	@Override
	public void setCreated(FileID id) {
		receiptConfig = receipt -> receipt.setFileID(id);
	}

	@Override
	public void setCreated(ContractID id) {
		receiptConfig = receipt -> receipt.setContractID(id);
	}

	@Override
	public void setCreated(TopicID id) {
		receiptConfig = receipt -> receipt.setTopicID(id);
	}

	@Override
	public void setTopicRunningHash(byte[] topicRunningHash, long sequenceNumber) {
		receiptConfig = receipt -> receipt
				.setTopicRunningHash(ByteString.copyFrom(topicRunningHash))
				.setTopicSequenceNumber(sequenceNumber)
				.setTopicRunningHashVersion(MerkleTopic.RUNNING_HASH_VERSION);
	}

	@Override
	public void addNonThresholdFeeChargedToPayer(long amount) {
		otherNonThresholdFees += amount;
	}

	@Override
	public void setCallResult(ContractFunctionResult result) {
		recordConfig = record -> record.setContractCallResult(result);
	}

	@Override
	public void setCreateResult(ContractFunctionResult result) {
		recordConfig = record -> record.setContractCreateResult(result);
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
		if (this.accessor().isTriggeredTxn()) {
			throw new IllegalStateException("Unable to trigger txns in triggered txns");
		}
		this.triggeredTxn = scopedAccessor;
	}

	@Override
	public TxnAccessor triggeredTxn() {
		return triggeredTxn;
	}
}
