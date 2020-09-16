package com.hedera.services.bdd.spec.transactions.file;

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
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class HapiFileAppend extends HapiTxnOp<HapiFileAppend> {
	static final Logger log = LogManager.getLogger(HapiFileAppend.class);
	private final String file;
	Optional<byte[]> contents = Optional.empty();
	Optional<Supplier<byte[]>> contentsSupplier = Optional.empty();
	Optional<String> path = Optional.empty();

	public HapiFileAppend(String file) {
		this.file = file;
	}

	public HapiFileAppend content(byte[] data) {
		contents = Optional.of(data);
		return this;
	}
	public HapiFileAppend content(String data) {
		contents = Optional.of(data.getBytes());
		return this;
	}
	public HapiFileAppend path(String to) {
		path = Optional.of(to);
		return this;
	}
	public HapiFileAppend contentFrom(Supplier<byte[]> more) {
		contentsSupplier = Optional.of(more);
		return this;
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.FileAppend;
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		if (contentsSupplier.isPresent()) {
			contents = Optional.of(contentsSupplier.get().get());
		} else if (path.isPresent()) {
			contents = Optional.of(Files.toByteArray(new File(path.get())));
		}
		var fid = TxnUtils.asFileId(file, spec);
		FileAppendTransactionBody opBody = spec
				.txns()
				.<FileAppendTransactionBody, FileAppendTransactionBody.Builder>body(
					FileAppendTransactionBody.class, builder -> {
						builder.setFileID(fid);
						contents.ifPresent(b -> builder.setContents(ByteString.copyFrom(b)));
					});
		return b -> b.setFileAppend(opBody);
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getFileSvcStub(targetNodeFor(spec), useTls)::appendContent;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		Timestamp expiry = TxnUtils.currExpiry(file, spec);
		FeeCalculator.ActivityMetrics metricsCalc = (txBody, sigUsage) ->
				fileFees.getFileAppendTxFeeMatrices(txBody, expiry, sigUsage);
		return spec.fees().forActivityBasedOp(HederaFunctionality.FileAppend, metricsCalc,txn, numPayerKeys);
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		return List.of(
				spec -> spec.registry().getKey(effectivePayer(spec)),
				spec -> spec.registry().getKey(file)
		);
	}

	@Override
	protected HapiFileAppend self() {
		return this;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		return super.toStringHelper().add("fileName", file);
	}
}
