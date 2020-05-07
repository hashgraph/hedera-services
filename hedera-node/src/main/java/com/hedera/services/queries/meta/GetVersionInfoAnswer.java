package com.hedera.services.queries.meta;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.queries.AbstractAnswer;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.NetworkGetVersionInfoResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetVersionInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

public class GetVersionInfoAnswer extends AbstractAnswer {
	private static final Logger log = LogManager.getLogger(GetVersionInfoAnswer.class);

	static AtomicReference<ActiveVersions> knownActive = new AtomicReference<>(null);

	public GetVersionInfoAnswer(final PropertySource properties) {
		super(
				GetVersionInfo,
				query -> query.getNetworkGetVersionInfo().getHeader().getPayment(),
				query -> query.getNetworkGetVersionInfo().getHeader().getResponseType(),
				response -> response.getNetworkGetVersionInfo().getHeader().getNodeTransactionPrecheckCode(),
				(query, view) -> invariantValidityCheck(properties));
	}

	@Override
	public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
		var op = query.getNetworkGetVersionInfo();
		var response = NetworkGetVersionInfoResponse.newBuilder();

		ResponseType type = op.getHeader().getResponseType();
		if (validity != OK) {
			response.setHeader(header(validity, type, cost));
		} else {
			if (type == COST_ANSWER) {
				response.setHeader(costAnswerHeader(OK, cost));
			} else {
				response.setHeader(answerOnlyHeader(OK));
				response.setHapiProtoVersion(knownActive.get().protoSemVer());
				response.setHederaServicesVersion(knownActive.get().hederaSemVer());
			}
		}

		return Response.newBuilder()
				.setNetworkGetVersionInfo(response)
				.build();
	}

	static ResponseCodeEnum invariantValidityCheck(PropertySource properties) {
		Optional<ActiveVersions> active = Optional.ofNullable(knownActive.get())
				.or(() -> Optional.ofNullable(
						fromResource(
								properties.getStringProperty("hedera.versionInfo.resource"),
								properties.getStringProperty("hedera.versionInfo.protoKey"),
								properties.getStringProperty("hedera.versionInfo.servicesKey"))));
		return active.map(any -> OK).orElse(FAIL_INVALID);
	}

	private static ActiveVersions fromResource(String propertiesFile, String protoKey, String servicesKey) {
		try (InputStream in = GetVersionInfoAnswer.class.getClassLoader().getResourceAsStream(propertiesFile)) {
			var props = new Properties();
			props.load(in);
			log.info("Discovered semantic versions {} from resource '{}'", props, propertiesFile);
			knownActive.set(new ActiveVersions(
					asSemVer((String)props.get(protoKey)),
					asSemVer((String)props.get(servicesKey))));
		} catch (Exception surprising) {
			log.warn(
					"Failed to read versions from resource '{}' (keys '{}' and '{}')",
					propertiesFile,
					protoKey,
					servicesKey,
					surprising);
		}
		return knownActive.get();
	}

	private static SemanticVersion asSemVer(String value) {
		long[] parts = EntityIdUtils.asDotDelimitedLongArray(value);
		return SemanticVersion.newBuilder()
				.setMajor((int)parts[0])
				.setMinor((int)parts[1])
				.setPatch((int)parts[2])
				.build();
	}

	public static class ActiveVersions {
		private final SemanticVersion proto;
		private final SemanticVersion services;

		public ActiveVersions(SemanticVersion proto, SemanticVersion services) {
			this.proto = proto;
			this.services = services;
		}

		public SemanticVersion protoSemVer() {
			return proto;
		}

		public SemanticVersion hederaSemVer() {
			return services;
		}
	}
}
