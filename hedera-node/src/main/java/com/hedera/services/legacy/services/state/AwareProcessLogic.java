package com.hedera.services.legacy.services.state;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.sigs.sourcing.ScopedSigBytesProvider;
import com.hedera.services.state.logic.ServicesTxnManager;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.txns.ProcessLogic;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.FeeObject;
import com.swirlds.common.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.EnumSet;

import static com.hedera.services.keys.HederaKeyActivation.ONLY_IF_SIG_IS_VALID;
import static com.hedera.services.keys.HederaKeyActivation.payerSigIsActive;
import static com.hedera.services.legacy.crypto.SignatureStatusCode.SUCCESS_VERIFY_ASYNC;
import static com.hedera.services.sigs.HederaToPlatformSigOps.rationalizeIn;
import static com.hedera.services.sigs.Rationalization.IN_HANDLE_SUMMARY_FACTORY;
import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static com.hedera.services.txns.diligence.DuplicateClassification.DUPLICATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;

public class AwareProcessLogic implements ProcessLogic {
	private static final Logger log = LogManager.getLogger(AwareProcessLogic.class);

	private static final EnumSet<ResponseCodeEnum> SIG_RATIONALIZATION_ERRORS = EnumSet.of(
			INVALID_FILE_ID,
			INVALID_TOKEN_ID,
			INVALID_ACCOUNT_ID,
			INVALID_SCHEDULE_ID,
			INVALID_SIGNATURE,
			KEY_PREFIX_MISMATCH,
			INVALID_SIGNATURE_COUNT_MISMATCHING_KEY,
			MODIFYING_IMMUTABLE_CONTRACT,
			INVALID_CONTRACT_ID,
			UNRESOLVABLE_REQUIRED_SIGNERS,
			SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);

	private final ServicesContext ctx;

	private final ServicesTxnManager txnManager = new ServicesTxnManager(
			this::processTxnInCtx, this::addRecordToStream, this::processTriggeredTxnInCtx, this::warnOf);

