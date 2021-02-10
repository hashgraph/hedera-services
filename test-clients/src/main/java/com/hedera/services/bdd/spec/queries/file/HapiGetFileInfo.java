package com.hedera.services.bdd.spec.queries.file;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.*;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.Optional;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;

public class HapiGetFileInfo extends HapiQueryOp<HapiGetFileInfo> {
	static final Logger log = LogManager.getLogger(HapiGetFileInfo.class);

	private static final String MISSING_FILE = "<n/a>";

	private String file = MISSING_FILE;

	private boolean immutable = false;
	private Optional<String> saveFileInfoToReg = Optional.empty();
	private Optional<Boolean> expectedDeleted = Optional.empty();
	private Optional<String> expectedWacl = Optional.empty();
	private Optional<String> expectedMemo = Optional.empty();
	private Optional<LongSupplier> expectedExpiry = Optional.empty();
	private Optional<LongPredicate> expiryTest = Optional.empty();
	private Optional<Supplier<String>> fileSupplier = Optional.empty();

	private FileID fileId;

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.FileGetInfo;
	}

	@Override
	protected HapiGetFileInfo self() {
		return this;
	}

	public HapiGetFileInfo isUnmodifiable() {
		immutable = true;
		return this;
	}

	public HapiGetFileInfo hasMemo(String v) {
		expectedMemo = Optional.of(v);
		return this;
	}

	public HapiGetFileInfo hasExpiry(LongSupplier expected) {
		expiryTest = Optional.of(v -> v == expected.getAsLong());
		return this;
	}

	public HapiGetFileInfo hasExpiryPassing(LongPredicate test) {
		expiryTest = Optional.of(test);
		return this;
	}

	public HapiGetFileInfo hasDeleted(boolean expected) {
		expectedDeleted = Optional.of(expected);
		return this;
	}

	public HapiGetFileInfo hasWacl(String expected) {
		expectedWacl = Optional.of(expected);
		return this;
	}

	public HapiGetFileInfo saveToRegistry(String name) {
		saveFileInfoToReg = Optional.of(name);
		return this;
	}

	public HapiGetFileInfo(String file) {
		this.file = file;
	}

	public HapiGetFileInfo(Supplier<String> supplier) {
		fileSupplier = Optional.of(supplier);
	}

	@Override
	protected void submitWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getFileInfoQuery(spec, payment, false);
		response = spec.clients().getFileSvcStub(targetNodeFor(spec), useTls).getFileInfo(query);
		if (verboseLoggingOn) {
			log.info("Info for file '" + file + "': " + response.getFileGetInfo());
		}
		if(saveFileInfoToReg.isPresent()) {
			spec.registry().saveFileInfo(saveFileInfoToReg.get(), response.getFileGetInfo().getFileInfo());
		}
	}

	@Override
	protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getFileInfoQuery(spec, payment, true);
		Response response = spec.clients().getFileSvcStub(targetNodeFor(spec), useTls).getFileInfo(query);
		return costFrom(response);
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
		var info = response.getFileGetInfo().getFileInfo();

		Assert.assertEquals(
				"Wrong file id!",
				TxnUtils.asFileId(file, spec),
				info.getFileID());

		if (immutable) {
			Assert.assertFalse("Should have no WACL, expected immutable!", info.hasKeys());
		}
		expectedWacl.ifPresent(k -> Assert.assertEquals(
				"Bad WACL!",
				spec.registry().getKey(k).getKeyList(),
				info.getKeys()));
		expectedDeleted.ifPresent(f -> Assert.assertEquals("Bad deletion status!", f, info.getDeleted()));
		long actual = info.getExpirationTime().getSeconds();
		expiryTest.ifPresent(p ->
				Assert.assertTrue(String.format("Expiry of %d was not as expected!", actual), p.test(actual)));
		expectedMemo.ifPresent(e -> Assert.assertEquals(e, info.getMemo()));
	}

	private Query getFileInfoQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
		file = fileSupplier.isPresent() ? fileSupplier.get().get() : file;
		var id = TxnUtils.asFileId(file, spec);
		fileId = id;
		FileGetInfoQuery infoQuery = FileGetInfoQuery.newBuilder()
			.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
			.setFileID(id)
			.build();
		return Query.newBuilder().setFileGetInfo(infoQuery).build();
	}

	@Override
	protected boolean needsPayment() {
		return true;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		return super.toStringHelper()
				.add("file", file)
				.add("fileId", fileId);
	}
}
