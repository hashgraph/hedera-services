package com.hedera.services.legacy.crypto;

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(JUnitPlatform.class)
class SignatureStatusTest {
	@Test
	public void formatsUnresolvableSigners() {
		// given:
		String expMsg = "Cannot resolve required signers for scheduled txn " +
				"[ source = 'handleTransaction', scheduled = '', error = 'INVALID_SCHEDULE_ID' ]";
		// and:
		var errorReport = new SignatureStatus(
				SignatureStatusCode.INVALID_SCHEDULE_ID,
				ResponseCodeEnum.INVALID_SCHEDULE_ID,
				true,
				TransactionID.getDefaultInstance(),
				ScheduleID.getDefaultInstance());

		// when:
		var subject = new SignatureStatus(
				SignatureStatusCode.UNRESOLVABLE_REQUIRED_SIGNERS,
				ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS,
				true,
				TransactionID.getDefaultInstance(),
				TransactionBody.getDefaultInstance(),
				errorReport);

		// expect:
		assertEquals(expMsg, subject.toLogMessage());
	}

	@Test
	public void formatsUnparseableTransaction() {
		// setup:
		var txnId = TransactionID.newBuilder().setAccountID(IdUtils.asAccount("0.0.75231")).build();
		// given:
		String expMsg = String.format("Cannot parse scheduled txn " +
				"[ source = 'handleTransaction', transactionId = '%s' ]", SignatureStatus.format(txnId));
		// and:
		var subject = new SignatureStatus(
				SignatureStatusCode.UNPARSEABLE_SCHEDULED_TRANSACTION,
				ResponseCodeEnum.UNPARSEABLE_SCHEDULED_TRANSACTION,
				true,
				txnId);

		// expect:
		assertEquals(expMsg, subject.toLogMessage());
	}
}