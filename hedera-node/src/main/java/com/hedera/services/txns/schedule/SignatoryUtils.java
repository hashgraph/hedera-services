package com.hedera.services.txns.schedule;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.VerificationStatus;
import org.apache.commons.codec.binary.Hex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SignatoryUtils {
	static boolean witnessInScope(
			ScheduleID id,
			ScheduleStore store,
			InHandleActivationHelper activationHelper,
			StringBuilder tmpSb
	) {
		var origSchedule = store.get(id);
		List<byte[]> signatories = signatoriesPresent(origSchedule.transactionBody(), activationHelper);
		int numWitnessed = witness(store, id, signatories);
		tmpSb.append(" - The resolved schedule has now witnessed ")
				.append(numWitnessed)
				.append(" (additional) valid keys sign.\n");
		var revisedSchedule = store.get(id);
		tmpSb.append(" - ").append(revisedSchedule).append("\n");
		var isReadyToExecute = isReady(revisedSchedule, activationHelper);
		if (isReadyToExecute) {
			tmpSb.append(" - Ready for execution!").append("\n");
		} else {
			tmpSb.append(" - Not ready for execution yet.").append("\n");
		}
		return isReadyToExecute;
	}

	static boolean isReady(MerkleSchedule schedule, InHandleActivationHelper activationHelper) {
		var scheduledTxn = uncheckedParse(schedule.transactionBody());
		return activationHelper.areScheduledPartiesActive(
				scheduledTxn,
				(key, sig) -> schedule.hasValidEd25519Signature(key.getEd25519()));
	}

	static int witness(ScheduleStore store, ScheduleID id, List<byte[]> signatories) {
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

	static List<byte[]> signatoriesPresent(
			byte[] correctBytes,
			InHandleActivationHelper activationHelper
	) {
		List<byte[]> signatories = new ArrayList<>();
		activationHelper.visitScheduledCryptoSigs((key, sig) -> {
			if (sig.getSignatureStatus() == VerificationStatus.VALID) {
				var i = sig.getMessageOffset();
				var isCorrectMatter = Arrays.equals(
						correctBytes, 0, correctBytes.length,
						sig.getContentsDirect(), i, i + sig.getMessageLength());
				if (isCorrectMatter) {
					signatories.add(key.getEd25519());
				}
			}
		});
		return signatories;
	}

	static TransactionBody uncheckedParse(byte[] rawScheduledTxn) {
		try {
			return TransactionBody.parseFrom(rawScheduledTxn);
		} catch (InvalidProtocolBufferException e) {
			throw new IllegalArgumentException(e);
		}
	}
}
