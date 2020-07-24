package com.hedera.services.records;

import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.txns.diligence.DuplicateClassification;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static com.hedera.services.txns.diligence.DuplicateClassification.DUPLICATE;
import static com.hedera.services.txns.diligence.DuplicateClassification.NODE_DUPLICATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;

public class TxnIdRecentHistory {
	int numDuplicates = 0;
	List<SubmissionRecord> classifiableRecords = null;
	List<SubmissionRecord> unclassifiableRecords = null;

	public static final EnumSet<ResponseCodeEnum> UNCLASSIFIABLE_STATUSES = EnumSet.of(
		INVALID_NODE_ACCOUNT,
		INVALID_PAYER_SIGNATURE);

	public ExpirableTxnRecord legacyQueryableRecord() {
		if (classifiableRecords != null && !classifiableRecords.isEmpty()) {
			return classifiableRecords.get(0).getRecord();
		}
		return null;
	}

	public void observe(ExpirableTxnRecord record, ResponseCodeEnum status, long submittingMember) {
		if (UNCLASSIFIABLE_STATUSES.contains(status)) {
			addUnclassifiable(record, submittingMember);
		} else {
			addClassifiable(record, submittingMember);
		}
	}

	private void addClassifiable(ExpirableTxnRecord record, long submittingMember) {
		if (classifiableRecords == null) {
			classifiableRecords = new LinkedList<>();
		}
		int i = 0;
		var iter = classifiableRecords.listIterator();
		var submissionRecord = new SubmissionRecord(record, submittingMember);
		boolean isNodeDuplicate = false;
		while (i < numDuplicates) {
			if (submittingMember == iter.next().submittingMember) {
				isNodeDuplicate = true;
				break;
			}
			i++;
		}
		if (isNodeDuplicate) {
			classifiableRecords.add(submissionRecord);
		} else {
			numDuplicates++;
			iter.add(submissionRecord);
		}
	}

	private void addUnclassifiable(ExpirableTxnRecord record, long submittingMember) {
		if (unclassifiableRecords == null) {
			unclassifiableRecords = new LinkedList<>();
		}
		unclassifiableRecords.add(new SubmissionRecord(record, submittingMember));
	}

	public void forgetExpiredAt(long now) {
		Optional.ofNullable(classifiableRecords).ifPresent(l -> forgetFromList(l, now));
		Optional.ofNullable(unclassifiableRecords).ifPresent(l -> forgetFromList(l, now));
	}

	private void forgetFromList(List<SubmissionRecord> records, long now) {
		records.removeIf(sr -> sr.record.getExpiry() <= now);
	}


	public DuplicateClassification currentDuplicityFor(long submittingMember) {
		if (numDuplicates == 0) {
			return BELIEVED_UNIQUE;
		}
		var iter = classifiableRecords.listIterator();
		for (int i = 0; i < numDuplicates; i++) {
			if (iter.next().submittingMember == submittingMember) {
				return NODE_DUPLICATE;
			}
		}
		return DUPLICATE;
	}

	static class SubmissionRecord {
		final long submittingMember;
		final ExpirableTxnRecord record;

		public ExpirableTxnRecord getRecord() {
			return record;
		}

		public SubmissionRecord(ExpirableTxnRecord record, long submittingMember) {
			this.submittingMember = submittingMember;
			this.record = record;
		}
	}
}
