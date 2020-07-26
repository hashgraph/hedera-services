package com.hedera.services.records;

import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.txns.diligence.DuplicateClassification;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static com.hedera.services.txns.diligence.DuplicateClassification.DUPLICATE;
import static com.hedera.services.txns.diligence.DuplicateClassification.NODE_DUPLICATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingLong;

public class TxnIdRecentHistory {
	private static final Comparator<RichInstant> RI_CMP =
			comparingLong(RichInstant::getSeconds).thenComparingInt(RichInstant::getNanos);
	private static final Comparator<ExpirableTxnRecord> SR_CMP = comparing(r -> r.getConsensusTimestamp(), RI_CMP);

	int numDuplicates = 0;
	List<ExpirableTxnRecord> memory = null;
	List<ExpirableTxnRecord> classifiableRecords = null;
	List<ExpirableTxnRecord> unclassifiableRecords = null;

	public static final EnumSet<ResponseCodeEnum> UNCLASSIFIABLE_STATUSES = EnumSet.of(
		INVALID_NODE_ACCOUNT,
		INVALID_PAYER_SIGNATURE);

	public ExpirableTxnRecord legacyQueryableRecord() {
		if (classifiableRecords != null && !classifiableRecords.isEmpty()) {
			return classifiableRecords.get(0);
		}
		return null;
	}

	public boolean isStagePending() {
		return memory != null;
	}

	public boolean isForgotten() {
		return (classifiableRecords == null || classifiableRecords.isEmpty())
				&& (unclassifiableRecords == null || unclassifiableRecords.isEmpty());
	}

	public void observe(ExpirableTxnRecord record, ResponseCodeEnum status) {
		if (UNCLASSIFIABLE_STATUSES.contains(status)) {
			addUnclassifiable(record);
		} else {
			addClassifiable(record);
		}
	}

	public void stage(ExpirableTxnRecord unorderedRecord) {
		if (memory == null) {
			memory = new ArrayList<>();
		}
		memory.add(unorderedRecord);
	}

	public void observeStaged() {
		memory.sort(SR_CMP);
		memory.forEach(record -> this.observe(record, ResponseCodeEnum.valueOf(record.getReceipt().getStatus())));
		memory = null;
	}

	private void addClassifiable(ExpirableTxnRecord record) {
		if (classifiableRecords == null) {
			classifiableRecords = new LinkedList<>();
		}
		int i = 0;
		long submittingMember = record.getSubmittingMember();
		var iter = classifiableRecords.listIterator();
		boolean isNodeDuplicate = false;
		while (i < numDuplicates) {
			if (submittingMember == iter.next().getSubmittingMember()) {
				isNodeDuplicate = true;
				break;
			}
			i++;
		}
		if (isNodeDuplicate) {
			classifiableRecords.add(record);
		} else {
			numDuplicates++;
			iter.add(record);
		}
	}

	private void addUnclassifiable(ExpirableTxnRecord record) {
		if (unclassifiableRecords == null) {
			unclassifiableRecords = new LinkedList<>();
		}
		unclassifiableRecords.add(record);
	}

	public void forgetExpiredAt(long now) {
		Optional.ofNullable(classifiableRecords).ifPresent(l -> forgetFromList(l, now));
		Optional.ofNullable(unclassifiableRecords).ifPresent(l -> forgetFromList(l, now));
	}

	private void forgetFromList(List<ExpirableTxnRecord> records, long now) {
		records.removeIf(record -> record.getExpiry() <= now);
	}

	public DuplicateClassification currentDuplicityFor(long submittingMember) {
		if (numDuplicates == 0) {
			return BELIEVED_UNIQUE;
		}
		var iter = classifiableRecords.listIterator();
		for (int i = 0; i < numDuplicates; i++) {
			if (iter.next().getSubmittingMember() == submittingMember) {
				return NODE_DUPLICATE;
			}
		}
		return DUPLICATE;
	}
}
