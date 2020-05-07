package com.hedera.services.context.domain.serdes;

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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.swirlds.fcmap.fclist.FCLinkedList;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.SerdeUtils.*;
import static com.hedera.test.utils.IdUtils.*;

@RunWith(JUnitPlatform.class)
public class DomainSerdesTest {
	private DomainSerdes subject = new DomainSerdes();

	@Test
	public void recordsSerdesWork() throws Exception {
		// given:
		FCQueue<JTransactionRecord> recordsIn = new FCQueue<>(JTransactionRecord::deserialize);
		recordsIn.offer(recordOne());
		recordsIn.offer(recordTwo());

		// when:
		byte[] repr = serOutcome(out -> subject.serializeRecords(recordsIn, out));
		// and:
		FCQueue<JTransactionRecord> recordsOut = new FCQueue<>(JTransactionRecord::deserialize);
		deOutcome(in -> { subject.deserializeIntoRecords(in, recordsOut); return recordsOut; }, repr);

		// then:
		assertEquals(recordsIn, recordsOut);
	}

	@Test
	public void legacyRecordsSerdesWork() throws Exception {
		// given:
		FCLinkedList<JTransactionRecord> recordsIn = new FCLinkedList<>(JTransactionRecord::deserialize);
		recordsIn.add(recordOne());
		recordsIn.add(recordTwo());

		// when:
		byte[] repr = serOutcome(out -> subject.serializeLegacyRecords(recordsIn, out));
		// and:
		FCLinkedList<JTransactionRecord> recordsOut = new FCLinkedList<>(JTransactionRecord::deserialize);
		deOutcome(in -> { subject.deserializeIntoLegacyRecords(in, recordsOut); return recordsOut; }, repr);

		// then:
		assertEquals(recordsIn, recordsOut);
	}

	@Test
	public void idSerdesWork() throws Exception {
		// given:
		JAccountID idIn = new JAccountID(1,2, 3);

		// when:
		byte[] repr = serOutcome(out -> subject.serializeId(idIn, out));
		// and:
		JAccountID idOut = deOutcome(in -> subject.deserializeId(in), repr);

		// then:
		assertEquals(idIn, idOut);
	}

	@Test
	public void keySerdesWork() throws Exception {
		// given:
		JKey keyIn = COMPLEX_KEY_ACCOUNT_KT.asJKey();

		// when:
		byte[] repr = serOutcome(out -> subject.serializeKey(keyIn, out));
		// and:
		JKey keyOut = deOutcome(in -> subject.deserializeKey(in), repr);

		// then:
		assertEquals(JKey.mapJKey(keyIn), JKey.mapJKey(keyOut));
	}


	public static JTransactionRecord recordOne() {
		TransactionRecord record = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder()
						.setStatus(INVALID_ACCOUNT_ID)
						.setAccountID(asAccount("0.0.3")))
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder().setSeconds(9_999_999_999L)))
				.setMemo("Alpha bravo charlie")
				.setConsensusTimestamp(Timestamp.newBuilder().setSeconds(9_999_999_999L))
				.setTransactionFee(555L)
				.setTransferList(withAdjustments(
						asAccount("0.0.2"), -4L,
						asAccount("0.0.1001"), 2L,
						asAccount("0.0.1002"), 2L))
				.setContractCallResult(ContractFunctionResult.newBuilder()
						.setContractID(asContract("1.2.3"))
						.setErrorMessage("Couldn't figure it out!")
						.setGasUsed(55L)
						.addLogInfo(ContractLoginfo.newBuilder()
								.setData(ByteString.copyFrom("Nonsense!".getBytes()))))
				.build();
		JTransactionRecord jRecord = JTransactionRecord.convert(record);
		jRecord.setVersionToSerializeContractFunctionResult(2l);
		return jRecord;
	}

	public static JTransactionRecord recordTwo() {
		TransactionRecord record = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder()
						.setStatus(INVALID_CONTRACT_ID)
						.setAccountID(asAccount("0.0.4")))
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder().setSeconds(7_777_777_777L)))
				.setMemo("Alpha bravo charlie")
				.setConsensusTimestamp(Timestamp.newBuilder().setSeconds(7_777_777_777L))
				.setTransactionFee(556L)
				.setTransferList(withAdjustments(
						asAccount("0.0.2"),-6L,
						asAccount("0.0.1001"), 3L,
						asAccount("0.0.1002"), 3L))
				.setContractCallResult(ContractFunctionResult.newBuilder()
						.setContractID(asContract("4.3.2"))
						.setErrorMessage("Couldn't figure it out immediately!")
						.setGasUsed(55L)
						.addLogInfo(ContractLoginfo.newBuilder()
								.setData(ByteString.copyFrom("Nonsensical!".getBytes()))))
				.build();
		JTransactionRecord jRecord = JTransactionRecord.convert(record);
		jRecord.setVersionToSerializeContractFunctionResult(2l);
		return jRecord;
	}
}
