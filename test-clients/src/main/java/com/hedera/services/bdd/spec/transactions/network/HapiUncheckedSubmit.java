package com.hedera.services.bdd.spec.transactions.network;

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

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.UncheckedSubmitBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;
import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;

public class HapiUncheckedSubmit<T extends HapiTxnOp<T>> extends HapiTxnOp<HapiUncheckedSubmit<T>> {
	private static final Logger log = LogManager.getLogger(HapiUncheckedSubmit.class);

	private final HapiTxnOp<T> subOp;

	public HapiUncheckedSubmit(HapiTxnOp<T> subOp) {
		this.subOp = subOp;
		this.hasAnyStatusAtAll();
	}

	@Override
	protected HapiUncheckedSubmit self() {
		return this;
	}

	@Override
	public HederaFunctionality type() {
		return UncheckedSubmit;
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		var subOpBytes = subOp.serializeSignedTxnFor(spec);
		if (verboseLoggingOn) {
			log.info("Submitting unchecked: " +
					CommonUtils.extractTransactionBody(Transaction.parseFrom(subOpBytes)));
		}
		UncheckedSubmitBody opBody = spec
				.txns()
				.<UncheckedSubmitBody, UncheckedSubmitBody.Builder>body(
						UncheckedSubmitBody.class, b -> {
								b.setTransactionBytes(ByteString.copyFrom(subOpBytes));
						}
				);
		return b -> b.setUncheckedSubmit(opBody);
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getNetworkSvcStub(targetNodeFor(spec), useTls)::uncheckedSubmit;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) {
		return 0L;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper();
		return helper;
	}
}
