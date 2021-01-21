package com.hedera.services.sigs.order;

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

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.crypto.SignatureStatusCode;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.runner.RunWith;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hedera.test.utils.IdUtils.*;

@RunWith(JUnitPlatform.class)
public class SigStatusOrderResultFactoryTest {
	private SigStatusOrderResultFactory subject;
	private boolean inHandleTxnDynamicContext = true;
	private TransactionID txnId = TransactionID.getDefaultInstance();

	@Test
	public void returnsNormalSummaryForValidOrder() {
		// given:
		subject = new SigStatusOrderResultFactory(false);
		SigningOrderResult<SignatureStatus> summary = subject.forValidOrder(new ArrayList<>());

		// expect:
		assertTrue(summary.hasKnownOrder());
	}

	@Test
	public void reportsInvalidAccountError() {
		// setup:
		AccountID invalidAccount = asAccount("1.2.3");
		SignatureStatus expectedError = new SignatureStatus(
				SignatureStatusCode.INVALID_ACCOUNT_ID, ResponseCodeEnum.INVALID_ACCOUNT_ID,
				inHandleTxnDynamicContext, txnId, invalidAccount, null, null, null);

		// given:
		subject = new SigStatusOrderResultFactory(inHandleTxnDynamicContext);
		SigningOrderResult<SignatureStatus> summary = subject.forInvalidAccount(invalidAccount, txnId);
		SignatureStatus error = summary.getErrorReport();

		// expect:
		assertEquals(expectedError.toLogMessage(), error.toLogMessage());
	}

	@Test
	public void reportsGeneralPayerError() {
		// setup:
		AccountID payer = asAccount("0.0.3");
		SignatureStatus expectedError = new SignatureStatus(
				SignatureStatusCode.GENERAL_PAYER_ERROR, ResponseCodeEnum.INVALID_SIGNATURE,
				inHandleTxnDynamicContext, txnId, payer, null, null, null);

		// given:
		subject = new SigStatusOrderResultFactory(inHandleTxnDynamicContext);
		SigningOrderResult<SignatureStatus> summary = subject.forGeneralPayerError(payer, txnId);
		SignatureStatus error = summary.getErrorReport();

		// expect:
		assertEquals(expectedError.toLogMessage(), error.toLogMessage());
	}

	@Test
	public void reportsGeneralError() {
		// setup:
		SignatureStatus expectedError = new SignatureStatus(
				SignatureStatusCode.GENERAL_ERROR, ResponseCodeEnum.INVALID_SIGNATURE,
				inHandleTxnDynamicContext, txnId, null, null, null, null);

		// given:
		subject = new SigStatusOrderResultFactory(inHandleTxnDynamicContext);
		SigningOrderResult<SignatureStatus> summary = subject.forGeneralError(txnId);
		SignatureStatus error = summary.getErrorReport();

		// expect:
		assertEquals(expectedError.toLogMessage(), error.toLogMessage());
	}

	@Test
	public void reportsMissingAccount() {
		// setup:
		AccountID missing = asAccount("1.2.3");
		SignatureStatus expectedError = new SignatureStatus(
				SignatureStatusCode.INVALID_ACCOUNT_ID, ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST,
				inHandleTxnDynamicContext, txnId, missing, null, null, null);

		// given:
		subject = new SigStatusOrderResultFactory(inHandleTxnDynamicContext);
		SigningOrderResult<SignatureStatus> summary = subject.forMissingAccount(missing, txnId);
		SignatureStatus error = summary.getErrorReport();

		// expect:
		assertEquals(expectedError.toLogMessage(), error.toLogMessage());
	}

	@Test
	public void reportsMissingFile() {
		// setup:
		FileID missing = asFile("1.2.3");
		SignatureStatus expectedError = new SignatureStatus(
				SignatureStatusCode.INVALID_FILE_ID, ResponseCodeEnum.INVALID_FILE_ID,
				inHandleTxnDynamicContext, txnId, null, missing, null, null);

		// given:
		subject = new SigStatusOrderResultFactory(inHandleTxnDynamicContext);
		SigningOrderResult<SignatureStatus> summary = subject.forMissingFile(missing, txnId);
		SignatureStatus error = summary.getErrorReport();

		// expect:
		assertEquals(expectedError.toLogMessage(), error.toLogMessage());
	}

	@Test
	public void reportsInvalidContract() {
		// setup:
		ContractID invalid = asContract("1.2.3");
		SignatureStatus expectedError = new SignatureStatus(
				SignatureStatusCode.INVALID_CONTRACT_ID, ResponseCodeEnum.INVALID_CONTRACT_ID,
				inHandleTxnDynamicContext, txnId, null, null, invalid, null);
		// given:
		subject = new SigStatusOrderResultFactory(inHandleTxnDynamicContext);
		SigningOrderResult<SignatureStatus> summary = subject.forInvalidContract(invalid, txnId);
		SignatureStatus error = summary.getErrorReport();

		// expect:
		assertEquals(expectedError.toLogMessage(), error.toLogMessage());
	}

	@Test
	public void reportsImmutableContract() {
		// setup:
		ContractID immutable = asContract("1.2.3");
		SignatureStatus expectedError = new SignatureStatus(
				SignatureStatusCode.IMMUTABLE_CONTRACT, ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT,
				inHandleTxnDynamicContext, txnId, null, null, immutable, null);

		// given:
		subject = new SigStatusOrderResultFactory(inHandleTxnDynamicContext);
		SigningOrderResult<SignatureStatus> summary = subject.forImmutableContract(immutable, txnId);
		SignatureStatus error = summary.getErrorReport();

		// expect:
		assertEquals(expectedError.toLogMessage(), error.toLogMessage());
	}

