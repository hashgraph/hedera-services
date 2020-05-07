package com.hedera.services.bdd.spec.queries.meta;

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
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NetworkGetVersionInfoQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

import java.util.Optional;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;

public class HapiGetVersionInfo extends HapiQueryOp<HapiGetVersionInfo> {
	private static final Logger log = LogManager.getLogger(HapiGetVersionInfo.class);

	Optional<SemanticVersion> expectedProto = Optional.empty();
	Optional<SemanticVersion> expectedServices = Optional.empty();

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.GetVersionInfo;
	}

	@Override
	protected HapiGetVersionInfo self() {
		return this;
	}

	public HapiGetVersionInfo hasProtoSemVer(SemanticVersion sv) {
		return hasProtoSemVer(sv.getMajor(), sv.getMinor(), sv.getPatch());
	}

	public HapiGetVersionInfo hasServicesSemVer(SemanticVersion sv) {
		return hasServicesSemVer(sv.getMajor(), sv.getMinor(), sv.getPatch());
	}

	public HapiGetVersionInfo hasProtoSemVer(int major, int minor, int patch) {
		expectedProto = Optional.of(SemanticVersion.newBuilder()
				.setMajor(major)
				.setMinor(minor)
				.setPatch(patch)
				.build());
		return this;
	}

	public HapiGetVersionInfo hasServicesSemVer(int major, int minor, int patch) {
		expectedServices = Optional.of(SemanticVersion.newBuilder()
				.setMajor(major)
				.setMinor(minor)
				.setPatch(patch)
				.build());
		return this;
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
		SemanticVersion actualProto = response.getNetworkGetVersionInfo().getHapiProtoVersion();
		SemanticVersion actualServices = response.getNetworkGetVersionInfo().getHederaServicesVersion();
		if (expectedProto.isPresent()) {
			Assert.assertEquals(
					"Wrong HAPI proto version",
					expectedProto.get(),
					actualProto);
		}
		if (expectedServices.isPresent()) {
			Assert.assertEquals(
					"Wrong Hedera Services version",
					expectedServices.get(),
					actualServices);
		}
	}

	@Override
	protected void submitWith(HapiApiSpec spec, Transaction payment) {
		Query query = getVersionInfoQuery(spec, payment, false);
		response = spec.clients().getNetworkSvcStub(targetNodeFor(spec), useTls).getVersionInfo(query);
		var info = response.getNetworkGetVersionInfo();
		if (verboseLoggingOn) {
			log.info("Versions :: " +
					String.format("HAPI Proto @ %d.%d.%d, ",
							info.getHapiProtoVersion().getMajor(),
							info.getHapiProtoVersion().getMinor(),
							info.getHapiProtoVersion().getPatch()) +
					String.format("Heder Services @ %d.%d.%d, ",
							info.getHederaServicesVersion().getMajor(),
							info.getHederaServicesVersion().getMinor(),
							info.getHederaServicesVersion().getPatch()));
		}
	}

	@Override
	protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getVersionInfoQuery(spec, payment, true);
		Response response = spec.clients().getNetworkSvcStub(targetNodeFor(spec), useTls).getVersionInfo(query);
		return costFrom(response);
	}

	private Query getVersionInfoQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
		NetworkGetVersionInfoQuery getVersionQuery = NetworkGetVersionInfoQuery.newBuilder()
				.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
				.build();
		return Query.newBuilder().setNetworkGetVersionInfo(getVersionQuery).build();
	}

	@Override
	protected boolean needsPayment() {
		return true;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		return MoreObjects.toStringHelper(this);
	}
}
