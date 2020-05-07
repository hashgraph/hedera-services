package com.hedera.services.ledger.properties;

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

import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.swirlds.fcmap.fclist.FCLinkedList;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.hedera.services.ledger.properties.MapValueProperty.*;

@RunWith(JUnitPlatform.class)
public class HederaAccountPropertyTest {
	@Test
	public void cannotSetNegativeBalance() {
		// expect:
		assertThrows(
				IllegalArgumentException.class,
				() -> BALANCE.setter().accept(new HederaAccount(), -1L));
	}

	@Test
	public void gettersAndSettersWork() throws Exception {
		// given:
		boolean origIsDeleted = false;
		boolean origIsReceiverSigReq = false;
		boolean origIsContract = false;
		long origBalance = 1L;
		long origReceivedRecordThresh = 1L;
		long origSendRecordThresh = 1L;
		long origAutoRenew = 1L;
		long origExpiry = 1L;
		Key origKey = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
		String origMemo = "a";
		AccountID origProxy = AccountID.getDefaultInstance();
		FCLinkedList<JTransactionRecord> origRecords = new FCLinkedList<>(JTransactionRecord::deserialize);
		origRecords.add(jRecordWith(ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT));
		origRecords.add(jRecordWith(ResponseCodeEnum.INVALID_PAYER_SIGNATURE));
		// and:
		boolean newIsDeleted = true;
		boolean newIsReceiverSigReq = true;
		boolean newIsContract = true;
		long newBalance = 2L;
		long newReceivedRecordThresh = 2L;
		long newSendRecordThresh = 2L;
		long newAutoRenew = 2L;
		long newExpiry = 2L;
		JKey newKey = new JKeyList();
		String newMemo = "b";
		JAccountID newProxy = new JAccountID(0, 0, 2);
		FCQueue<JTransactionRecord> newRecords = new FCQueue<>(JTransactionRecord::deserialize);
		newRecords.offer(jRecordWith(ResponseCodeEnum.SUCCESS));
		// and:
		HederaAccount account = new HederaAccountCustomizer()
				.fundsReceivedRecordThreshold(origReceivedRecordThresh)
				.fundsSentRecordThreshold(origSendRecordThresh)
				.key(JKey.mapKey(origKey))
				.expiry(origExpiry)
				.proxy(JAccountID.convert(origProxy))
				.autoRenewPeriod(origAutoRenew)
				.isDeleted(origIsDeleted)
				.memo(origMemo)
				.isSmartContract(origIsContract)
				.isReceiverSigRequired(origIsReceiverSigReq)
				.customizing(new HederaAccount());
		account.setBalance(origBalance);
		account.getRecords().offer(origRecords.get(0));
		account.getRecords().offer(origRecords.get(1));

		// when:
		IS_DELETED.setter().accept(account, newIsDeleted);
		IS_RECEIVER_SIG_REQUIRED.setter().accept(account, newIsReceiverSigReq);
		IS_SMART_CONTRACT.setter().accept(account, newIsContract);
		BALANCE.setter().accept(account, newBalance);
		FUNDS_RECEIVED_RECORD_THRESHOLD.setter().accept(account, newReceivedRecordThresh);
		FUNDS_SENT_RECORD_THRESHOLD.setter().accept(account, newSendRecordThresh);
		AUTO_RENEW_PERIOD.setter().accept(account, newAutoRenew);
		EXPIRY.setter().accept(account, newExpiry);
		KEY.setter().accept(account, newKey);
		MEMO.setter().accept(account, newMemo);
		PROXY.setter().accept(account, newProxy);
		TRANSACTION_RECORDS.setter().accept(account, newRecords);

		// then:
		assertEquals(newIsDeleted, IS_DELETED.getter().apply(account));
		assertEquals(newIsReceiverSigReq, IS_RECEIVER_SIG_REQUIRED.getter().apply(account));
		assertEquals(newIsContract, IS_SMART_CONTRACT.getter().apply(account));
		assertEquals(newBalance, BALANCE.getter().apply(account));
		assertEquals(newReceivedRecordThresh, FUNDS_RECEIVED_RECORD_THRESHOLD.getter().apply(account));
		assertEquals(newSendRecordThresh, FUNDS_SENT_RECORD_THRESHOLD.getter().apply(account));
		assertEquals(newAutoRenew, AUTO_RENEW_PERIOD.getter().apply(account));
		assertEquals(newExpiry, EXPIRY.getter().apply(account));
		assertEquals(newKey, KEY.getter().apply(account));
		assertEquals(newMemo, MEMO.getter().apply(account));
		assertEquals(newProxy, PROXY.getter().apply(account));
		assertEquals(newRecords, TRANSACTION_RECORDS.getter().apply(account));
	}

	private JTransactionRecord jRecordWith(ResponseCodeEnum status) {
		return JTransactionRecord.convert(
				TransactionRecord.newBuilder()
					.setReceipt(TransactionReceipt.newBuilder().setStatus(status))
				.build()
		);
	}
}
