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
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.TrieSigMapGenerator;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.usage.schedule.ScheduleCreateUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiPropertySource.asScheduleString;
import static com.hedera.services.bdd.spec.keys.SigMapGenerator.Nature.UNIQUE;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.stream.Collectors.toList;

public class HapiScheduleCreate<T extends HapiTxnOp<T>> extends HapiTxnOp<HapiScheduleCreate<T>> {
	private static final Logger log = LogManager.getLogger(HapiScheduleCreate.class);

	private static final int defaultScheduleTxnExpiry = HapiSpecSetup.getDefaultNodeProps()
			.getInteger("ledger.schedule.txExpiryTimeSecs");

	private boolean scheduleNonsense = false;
	private boolean skipRegistryUpdate = false;
	private boolean scheduleNoFunction = false;
	private boolean saveExpectedScheduledTxnId = false;
	private ByteString bytesSigned = ByteString.EMPTY;
	private List<String> initialSigners = Collections.emptyList();
	private final String entity;
	private final HapiTxnOp<T> scheduled;
	private Optional<String> adminKey = Optional.empty();
	private Optional<String> payerAccountID = Optional.empty();
	private Optional<String> entityMemo = Optional.empty();
	private Optional<BiConsumer<String, byte[]>> successCb = Optional.empty();

	public HapiScheduleCreate(String scheduled, HapiTxnOp<T> txn) {
		this.entity = scheduled;
		this.scheduled = txn
				.withLegacyProtoStructure()
				.sansTxnId()
				.sansNodeAccount()
				.signedBy();
	}

	public HapiScheduleCreate<T> savingExpectedScheduledTxnId() {
		saveExpectedScheduledTxnId = true;
		return this;
	}

	public HapiScheduleCreate<T> rememberingNothing() {
		skipRegistryUpdate = true;
		return this;
	}

	public HapiScheduleCreate<T> garbled() {
		scheduleNonsense = true;
		return this;
	}

	public HapiScheduleCreate<T> functionless() {
		scheduleNoFunction = true;
		return this;
	}

	public HapiScheduleCreate<T> adminKey(String s) {
		adminKey = Optional.of(s);
		return this;
	}

	public HapiScheduleCreate<T> exposingSuccessTo(BiConsumer<String, byte[]> cb) {
		successCb = Optional.of(cb);
		return this;
	}

	public HapiScheduleCreate<T> designatingPayer(String s) {
		payerAccountID = Optional.of(s);
		return this;
	}

	public HapiScheduleCreate<T> alsoSigningWith(String... s) {
		initialSigners = List.of(s);
		return this;
	}

	public HapiScheduleCreate<T> withEntityMemo(String entityMemo) {
		this.entityMemo = Optional.of(entityMemo);
		return this;
	}

	@Override
	protected HapiScheduleCreate<T> self() {
		return this;
	}

	@Override
	public HederaFunctionality type() {
		return ScheduleCreate;
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		var subOp = scheduled.signedTxnFor(spec);

		ScheduleCreateTransactionBody opBody = spec
				.txns()
				.<ScheduleCreateTransactionBody, ScheduleCreateTransactionBody.Builder>body(
						ScheduleCreateTransactionBody.class, b -> {
							if (scheduleNonsense) {
								b.setTransactionBody(ByteString.copyFromUtf8("NONSENSE"));
							} else if (scheduleNoFunction) {
								b.setTransactionBody(ByteString.copyFrom(
										TransactionBody.getDefaultInstance().toByteArray()));
							} else {
								b.setTransactionBody(subOp.getBodyBytes());
							}
							adminKey.ifPresent(k -> b.setAdminKey(spec.registry().getKey(k)));
							entityMemo.ifPresent(b::setMemo);
							payerAccountID.ifPresent(a -> {
								var payer = TxnUtils.asId(a, spec);
								b.setPayerAccountID(payer);
							});
						}
				);
		return b -> b.setScheduleCreate(opBody);
	}


	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getScheduleSvcStub(targetNodeFor(spec), useTls)::createSchedule;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.ScheduleCreate, this::usageEstimate, txn, numPayerKeys);
	}

	private FeeData usageEstimate(TransactionBody txn, SigValueObj svo) {
		return ScheduleCreateUsage.newEstimate(txn, suFrom(svo))
				.givenScheduledTxExpirationTimeSecs(defaultScheduleTxnExpiry)
				.get();
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("entity", entity);
		helper.add("id", createdSchedule().orElse("<N/A>"));
		return helper;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) throws Throwable {
		if (actualStatus != SUCCESS) {
			return;
		}
		if (verboseLoggingOn) {
			log.info("Created schedule '{}' as {}", entity, createdSchedule().get());
		}
		successCb.ifPresent(cb -> cb.accept(
				asScheduleString(lastReceipt.getScheduleID()),
				bytesSigned.toByteArray()));
		if (skipRegistryUpdate) {
			return;
		}
		var registry = spec.registry();
		registry.saveScheduleId(entity, lastReceipt.getScheduleID());
		registry.saveExpiry(entity, (long)defaultScheduleTxnExpiry);
		adminKey.ifPresent(k -> registry.saveAdminKey(entity, spec.registry().getKey(k)));
		if (saveExpectedScheduledTxnId) {
			if (verboseLoggingOn) {
				log.info("Returned receipt for scheduled txn is {}", lastReceipt.getScheduledTransactionID());
			}
			registry.saveTxnId(correspondingScheduledTxnId(entity), lastReceipt.getScheduledTransactionID());
		}
	}

	public static String correspondingScheduledTxnId(String entity) {
		return entity + "ScheduledTxnId";
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		List<Function<HapiApiSpec, Key>> signers =
				new ArrayList<>(List.of(spec -> spec.registry().getKey(effectivePayer(spec))));
		adminKey.ifPresent(k -> signers.add(spec -> spec.registry().getKey(k)));
		for (String added : initialSigners) {
			signers.add(spec -> spec.registry().getKey(added));
		}
		return signers;
	}

	private Optional<String> createdSchedule() {
		return Optional
				.ofNullable(lastReceipt)
				.map(receipt -> asScheduleString(receipt.getScheduleID()));
	}
}