	@Test
	public void reportsMissingToken() {
		// setup:
		TokenID missing = IdUtils.asToken("1.2.3");
		SignatureStatus expectedError = new SignatureStatus(
				SignatureStatusCode.INVALID_TOKEN_ID, ResponseCodeEnum.INVALID_TOKEN_ID,
				inHandleTxnDynamicContext, txnId, missing);

		// given:
		subject = new SigStatusOrderResultFactory(inHandleTxnDynamicContext);
		SigningOrderResult<SignatureStatus> summary = subject.forMissingToken(missing, txnId);
		SignatureStatus error = summary.getErrorReport();

		// expect:
		assertEquals(expectedError.toLogMessage(), error.toLogMessage());
	}

	@Test
	public void reportsMissingSchedule() {
		// setup:
		ScheduleID missing = IdUtils.asSchedule("1.2.3");
		SignatureStatus expectedError = new SignatureStatus(
				SignatureStatusCode.INVALID_SCHEDULE_ID, ResponseCodeEnum.INVALID_SCHEDULE_ID,
				inHandleTxnDynamicContext, txnId, missing);

		// given:
		subject = new SigStatusOrderResultFactory(inHandleTxnDynamicContext);
		SigningOrderResult<SignatureStatus> summary = subject.forMissingSchedule(missing, txnId);
		SignatureStatus error = summary.getErrorReport();

		// expect:
		assertEquals(expectedError.toLogMessage(), error.toLogMessage());
	}

	@Test
	public void reportsMissingTopic() {
		// setup:
		TopicID missing = asTopic("1.2.3");
		SignatureStatus expectedError = new SignatureStatus(
				SignatureStatusCode.INVALID_TOPIC_ID, ResponseCodeEnum.INVALID_TOPIC_ID,
				inHandleTxnDynamicContext, txnId, null, null, null, missing);

		// given:
		subject = new SigStatusOrderResultFactory(inHandleTxnDynamicContext);
		SigningOrderResult<SignatureStatus> summary = subject.forMissingTopic(missing, txnId);
		SignatureStatus error = summary.getErrorReport();

		// expect:
		assertEquals(expectedError.toLogMessage(), error.toLogMessage());
	}

	@Test
	public void reportsInvalidAutoRenewAccount() {
		// setup:
		AccountID missing = asAccount("11.22.33");
		SignatureStatus expectedError = new SignatureStatus(
				SignatureStatusCode.INVALID_AUTO_RENEW_ACCOUNT_ID, ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT,
				inHandleTxnDynamicContext, txnId, missing, null, null, null);

		// given:
		subject = new SigStatusOrderResultFactory(inHandleTxnDynamicContext);
		SigningOrderResult<SignatureStatus> summary = subject.forMissingAutoRenewAccount(missing, txnId);
		SignatureStatus error = summary.getErrorReport();

		// expect:
		assertEquals(expectedError.toLogMessage(), error.toLogMessage());
	}

	@Test
	public void reportsUnresolvableSigners() {
		// setup:
		var errorReport = new SignatureStatus(
				SignatureStatusCode.INVALID_SCHEDULE_ID,
				ResponseCodeEnum.INVALID_SCHEDULE_ID,
				true,
				TransactionID.getDefaultInstance(),
				ScheduleID.getDefaultInstance());
		TransactionBody scheduled = TransactionBody.getDefaultInstance();
		SignatureStatus expectedError = new SignatureStatus(
				SignatureStatusCode.UNRESOLVABLE_REQUIRED_SIGNERS, ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS,
				inHandleTxnDynamicContext, txnId, scheduled, errorReport);

		// given:
		subject = new SigStatusOrderResultFactory(inHandleTxnDynamicContext);
		var summary = subject.forUnresolvableRequiredSigners(scheduled, txnId, errorReport);
		SignatureStatus error = summary.getErrorReport();

		// expect:
		assertEquals(expectedError.toLogMessage(), error.toLogMessage());
	}

	@Test
	public void reportsUnparseableTxn() {
		// setup:
		SignatureStatus expectedError = new SignatureStatus(
				SignatureStatusCode.UNPARSEABLE_SCHEDULED_TRANSACTION, ResponseCodeEnum.UNPARSEABLE_SCHEDULED_TRANSACTION,
				inHandleTxnDynamicContext, txnId);

		// given:
		subject = new SigStatusOrderResultFactory(inHandleTxnDynamicContext);
		var summary = subject.forUnparseableScheduledTxn(txnId);
		SignatureStatus error = summary.getErrorReport();

		// expect:
		assertEquals(expectedError.toLogMessage(), error.toLogMessage());
	}

	@Test
	public void reportsNestedScheduleCreate() {
		// setup:
		SignatureStatus expectedError = new SignatureStatus(
				SignatureStatusCode.UNSCHEDULABLE_TRANSACTION,
				ResponseCodeEnum.UNSCHEDULABLE_TRANSACTION,
				inHandleTxnDynamicContext,
				txnId);

		// given:
		subject = new SigStatusOrderResultFactory(inHandleTxnDynamicContext);
		var summary = subject.forUnschedulableTxn(txnId);
		SignatureStatus error = summary.getErrorReport();

		// expect:
		assertEquals(expectedError.toLogMessage(), error.toLogMessage());
	}
}
