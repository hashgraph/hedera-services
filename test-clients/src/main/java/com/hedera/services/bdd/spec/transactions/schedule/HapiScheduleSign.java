package com.hedera.services.bdd.spec.transactions.schedule;

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
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.infrastructure.RegistryNotFound;
import com.hedera.services.bdd.spec.keys.SigMapGenerator;
import com.hedera.services.bdd.spec.queries.schedule.HapiGetScheduleInfo;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.usage.schedule.ScheduleSignUsage;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleInfo;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.withNature;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asScheduleId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleCreate.correspondingScheduledTxnId;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.stream.Collectors.toList;

public class HapiScheduleSign extends HapiTxnOp<HapiScheduleSign> {
	private static final Logger log = LogManager.getLogger(HapiScheduleSign.class);

	private boolean lookupBytesToSign = false;
	private final String schedule;
	private List<String> signatories = Collections.emptyList();
	private Optional<String> savedScheduledTxnId = Optional.empty();
	private Optional<byte[]> explicitBytes = Optional.empty();

	public HapiScheduleSign(String schedule) {
		this.schedule = schedule;
	}

	public HapiScheduleSign lookingUpBytesToSign() {
		lookupBytesToSign = true;
		return this;
	}

	public HapiScheduleSign withSignatories(String... keys)	 {
		signatories = List.of(keys);
		return this;
	}

	public HapiScheduleSign signingExplicit(byte[] bytes) {
		explicitBytes = Optional.of(bytes);
		return this;
	}

	public HapiScheduleSign receiptHasScheduledTxnId(String creation) {
		savedScheduledTxnId = Optional.of(correspondingScheduledTxnId(creation));
		return this;
	}

	@Override
	protected HapiScheduleSign self() {
		return this;
	}

	@Override
	public HederaFunctionality type() {
		return ScheduleSign;
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		var registry = spec.registry();
		byte[] bytesToSign;

		if (explicitBytes.isPresent()) {
			bytesToSign = explicitBytes.get();
		} else {
			if (lookupBytesToSign) {
				var subOp = getScheduleInfo(schedule)
						.hasCostAnswerPrecheckFrom(OK, ResponseCodeEnum.INVALID_SCHEDULE_ID);
				allRunFor(spec, subOp);
				if (subOp.getResponse() == null) {
					bytesToSign = new byte[] { };
				} else {
					var info = subOp.getResponse().getScheduleGetInfo().getScheduleInfo();
					bytesToSign = info.getTransactionBody().toByteArray();
					if (verboseLoggingOn) {
						log.info("Found transaction to sign: {}", TransactionBody.parseFrom(bytesToSign));
					} else {
						log.info("Found {} bytes to sign", bytesToSign.length);
					}
				}
			} else {
				try {
					bytesToSign = registry.getBytes(HapiScheduleCreate.registryBytesTag(schedule));
				} catch (RegistryNotFound rnf) {
					bytesToSign = new byte[] {};
				}
			}
		}

		var signingKeys = signatories.stream().map(k -> registry.getKey(k)).collect(toList());
		var authors = spec.keys().authorsFor(signingKeys, Collections.emptyMap());
		var ceremony = spec.keys().new Ed25519Signing(bytesToSign, authors);
		var sigs = ceremony.completed();
		ScheduleSignTransactionBody opBody = spec
				.txns()
				.<ScheduleSignTransactionBody, ScheduleSignTransactionBody.Builder>body(
						ScheduleSignTransactionBody.class, b -> {
							b.setScheduleID(asScheduleId(schedule, spec));
							b.setSigMap(withNature(SigMapGenerator.Nature.UNIQUE).forEd25519Sigs(sigs));
						}
				);
		return b -> b.setScheduleSign(opBody);
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getScheduleSvcStub(targetNodeFor(spec), useTls)::signSchedule;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		try {
			final ScheduleInfo info = lookupInfo(spec);
			FeeCalculator.ActivityMetrics metricsCalc = (_txn, svo) ->
					ScheduleSignUsage.newEstimate(_txn, suFrom(svo))
							.givenExpiry(info.getExpirationTime().getSeconds()).get();
			return spec.fees().forActivityBasedOp(
					HederaFunctionality.ScheduleSign, metricsCalc, txn, numPayerKeys);
		} catch (Throwable ignore) {
			return 100_000_000L;
		}
	}

	private ScheduleInfo lookupInfo(HapiApiSpec spec) throws Throwable {
		HapiGetScheduleInfo subOp = getScheduleInfo(schedule).noLogging();
		Optional<Throwable> error = subOp.execFor(spec);
		if (error.isPresent()) {
			if (!loggingOff) {
				log.warn(
						"Unable to look up current info for "
								+ HapiPropertySource.asScheduleString(spec.registry().getScheduleId(schedule)),
						error.get());
			}
			throw error.get();
		}
		return subOp.getResponse().getScheduleGetInfo().getScheduleInfo();
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("schedule", schedule);
		return helper;
	}
}
