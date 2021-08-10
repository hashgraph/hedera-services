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
import com.hedera.services.keys.HederaKeyActivation;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.Rationalization;
import com.hedera.services.sigs.factories.ReusableBodySigningFactory;
import com.hedera.services.state.logic.ServicesTxnManager;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.txns.ProcessLogic;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.fee.FeeObject;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.crypto.TransactionSignature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.EnumSet;
import java.util.function.BiPredicate;

import static com.hedera.services.keys.HederaKeyActivation.payerSigIsActive;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
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
			MODIFYING_IMMUTABLE_CONTRACT,
			INVALID_CONTRACT_ID,
			UNRESOLVABLE_REQUIRED_SIGNERS,
			SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);

	private final ServicesContext ctx;
	private final Rationalization rationalization;
	private final ReusableBodySigningFactory bodySigningFactory;
	private final BiPredicate<JKey, TransactionSignature> validityTest;

	private final ServicesTxnManager txnManager = new ServicesTxnManager(
			this::processTxnInCtx, this::addRecordToStream, this::processTriggeredTxnInCtx, this::warnOf);

	private PayerSigValidity payerSigValidity = HederaKeyActivation::payerSigIsActive;

	public AwareProcessLogic(
			ServicesContext ctx,
			Rationalization rationalization,
			ReusableBodySigningFactory bodySigningFactory,
			BiPredicate<JKey, TransactionSignature> validityTest
	) {
		this.ctx = ctx;
		this.validityTest = validityTest;
		this.rationalization = rationalization;
		this.bodySigningFactory = bodySigningFactory;
	}

	@Override
	public void incorporateConsensusTxn(SwirldTransaction platformTxn, Instant consensusTime, long submittingMember) {
		try {
			final var accessor = ctx.expandHandleSpan().accessorFor(platformTxn);
			Instant effectiveConsensusTime = consensusTime;
			if (accessor.canTriggerTxn()) {
				effectiveConsensusTime = consensusTime.minusNanos(1);
			}

			if (!ctx.invariants().holdFor(accessor, effectiveConsensusTime, submittingMember)) {
				return;
			}

			ctx.expiries().purge(effectiveConsensusTime.getEpochSecond());

			txnManager.process(accessor, effectiveConsensusTime, submittingMember, ctx);
			final var triggeredAccessor = ctx.txnCtx().triggeredTxn();
			if (triggeredAccessor != null) {
				txnManager.process(triggeredAccessor, consensusTime, submittingMember, ctx);
			}

			ctx.entityAutoRenewal().execute(consensusTime);
		} catch (InvalidProtocolBufferException e) {
			log.warn("Consensus platform txn was not gRPC!", e);
		}
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
					ctx.txnCtx().accessor().getSignedTxnWrapper(),
					ctx.ledger().currentChangeSet(),
					e);
		} catch (Exception unexpected) {
			log.error("Failure in {} ::", context, e);
			log.error("Full details could not be logged!", unexpected);
		}
	}

	void addRecordToStream() {
		ctx.recordsHistorian().lastCreatedRecord().ifPresent(finalRecord ->
				stream(ctx.txnCtx().accessor().getSignedTxnWrapper(),
						finalRecord, ctx.txnCtx().consensusTime()));
	}

	private void doTriggeredProcess(TxnAccessor accessor, Instant consensusTime) {
		ctx.networkCtxManager().advanceConsensusClockTo(consensusTime);
		ctx.networkCtxManager().prepareForIncorporating(accessor);

		FeeObject fees = ctx.fees().computeFee(accessor, ctx.txnCtx().activePayerKey(), ctx.currentView());
		var chargingOutcome = ctx.txnChargingPolicy().applyForTriggered(fees);
		if (chargingOutcome != OK) {
			ctx.txnCtx().setStatus(chargingOutcome);
			return;
		}

		process(accessor);
	}

	private void doProcess(TxnAccessor accessor, Instant consensusTime) {
		ctx.networkCtxManager().advanceConsensusClockTo(consensusTime);

		var sigStatus = rationalizeWithPreConsensusSigs(accessor);
		if (hasActivePayerSig(accessor)) {
			ctx.txnCtx().payerSigIsKnownActive();
			ctx.networkCtxManager().prepareForIncorporating(accessor);
		}

		if (!ctx.chargingPolicyAgent().applyPolicyFor(accessor)) {
			return;
		}

		if (SIG_RATIONALIZATION_ERRORS.contains(sigStatus)) {
			ctx.txnCtx().setStatus(sigStatus);
			return;
		}
		if (!ctx.activationHelper().areOtherPartiesActive(validityTest)) {
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
		if (ctx.transitionRunner().tryTransition(accessor)) {
			ctx.networkCtxManager().finishIncorporating(accessor.getFunction());
		}
	}

	boolean hasActivePayerSig(TxnAccessor accessor) {
		try {
			return payerSigValidity.test(accessor, validityTest);
		} catch (Exception unknown) {
			log.warn("Unhandled exception when testing payer sig activation", unknown);
		}
		return false;
	}

	ResponseCodeEnum rationalizeWithPreConsensusSigs(TxnAccessor accessor) {
		bodySigningFactory.resetFor(accessor);

		rationalization.performFor(
				accessor,
				ctx.syncVerifier(),
				ctx.backedKeyOrder(),
				accessor.getPkToSigsFn(),
				bodySigningFactory);
		final var status = rationalization.finalStatus();
		if (status == OK) {
			if (rationalization.usedSyncVerification()) {
				ctx.speedometers().cycleSyncVerifications();
			} else {
				ctx.speedometers().cycleAsyncVerifications();
			}
		}
		return status;
	}

	void stream(
			com.hederahashgraph.api.proto.java.Transaction txn,
			ExpirableTxnRecord expiringRecord,
			Instant consensusTime
	) {
		final var rso = new RecordStreamObject(expiringRecord, txn, consensusTime);
		ctx.updateRecordRunningHash(rso.getRunningHash());
		final var handoff = ctx.nonBlockingHandoff();
		while (!handoff.offer(rso)) {
			/* Cannot proceed until we have handed off the record. */
		}
	}

	@FunctionalInterface
	interface PayerSigValidity {
		boolean test(TxnAccessor accessor, BiPredicate<JKey, TransactionSignature> test);
	}

	void setPayerSigValidity(PayerSigValidity payerSigValidity) {
		this.payerSigValidity = payerSigValidity;
	}
}
