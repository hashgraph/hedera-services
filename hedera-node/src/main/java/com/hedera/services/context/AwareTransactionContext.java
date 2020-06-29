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
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.*;
import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.common.Address;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static com.hedera.services.utils.MiscUtils.canonicalDiffRepr;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromString;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hedera.services.legacy.core.jproto.JKey.mapKey;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

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

	static final JKey EMPTY_HEDERA_KEY;
	static {
		try {
			EMPTY_HEDERA_KEY = mapKey(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build());
		} catch (Exception impossible) {
			throw new IllegalStateException("Empty Hedera key could not be initialized! " + impossible.getMessage());
		}
	}

	private final ServicesContext ctx;

	private final Consumer<TransactionRecord.Builder> noopRecordConfig = ignore -> {};
	private final Consumer<TransactionReceipt.Builder> noopReceiptConfig = ignore -> {};

	private long submittingMember;
	private long otherNonThresholdFees;
	private boolean isPayerSigKnownActive;
	private Instant consensusTime;
	private Timestamp consensusTimestamp;
	private ByteString hash;
	private ResponseCodeEnum statusSoFar;
	private PlatformTxnAccessor accessor;
	private Consumer<TransactionRecord.Builder> recordConfig = noopRecordConfig;
	private Consumer<TransactionReceipt.Builder> receiptConfig = noopReceiptConfig;

	public AwareTransactionContext(ServicesContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public void resetFor(PlatformTxnAccessor accessor, Instant consensusTime, long submittingMember) {
		this.accessor = accessor;
		this.consensusTime = consensusTime;
		this.submittingMember = submittingMember;

		otherNonThresholdFees = 0L;
		hash = MiscUtils.sha384HashOf(accessor);
		statusSoFar = UNKNOWN;
		consensusTimestamp = asTimestamp(consensusTime);
		recordConfig = noopRecordConfig;
		receiptConfig = noopReceiptConfig;
		isPayerSigKnownActive = false;

		ctx.charging().resetFor(accessor);
	}

	@Override
	public JKey activePayerKey() {
		return isPayerSigKnownActive
				? ctx.accounts().get(MerkleEntityId.fromPojoAccountId(accessor.getPayer())).getKey()
				: EMPTY_HEDERA_KEY;
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
			log.warn("No address was available for member {}, returning account 0.0.0!", submittingMember, e);
		}
		return AccountID.getDefaultInstance();
	}

	@Override
	public long submittingSwirldsMember() {
		return submittingMember;
	}

	@Override
	public TransactionRecord recordSoFar() {
		long amount = ctx.charging().totalNonThresholdFeesChargedToPayer() + otherNonThresholdFees;

		if (log.isDebugEnabled()) {
			logItemized();
		}
		TransactionRecord.Builder record = TransactionRecord.newBuilder()
				.setMemo(accessor.getTxn().getMemo())
				.setReceipt(receiptSoFar())
				.setTransferList(ctx.ledger().netTransfersInTxn())
				.setTransactionID(accessor.getTxnId())
				.setTransactionFee(amount)
				.setTransactionHash(hash)
				.setConsensusTimestamp(consensusTimestamp);

		recordConfig.accept(record);

		return record.build();
	}

	private void logItemized() {
		Transaction signedTxn = accessor().getSignedTxn4Log();
		String readableTransferList = MiscUtils.readableTransferList(itemizedRepresentation());
		log.debug(
				"Transfer list with temized fees for {} is {}",
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
	public PlatformTxnAccessor accessor() {
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
}
