package com.hedera.services.bdd.spec.assertions;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.QueryUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import static com.hedera.services.bdd.spec.assertions.EqualityAssertsProviderFactory.shouldBe;
import static java.util.Collections.EMPTY_LIST;

public class TransactionRecordAsserts extends BaseErroringAssertsProvider<TransactionRecord> {
	static final Logger log = LogManager.getLogger(TransactionRecordAsserts.class);

	public static TransactionRecordAsserts recordWith() { return new TransactionRecordAsserts(); }

	public TransactionRecordAsserts payer(String account) {
		registerIdLookupAssert(account, r -> r.getTransactionID().getAccountID(), AccountID.class, "Bad payer!");
		return this;
	}

	public TransactionRecordAsserts txnId(String expectedTxn) {
		this.<TransactionID>registerTypedProvider("transactionID", spec -> txnId -> {
			try {
				Assert.assertEquals("Wrong txnId!", spec.registry().getTxnId(expectedTxn), txnId);
			} catch (Throwable t) {
				return List.of(t);
			}
			return EMPTY_LIST;
		});
		return this;
	}

	public TransactionRecordAsserts status(ResponseCodeEnum expectedStatus) {
		this.<TransactionReceipt>registerTypedProvider("receipt", spec -> receipt -> {
			try {
				Assert.assertEquals("Bad status!", expectedStatus, receipt.getStatus());
			} catch (Throwable t) {
				return List.of(t);
			}
			return EMPTY_LIST;
		});
		return this;
	}

	public TransactionRecordAsserts checkTopicRunningHashVersion(int versionNumber) {
		this.<TransactionReceipt>registerTypedProvider("receipt", spec -> receipt -> {
			try {
				Assert.assertEquals("Bad TopicRunningHashVerions!",
						versionNumber,
						receipt.getTopicRunningHashVersion());
			} catch (Throwable t) {
				return List.of(t);
			}
			return EMPTY_LIST;
		});
		return this;
	}

	public TransactionRecordAsserts contractCallResult(ContractFnResultAsserts provider) {
		registerTypedProvider("contractCallResult", provider);
		return this;
	}

	public TransactionRecordAsserts contractCreateResult(ContractFnResultAsserts provider) {
		registerTypedProvider("contractCreateResult", provider);
		return this;
	}

	public TransactionRecordAsserts transfers(TransferListAsserts provider) {
		registerTypedProvider("transferList", provider);
		return this;
	}

	public TransactionRecordAsserts fee(Long amount) {
		registerTypedProvider("transactionFee", shouldBe(amount));
		return this;
	}


	public TransactionRecordAsserts memo(String text) {
		registerTypedProvider("memo", shouldBe(text));
		return this;
	}

	public TransactionRecordAsserts fee(Function<HapiApiSpec, Long> amountFn) {
		registerTypedProvider("transactionFee", shouldBe(amountFn));
		return this;
	}

	private <T> void registerTypedProvider(String forField, ErroringAssertsProvider<T> provider) {
		try {
			Method m = TransactionRecord.class.getMethod(QueryUtils.asGetter(forField));
			registerProvider((spec, o) -> {
				TransactionRecord record = (TransactionRecord)o;
				T instance = (T)m.invoke(record);
				ErroringAsserts<T> asserts = provider.assertsFor(spec);
				List<Throwable> errors = asserts.errorsIn(instance);
				AssertUtils.rethrowSummaryError(log, "Bad " + forField + "!", errors);
			});
		} catch (Exception e) {
			log.warn("Unable to register asserts provider for TransactionRecord field '" + forField + "'", e);
		}
	}
}