	public AwareProcessLogic(ServicesContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public void incorporateConsensusTxn(Transaction platformTxn, Instant consensusTime, long submittingMember) {
		try {
			PlatformTxnAccessor accessor = new PlatformTxnAccessor(platformTxn);
			Instant timestamp = consensusTime;
			if (accessor.canTriggerTxn()) {
				timestamp = timestamp.minusNanos(1);
			}

			if (!txnSanityChecks(accessor, timestamp, submittingMember)) {
				return;
			}
			txnManager.process(accessor, timestamp, submittingMember, ctx);

			if (ctx.txnCtx().triggeredTxn() != null) {
				TxnAccessor scopedAccessor = ctx.txnCtx().triggeredTxn();
				txnManager.process(scopedAccessor, consensusTime, submittingMember, ctx);
			}
		} catch (InvalidProtocolBufferException e) {
			log.warn("Consensus platform txn was not gRPC!", e);
		}
	}

	private boolean txnSanityChecks(PlatformTxnAccessor accessor, Instant consensusTime, long submittingMember) {
		var lastHandled = ctx.consensusTimeOfLastHandledTxn();
		if (lastHandled != null && !consensusTime.isAfter(lastHandled)) {
			var msg = String.format(
					"Catastrophic invariant failure! Non-increasing consensus time %s versus last-handled %s: %s",
					consensusTime, lastHandled, accessor.getSignedTxn4Log());
			log.error(msg);
			return false;
		}
		if (ctx.addressBook().getAddress(submittingMember).getStake() == 0L) {
			var msg = String.format("Ignoring a transaction submitted by zero-stake node %d: %s",
					submittingMember,
					accessor.getSignedTxn4Log());
			log.warn(msg);
			return false;
		}
		return true;
	}

	private void processTxnInCtx() {
		doProcess(ctx.txnCtx().accessor(), ctx.txnCtx().consensusTime());
	}

	private void processTriggeredTxnInCtx() {
		doTriggeredProcess(ctx.txnCtx().accessor(), ctx.txnCtx().consensusTime());
	}

	private void warnOf(Exception e, String context) {
		String tpl = "Possibly CATASTROPHIC failure in {} :: {} ==>> {} ==>>";
		try {
			log.error(
					tpl,
					context,
					ctx.txnCtx().accessor().getSignedTxn4Log(),
					ctx.ledger().currentChangeSet(),
					e);
		} catch (Exception unexpected) {
			log.error("Failure in {} ::", context, e);
			log.error("Full details could not be logged!", unexpected);
		}
	}

	private void addRecordToStream() {
		var finalRecord = ctx.recordsHistorian().lastCreatedRecord().get();
		addForStreaming(ctx.txnCtx().accessor().getBackwardCompatibleSignedTxn(), finalRecord,
				ctx.txnCtx().consensusTime());
	}

	private void doTriggeredProcess(TxnAccessor accessor, Instant consensusTime) {
		ctx.networkCtxManager().advanceConsensusClockTo(consensusTime);
		ctx.networkCtxManager().prepareForIncorporating(accessor.getFunction());

		FeeObject fee = ctx.fees().computeFee(accessor, ctx.txnCtx().activePayerKey(), ctx.currentView());
		var chargingOutcome = ctx.txnChargingPolicy().applyForTriggered(ctx.charging(), fee);
		if (chargingOutcome != OK) {
			ctx.txnCtx().setStatus(chargingOutcome);
			return;
		}

		process(accessor);
	}

	private void doProcess(TxnAccessor accessor, Instant consensusTime) {
		ctx.networkCtxManager().advanceConsensusClockTo(consensusTime);
		ctx.recordsHistorian().purgeExpiredRecords();
		ctx.expiries().purgeExpiredEntitiesAt(consensusTime.getEpochSecond());

		var sigStatus = rationalizeWithPreConsensusSigs(accessor);
		if (hasActivePayerSig(accessor)) {
			ctx.txnCtx().payerSigIsKnownActive();
			ctx.networkCtxManager().prepareForIncorporating(accessor.getFunction());
		}

		FeeObject fee = ctx.fees().computeFee(accessor, ctx.txnCtx().activePayerKey(), ctx.currentView());

		var recentHistory = ctx.txnHistories().get(accessor.getTxnId());
		var duplicity = (recentHistory == null)
				? BELIEVED_UNIQUE
				: recentHistory.currentDuplicityFor(ctx.txnCtx().submittingSwirldsMember());

		if (ctx.nodeDiligenceScreen().nodeIgnoredDueDiligence(duplicity)) {
			ctx.txnChargingPolicy().applyForIgnoredDueDiligence(ctx.charging(), fee);
			return;
		}
		if (duplicity == DUPLICATE) {
			ctx.txnChargingPolicy().applyForDuplicate(ctx.charging(), fee);
			ctx.txnCtx().setStatus(DUPLICATE_TRANSACTION);
			return;
		}

		var chargingOutcome = ctx.txnChargingPolicy().apply(ctx.charging(), fee);
		if (chargingOutcome != OK) {
			ctx.txnCtx().setStatus(chargingOutcome);
			return;
		}
		if (SIG_RATIONALIZATION_ERRORS.contains(sigStatus.getResponseCode())) {
			ctx.txnCtx().setStatus(sigStatus.getResponseCode());
			return;
		}
		if (!ctx.activationHelper().areOtherPartiesActive(ONLY_IF_SIG_IS_VALID)) {
			ctx.txnCtx().setStatus(INVALID_SIGNATURE);
			return;
		}

		process(accessor);
	}

	private void process(TxnAccessor accessor) {
		var sysAuthStatus = ctx.systemOpPolicies().check(accessor).asStatus();
		if (sysAuthStatus != OK) {
			ctx.txnCtx().setStatus(sysAuthStatus);
			return;
		}
		var transitionLogic = ctx.transitionLogic().lookupFor(accessor.getFunction(), accessor.getTxn());
		if (transitionLogic.isEmpty()) {
			log.warn("Transaction w/o applicable transition logic at consensus :: {}", accessor::getSignedTxn4Log);
			ctx.txnCtx().setStatus(FAIL_INVALID);
			return;
		}
		var logic = transitionLogic.get();
		var opValidity = logic.syntaxCheck().apply(accessor.getTxn());
		if (opValidity != OK) {
			ctx.txnCtx().setStatus(opValidity);
			return;
		}
		logic.doStateTransition();

		ctx.networkCtxManager().finishIncorporating(accessor.getFunction());
	}

	private boolean hasActivePayerSig(TxnAccessor accessor) {
		try {
			return payerSigIsActive(accessor, ctx.backedKeyOrder(), IN_HANDLE_SUMMARY_FACTORY);
		} catch (Exception edgeCase) {
			log.warn("Almost inconceivably, when testing payer sig activation:", edgeCase);
		}
		return false;
	}

	private SignatureStatus rationalizeWithPreConsensusSigs(TxnAccessor accessor) {
		var sigProvider = new ScopedSigBytesProvider(accessor);
		var sigStatus = rationalizeIn(
				accessor,
				ctx.syncVerifier(),
				ctx.backedKeyOrder(),
				sigProvider,
				ctx.sigFactoryCreator()::createScopedFactory);
		if (!sigStatus.isError()) {
			if (sigStatus.getStatusCode() == SUCCESS_VERIFY_ASYNC) {
				ctx.speedometers().cycleAsyncVerifications();
			} else {
				ctx.speedometers().cycleSyncVerifications();
			}
		}
		return sigStatus;
	}

	void addForStreaming(
			com.hederahashgraph.api.proto.java.Transaction grpcTransaction,
			TransactionRecord transactionRecord,
			Instant consensusTimeStamp
	) {
		var recordStreamObject = new RecordStreamObject(transactionRecord, grpcTransaction, consensusTimeStamp);
		ctx.updateRecordRunningHash(recordStreamObject.getRunningHash());
		ctx.recordStreamManager().addRecordStreamObject(recordStreamObject);
	}
}
