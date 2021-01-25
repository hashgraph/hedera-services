package com.hedera.services.txns.schedule;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.VerificationStatus;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;

public class SignatoryUtils {
	@FunctionalInterface
	interface SigningsWitness {
		Pair<ResponseCodeEnum, Boolean> observeInScope(
				int numSigs,
				ScheduleID id,
				ScheduleStore store,
				InHandleActivationHelper activationHelper);
	}

	/**
	 * Analyzes four pieces of data to determine how a transition logic
	 * should respond to the signatures scoped to the given
	 * {@code InHandleActivationHandler}. These four pieces of data are:
	 * <ol>
	 *     <li>{@code A}, the number of signing attempts in scope.</li>
	 *     <li>{@code V}, the number of {@code VALID} scheduled transaction signatures.</li>
	 *     <li>{@code I}, the number of {@code INVALID} scheduled transaction signatures.</li>
	 *     <li>{@code N}, how many of the {@code VALID} signatures represent new signatories for the schedule.</li>
	 * </ol>
	 *
	 * If {@code A == 0}, responds with {@code Pair.of(OK, false)}.
	 *
	 * If {@code V &lt; A} and {@code I &gt; 0}, responds with {@code Pair.of(SOME_SIGNATURES_WERE_INVALID, false)} .
	 *
	 * If {@code V == A}, ignores any {@code INVALID} signatures, since it is always
	 * possible that a prefix collision between required signing keys will result in
	 * expanding multiple signatures for a given {@code SignaturePair} entry.
	 *
	 * However, if {@code N == 0} then returns {@code Pair.of(NO_NEW_VALID_SIGNATURES, false)}
	 * because all the valid signatures represented Ed25519 signatories already present on the schedule.
	 *
	 * Otherwise returns {@code Pair.of(OK, true)} if the new signatories activated the
	 * schedule, and {@code Pair.of(OK, false)} if they did not.
	 */
	static Pair<ResponseCodeEnum, Boolean> witnessInScope(
			int numSigs,
			ScheduleID id,
			ScheduleStore store,
			InHandleActivationHelper activationHelper
	) {
		var status = OK;
		if (numSigs > 0) {
			status = witnessInNonTrivialScope(numSigs, id, store, activationHelper);
		}

		if (status == SOME_SIGNATURES_WERE_INVALID) {
			return Pair.of(SOME_SIGNATURES_WERE_INVALID, false);
		}

		var revisedSchedule = store.get(id);
		var isReadyToExecute = isReady(revisedSchedule, activationHelper);
		if (isReadyToExecute) {
			status = OK;
		}
		return Pair.of(status, isReadyToExecute);
	}

	private static ResponseCodeEnum witnessInNonTrivialScope(
			int numSigs,
			ScheduleID id,
			ScheduleStore store,
			InHandleActivationHelper activationHelper
	) {
		AtomicInteger numInvalid = new AtomicInteger();
		List<byte[]> signatories = new ArrayList<>();
		activationHelper.visitScheduledCryptoSigs((key, sig) -> {
			if (sig.getSignatureStatus() == VerificationStatus.VALID) {
				appendIfUnique(signatories, key.getEd25519());
			} else {
				numInvalid.getAndIncrement();
			}
		});

		int numValid = signatories.size();
		if (numValid < numSigs && numInvalid.get() > 0) {
			return SOME_SIGNATURES_WERE_INVALID;
		}

		int numWitnessed = witness(store, id, signatories);
		return (numWitnessed == 0) ? NO_NEW_VALID_SIGNATURES : OK;
	}

	private static void appendIfUnique(List<byte[]> l, byte[] bytes) {
		for (byte[] extant : l) {
			if (Arrays.equals(extant, bytes)) {
				return;
			}
		}
		l.add(bytes);
	}

	private static boolean isReady(MerkleSchedule schedule, InHandleActivationHelper activationHelper) {
		var scheduledTxn = uncheckedParse(schedule.transactionBody());
		return activationHelper.areScheduledPartiesActive(
				scheduledTxn,
				(key, sig) -> schedule.hasValidEd25519Signature(key.getEd25519()));
	}

	private static int witness(ScheduleStore store, ScheduleID id, List<byte[]> signatories) {
		AtomicInteger numWitnessed = new AtomicInteger();
		store.apply(id, schedule -> {
			for (byte[] key : signatories) {
				if (schedule.witnessValidEd25519Signature(key)) {
					numWitnessed.getAndIncrement();
				}
			}
		});
		return numWitnessed.get();
	}

	private static TransactionBody uncheckedParse(byte[] rawScheduledTxn) {
		try {
			return TransactionBody.parseFrom(rawScheduledTxn);
		} catch (InvalidProtocolBufferException e) {
			throw new IllegalArgumentException(e);
		}
	}
}
