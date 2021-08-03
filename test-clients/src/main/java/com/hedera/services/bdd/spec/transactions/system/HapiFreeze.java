package com.hedera.services.bdd.spec.transactions.system;

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
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.asFileId;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;

public class HapiFreeze extends HapiTxnOp<HapiFreeze> {
	private final static int DELAY_DEFAULT = 5;
	private final static int DURATION_DEFAULT = 30;
	boolean settingDelayUnits = true;
	ChronoUnit delayUnit = SECONDS;
	ChronoUnit durationUnit = SECONDS;

	private Optional<Integer> delay = Optional.empty();
	private Optional<Integer> duration = Optional.empty();
	ZonedDateTime start, end;
	private Optional<Instant> freezeStartTime = Optional.empty();
	private String fileID = null;
	private String fileName = null;
	private Optional<byte[]> fileHash = Optional.empty();
	@Override
	protected HapiFreeze self() {
		return this;
	}

	public HapiFreeze startAt(Instant startTime) {
		freezeStartTime = Optional.of(startTime);
		return this;
	}

	public HapiFreeze startingIn(int n) {
		delay = Optional.of(n);
		settingDelayUnits = true;
		return this;
	}

	public HapiFreeze andLasting(int n) {
		duration = Optional.of(n);
		settingDelayUnits =false;
		return this;
	}
	public HapiFreeze seconds() {
		if (settingDelayUnits) {
			this.delayUnit = SECONDS;
		} else {
			this.durationUnit = SECONDS;
		}
		return this;
	}
	public HapiFreeze minutes() {
		if (settingDelayUnits) {
			this.delayUnit = MINUTES;
		} else {
			this.durationUnit = MINUTES;
		}
		return this;
	}

	public HapiFreeze setFileName(String fileName) {
		this.fileName = fileName;
		return this;
	}

	public HapiFreeze setFileID(String fileID) {
		this.fileID = fileID;
		return this;
	}

	public HapiFreeze setFileHash(byte[] data) {
		fileHash = Optional.of(data);
		return this;
	}
	public HapiFreeze setFileHash(String data) {
		fileHash = Optional.of(data.getBytes());
		return this;
	}

	@Override
	public HederaFunctionality type() {
		return Freeze;
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(final HapiApiSpec spec) throws Throwable {
		FreezeTransactionBody opBody  = FreezeTransactionBody.getDefaultInstance();
		if(delay.isPresent() || duration.isPresent()) {
			if(delay.isPresent() && duration.isEmpty()) {
				duration = Optional.of(DURATION_DEFAULT);
			}
			else {
				delay = Optional.of(DELAY_DEFAULT);
			}
			Instant moment = Instant.now().plus(delay.get(), delayUnit);
			start = ZonedDateTime.ofInstant(moment, ZoneId.of("GMT"));
			end = ZonedDateTime.ofInstant(moment.plus(duration.get(), durationUnit), ZoneId.of("GMT"));
			opBody = spec
					.txns()
					.<FreezeTransactionBody, FreezeTransactionBody.Builder>body(
							FreezeTransactionBody.class, b -> {
								b.setStartHour(start.getHour());
								b.setStartMin(start.getMinute());
								b.setEndHour(end.getHour());
								b.setEndMin(end.getMinute());
								fillRestOfTxnBody(b, spec);
							}
					);
		}
		else if(freezeStartTime.isPresent()) {
			Instant startTime = freezeStartTime.get();
			opBody = spec.txns()
					.<FreezeTransactionBody, FreezeTransactionBody.Builder>body(
							FreezeTransactionBody.class, b -> {
								b.setStartTime(Timestamp.newBuilder().setSeconds(startTime.getEpochSecond())
										.setNanos(startTime.getNano()));
								fillRestOfTxnBody(b, spec);
							}
					);
		}
		final FreezeTransactionBody txnBody = opBody;
		return b -> b.setFreeze(txnBody);
	}

	private void fillRestOfTxnBody(FreezeTransactionBody.Builder b, HapiApiSpec spec) {
		if (fileID!=null) {
			b.setUpdateFile(asFileId(fileID, spec));
		}
		if (fileName !=null){
			FileID foundID = spec.registry().getFileId(fileName);
			b.setUpdateFile(foundID);
		}
		fileHash.ifPresent(x -> b.setFileHash(ByteString.copyFrom(x)));
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getFreezeSvcStub(targetNodeFor(spec), useTls)::freeze;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) {
		return spec.fees().maxFeeTinyBars();
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper();
		if (start != null && end != null) {
				helper.add("start", String.format("%d:%d", start.getHour(), start.getMinute()))
						.add("end", String.format("%d:%d", end.getHour(), end.getMinute()));
		}
		return helper;
	}
}
