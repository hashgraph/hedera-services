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

import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.charging.NarratedCharging;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.expiry.ExpiringEntity;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SwirldsTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

/**
 * Implements a transaction context using infrastructure known to be
 * available in the node context. This is the most convenient implementation,
 * since such infrastructure will often depend on an instance of
 * {@link TransactionContext}; and we risk circular dependencies if we
 * inject the infrastructure as dependencies here.
 */
@Singleton
public class BasicTransactionContext implements TransactionContext {
	private static final Logger log = LogManager.getLogger(BasicTransactionContext.class);

	public static final JKey EMPTY_KEY;

	static {
		EMPTY_KEY = asFcKeyUnchecked(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build());
	}

	private TxnAccessor triggeredTxn = null;

	private static final Consumer<TxnReceipt.Builder> noopReceiptConfig = ignore -> {
	};
	private static final Consumer<ExpirableTxnRecord.Builder> noopRecordConfig = ignore -> {
	};

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
	private List<FcAssessedCustomFee> assessedCustomFees;

	ExpirableTxnRecord.Builder recordSoFar = ExpirableTxnRecord.newBuilder();
	private EvmFnResult evmFnResult;

	private final NodeInfo nodeInfo;
	private final EntityCreator creator;
	private final EntityIdSource ids;
	private final NarratedCharging narratedCharging;
	private final HbarCentExchange exchange;
	private final SideEffectsTracker sideEffectsTracker;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;

	@Inject
	BasicTransactionContext(
			final NarratedCharging narratedCharging,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final NodeInfo nodeInfo,
			final HbarCentExchange exchange,
			final EntityCreator creator,
			final SideEffectsTracker sideEffectsTracker,
			final EntityIdSource ids
	) {
		this.ids = ids;
		this.accounts = accounts;
		this.narratedCharging = narratedCharging;
		this.nodeInfo = nodeInfo;
		this.exchange = exchange;
		this.sideEffectsTracker = sideEffectsTracker;
		this.creator = creator;
	}

	@Override
	public void resetFor(final TxnAccessor accessor, final Instant consensusTime, final long submittingMember) {
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
		assessedCustomFees = null;

		narratedCharging.resetForTxn(accessor, submittingMember);

		ids.resetProvisionalIds();
		recordSoFar.reset();
		sideEffectsTracker.reset();
		evmFnResult = null;
	}

	@Override
	public void setAssessedCustomFees(final List<FcAssessedCustomFee> assessedCustomFees) {
		this.assessedCustomFees = assessedCustomFees;
	}

	@Override
	public JKey activePayerKey() {
		return isPayerSigKnownActive
				? accounts.get().get(fromAccountId(accessor.getPayer())).getAccountKey()
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
			return nodeInfo.accountOf(submittingMember);
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
	public ExpirableTxnRecord.Builder recordSoFar() {
		final var receiptBuilder = receiptSoFar();
		final var totalFees = narratedCharging.totalFeesChargedToPayer() + otherNonThresholdFees;
		recordSoFar = creator.createTopLevelRecord(
				totalFees,
				hash,
				accessor,
				consensusTime,
				receiptBuilder,
				assessedCustomFees,
				sideEffectsTracker);

		recordConfig.accept(recordSoFar);
		return recordSoFar;
	}

	TxnReceipt.Builder receiptSoFar() {
		final var receipt = TxnReceipt.newBuilder()
				.setExchangeRates(exchange.fcActiveRates())
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
	public SwirldsTxnAccessor swirldsTxnAccessor() {
		if (accessor instanceof SwirldsTxnAccessor swirldsTxnAccessor) {
			return swirldsTxnAccessor;
		} else {
			throw new IllegalStateException("This context did not originate from a Swirlds transaction");
		}
	}

	@Override
	public void setStatus(final ResponseCodeEnum status) {
		statusSoFar = status;
	}

	@Override
	public void setCreated(final AccountID id) {
		receiptConfig = receipt -> receipt.setAccountId(EntityId.fromGrpcAccountId(id));
	}

	@Override
	public void setCreated(final ScheduleID id) {
		receiptConfig = receipt -> receipt.setScheduleId(EntityId.fromGrpcScheduleId(id));
	}

	@Override
	public void setScheduledTxnId(final TransactionID txnId) {
		receiptConfig = receiptConfig.andThen(receipt -> receipt.setScheduledTxnId(TxnId.fromGrpc(txnId)));
	}

	@Override
	public void setCreated(final FileID id) {
		receiptConfig = receipt -> receipt.setFileId(EntityId.fromGrpcFileId(id));
	}

	@Override
	public void setTargetedContract(final ContractID id) {
		receiptConfig = receipt -> receipt.setContractId(EntityId.fromGrpcContractId(id));
	}

	@Override
	public void setCreated(final TopicID id) {
		receiptConfig = receipt -> receipt.setTopicId(EntityId.fromGrpcTopicId(id));
	}

	@Override
	public void setTopicRunningHash(final byte[] topicRunningHash, final long sequenceNumber) {
		receiptConfig = receipt -> receipt
				.setTopicRunningHash(topicRunningHash)
				.setTopicSequenceNumber(sequenceNumber)
				.setRunningHashVersion(MerkleTopic.RUNNING_HASH_VERSION);
	}

	@Override
	public void addNonThresholdFeeChargedToPayer(final long amount) {
		otherNonThresholdFees += amount;
	}

	@Override
	public void setCallResult(final EvmFnResult result) {
		this.evmFnResult = result;
		recordConfig = expiringRecord -> expiringRecord.setContractCallResult(result);
	}

	@Override
	public void updateForEvmCall(final EthTxData callContext, EntityId senderId) {
		this.evmFnResult.updateForEvmCall(callContext, senderId);
		var wrappedRecordConfig = recordConfig;
		recordConfig = expiringRecord -> {
			wrappedRecordConfig.accept(expiringRecord);
			expiringRecord.setEthereumHash(callContext.getEthereumHash());
		};
	}

	@Override
	public void setCreateResult(final EvmFnResult result) {
		this.evmFnResult = result;
		recordConfig = expiringRecord -> expiringRecord.setContractCreateResult(result);
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
	public void trigger(final TxnAccessor scopedAccessor) {
		if (accessor().isTriggeredTxn()) {
			throw new IllegalStateException("Unable to trigger txns in triggered txns");
		}
		if (triggeredTxn != null && (triggeredTxn != scopedAccessor)) {
			throw new IllegalStateException("Unable to trigger more than one txn.");
		}
		triggeredTxn = scopedAccessor;
	}

	@Override
	public TxnAccessor triggeredTxn() {
		return triggeredTxn;
	}

	@Override
	public void addExpiringEntities(final Collection<ExpiringEntity> entities) {
		expiringEntities.addAll(entities);
	}

	@Override
	public List<ExpiringEntity> expiringEntities() {
		return expiringEntities;
	}

	@Override
	public boolean hasContractResult() {
		return evmFnResult != null;
	}

	@Override
	public long getGasUsedForContractTxn() {
		return evmFnResult.getGasUsed();
	}

	/* --- Used by unit tests --- */
	List<FcAssessedCustomFee> getAssessedCustomFees() {
		return assessedCustomFees;
	}

	ExpirableTxnRecord.Builder getRecordSoFar() {
		return recordSoFar;
	}

	void setRecordSoFar(final ExpirableTxnRecord.Builder recordSoFar) {
		this.recordSoFar = recordSoFar;
	}

	long getNonThresholdFeeChargedToPayer() {
		return otherNonThresholdFees;
	}
}
