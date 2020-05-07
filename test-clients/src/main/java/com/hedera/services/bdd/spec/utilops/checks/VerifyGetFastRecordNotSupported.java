package com.hedera.services.bdd.spec.utilops.checks;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.TransactionGetFastRecordQuery;
import com.hedera.services.bdd.spec.HapiApiSpec;
import org.junit.Assert;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

public class VerifyGetFastRecordNotSupported extends UtilOp {
	@Override
	protected boolean submitOp(HapiApiSpec spec) {
		TransactionGetFastRecordQuery.Builder op = TransactionGetFastRecordQuery.newBuilder();
		Query query = Query.newBuilder().setTransactionGetFastRecord(op).build();
		Response response = spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls).getFastTransactionRecord(query);
		Assert.assertEquals(
				NOT_SUPPORTED,
				response.getTransactionGetFastRecord().getHeader().getNodeTransactionPrecheckCode());
		return false;
	}
}
