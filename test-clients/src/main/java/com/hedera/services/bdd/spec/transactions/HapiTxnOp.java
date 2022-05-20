package com.hedera.services.bdd.spec.transactions;

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

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.exceptions.HapiTxnCheckStateException;
import com.hedera.services.bdd.spec.exceptions.HapiTxnPrecheckStateException;
import com.hedera.services.bdd.spec.fees.Payment;
import com.hedera.services.bdd.spec.infrastructure.DelegatingOpFinisher;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.KeyGenerator;
import com.hedera.services.bdd.spec.keys.OverlappingKeyGenerator;
import com.hedera.services.bdd.spec.keys.SigMapGenerator;
import com.hedera.services.bdd.spec.stats.QueryObs;
import com.hedera.services.bdd.spec.stats.TxnObs;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import io.grpc.StatusRuntimeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.fees.Payment.Reason.TXN_FEE;
import static com.hedera.services.bdd.spec.keys.DefaultKeyGen.DEFAULT_KEY_GEN;
import static com.hedera.services.bdd.spec.queries.QueryUtils.txnReceiptQueryFor;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.extractTxnId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.txnToString;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetReceipt;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static java.lang.Thread.sleep;
import static java.util.stream.Collectors.toList;

public abstract class HapiTxnOp<T extends HapiTxnOp<T>> extends HapiSpecOperation {
	private static final Logger log = LogManager.getLogger(HapiTxnOp.class);

	private static final Response UNKNOWN_RESPONSE = Response.newBuilder()
			.setTransactionGetReceipt(TransactionGetReceiptResponse.newBuilder()
					.setReceipt(TransactionReceipt.newBuilder()
							.setStatus(UNKNOWN)))
			.build();

	private long submitTime = 0L;
	private TxnObs stats;
	private boolean ensureResolvedStatusIsntFromDuplicate = false;
	private final TupleType LONG_TUPLE = TupleType.parse("(int64)");

	protected boolean deferStatusResolution = false;
	protected boolean acceptAnyStatus = false;
	protected boolean acceptAnyPrecheck = false;
	protected boolean acceptAnyKnownStatus = false;
	protected ResponseCodeEnum actualStatus = UNKNOWN;
	protected ResponseCodeEnum actualPrecheck = UNKNOWN;
	protected TransactionReceipt lastReceipt;

	protected Optional<Function<Transaction, Transaction>> fiddler = Optional.empty();

	protected Optional<ResponseCodeEnum> expectedStatus = Optional.empty();
	protected Optional<ResponseCodeEnum> expectedPrecheck = Optional.empty();
	protected Optional<KeyGenerator.Nature> keyGen = Optional.empty();
	protected Optional<EnumSet<ResponseCodeEnum>> permissibleStatuses = Optional.empty();
	protected Optional<EnumSet<ResponseCodeEnum>> permissiblePrechecks = Optional.empty();
	/** if response code in the set then allow to resubmit transaction */
	protected Optional<EnumSet<ResponseCodeEnum>> retryPrechecks = Optional.empty();

	public long getSubmitTime() {
		return submitTime;
	}

	public ResponseCodeEnum getExpectedStatus() {
		return expectedStatus.orElse(SUCCESS);
	}

	public ResponseCodeEnum getExpectedPrecheck() {
		return expectedPrecheck.orElse(OK);
	}

	abstract protected T self();

	abstract protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable;

	abstract protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec);

	public byte[] serializeSignedTxnFor(HapiApiSpec spec) throws Throwable {
		return finalizedTxn(spec, opBodyDef(spec)).toByteArray();
	}

	public Transaction signedTxnFor(HapiApiSpec spec) throws Throwable {
		return finalizedTxn(spec, opBodyDef(spec));
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		stats = new TxnObs(type());
		fixNodeFor(spec);
		configureTlsFor(spec);

		int retryCount = 1;
		while (true) {
			Transaction txn = finalizedTxn(spec, opBodyDef(spec));

			if (!loggingOff) {
				log.info(spec.logPrefix() + " submitting " + this + " via " + txnToString(txn));
			}

			TransactionResponse response = null;
			try {
				if (fiddler.isPresent()) {
					txn = fiddler.get().apply(txn);
				}
				response = timedCall(spec, txn);
			} catch (StatusRuntimeException e) {
				var msg = e.toString();
				if (isRecognizedRecoverable(msg)) {
					log.info("Recognized recoverable runtime exception {}, retrying status resolution...", msg);
					continue;
				} else {
					if (spec.setup().suppressUnrecoverableNetworkFailures()) {
						return false;
					}
					log.error("{} Status resolution failed due to unrecoverable runtime exception, " +
							"possibly network connection lost.", TxnUtils.toReadableString(txn));
					throw new HapiTxnCheckStateException("Unable to resolve txn status!");
				}
			}

			/* Used by superclass to perform standard housekeeping. */
			txnSubmitted = txn;

			actualPrecheck = response.getNodeTransactionPrecheckCode();
			if (
					retryPrechecks.isPresent() &&
							retryPrechecks.get().contains(actualPrecheck) &&
							isWithInRetryLimit(retryCount)
			) {
				retryCount++;
				sleep(10);
			} else {
				break;
			}
		}
		spec.updatePrecheckCounts(actualPrecheck);
		stats.setAccepted(actualPrecheck == OK);
		if (actualPrecheck == INSUFFICIENT_PAYER_BALANCE || actualPrecheck == INSUFFICIENT_TX_FEE) {
			if (payerIsRechargingFor(spec)) {
				addIpbToPermissiblePrechecks();
				if (payerNotRecentlyRecharged(spec)) {
					rechargePayerFor(spec);
				}
			}
		}
		if (!acceptAnyPrecheck) {
			if (permissiblePrechecks.isPresent()) {
				if (permissiblePrechecks.get().contains(actualPrecheck)) {
					expectedPrecheck = Optional.of(actualPrecheck);
				} else {
//					log.error(
//							"{} {} Wrong actual precheck status {}, not one of {}!",spec.logPrefix(), this,
//							actualPrecheck,
//							permissiblePrechecks.get());
					throw new HapiTxnPrecheckStateException(String.format(
							"Wrong precheck status! Expected one of %s, actual %s",
							permissiblePrechecks.get(), actualPrecheck));
				}
			} else {
				if (getExpectedPrecheck() != actualPrecheck) {
					// Change to an info until HapiClientValidator can be modified and can understand new errors
					log.info("{} {} Wrong actual precheck status {}, expecting {}", spec.logPrefix(), this,
							actualPrecheck, getExpectedPrecheck());
					throw new HapiTxnPrecheckStateException(String.format(
							"Wrong precheck status! Expected %s, actual %s",
							getExpectedPrecheck(), actualPrecheck));
				}
			}
		}
		if (actualPrecheck != OK) {
			considerRecording(spec, stats);
			return false;
		}
		spec.adhocIncrement();

		if (!deferStatusResolution) {
			resolveStatus(spec);
			if (!hasStatsToCollectDuringFinalization(spec)) {
				considerRecording(spec, stats);
			}
		}
		if (requiresFinalization(spec)) {
			spec.offerFinisher(new DelegatingOpFinisher(this));
		}

		return !deferStatusResolution;
	}

	private TransactionResponse timedCall(HapiApiSpec spec, Transaction txn) {
		submitTime = System.currentTimeMillis();
		TransactionResponse response = callToUse(spec).apply(txn);
		long after = System.currentTimeMillis();
		stats.setResponseLatency(after - submitTime);
		return response;
	}

	private void resolveStatus(HapiApiSpec spec) throws Throwable {
		actualStatus = resolvedStatusOfSubmission(spec);
		spec.updateResolvedCounts(actualStatus);
		if (actualStatus == INSUFFICIENT_PAYER_BALANCE) {
			if (payerIsRechargingFor(spec)) {
				addIpbToPermissibleStatuses();
				if (payerNotRecentlyRecharged(spec)) {
					rechargePayerFor(spec);
				}
			}
		}
		if (permissibleStatuses.isPresent()) {
			if (permissibleStatuses.get().contains(actualStatus)) {
				expectedStatus = Optional.of(actualStatus);
			} else {
				log.error(
						"{} {} Wrong actual status {}, not one of {}!", spec.logPrefix(), this,
						actualStatus,
						permissibleStatuses.get());
				throw new HapiTxnCheckStateException(String.format(
						"Wrong status! Expected one of %s, was %s",
						permissibleStatuses.get(), actualStatus));
			}
		} else {
			if (getExpectedStatus() != actualStatus) {
				// Change to an info until HapiClientValidator can be modified and can understand new errors
				log.info("{} {} Wrong actual status {}, expected {}", spec.logPrefix(), this, actualStatus,
						getExpectedStatus());
				throw new HapiTxnCheckStateException(String.format(
						"Wrong status! Expected %s, was %s",
						getExpectedStatus(), actualStatus));
			}
		}
		if (!deferStatusResolution) {
			if (spec.setup().costSnapshotMode() != HapiApiSpec.CostSnapshotMode.OFF) {
				publishFeeChargedTo(spec);
			}
		}
		if (ensureResolvedStatusIsntFromDuplicate) {
			assertRecordHasExpectedMemo(spec);
		}
	}

	private void rechargePayerFor(HapiApiSpec spec) {
		long rechargeAmount = spec.registry().getRechargeAmount(payer.get());
		var bank = spec.setup().defaultPayerName();
		spec.registry().setRechargingTime(payer.get(), Instant.now()); //record timestamp of last recharge event
		allRunFor(spec, cryptoTransfer(tinyBarsFromTo(bank, payer.get(), rechargeAmount)));
	}

	private boolean payerIsRechargingFor(HapiApiSpec spec) {
		return payer.map(spec.registry()::isRecharging).orElse(Boolean.FALSE);
	}

	synchronized private boolean payerNotRecentlyRecharged(HapiApiSpec spec) {
		Instant lastInstant = payer.map(spec.registry()::getRechargingTime).orElse(Instant.MIN);
		Integer rechargeWindow = payer.map(spec.registry()::getRechargingWindow).orElse(0);
		return !lastInstant.plusSeconds(rechargeWindow).isAfter(Instant.now());
	}

	private void addIpbToPermissiblePrechecks() {
		if (permissiblePrechecks.isEmpty()) {
			permissiblePrechecks = Optional.of(
					EnumSet.copyOf(List.of(OK, INSUFFICIENT_PAYER_BALANCE, INSUFFICIENT_TX_FEE)));
		} else if (!permissiblePrechecks.get().contains(INSUFFICIENT_PAYER_BALANCE) ||
				!permissiblePrechecks.get().contains(INSUFFICIENT_TX_FEE)) {
			permissiblePrechecks = Optional.of(addIpbToleranceTo(permissiblePrechecks.get()));
		}
	}

	private void addIpbToPermissibleStatuses() {
		if (permissibleStatuses.isEmpty()) {
			permissibleStatuses = Optional.of(EnumSet.copyOf(List.of(SUCCESS, INSUFFICIENT_PAYER_BALANCE)));
		} else if (!permissibleStatuses.get().contains(INSUFFICIENT_PAYER_BALANCE)) {
			permissibleStatuses = Optional.of(addIpbToleranceTo(permissibleStatuses.get()));
		}
	}

	private EnumSet<ResponseCodeEnum> addIpbToleranceTo(EnumSet<ResponseCodeEnum> immutableSet) {
		List<ResponseCodeEnum> tolerating = new ArrayList<>(immutableSet);
		tolerating.add(INSUFFICIENT_PAYER_BALANCE);
		return EnumSet.copyOf(tolerating);
	}

	private void publishFeeChargedTo(HapiApiSpec spec) throws Throwable {
		if (recordOfSubmission == null) {
			lookupSubmissionRecord(spec);
		}
		long fee = recordOfSubmission.getTransactionFee();
		spec.recordPayment(new Payment(fee, self().getClass().getSimpleName(), TXN_FEE));
	}

	private void assertRecordHasExpectedMemo(HapiApiSpec spec) throws Throwable {
		if (recordOfSubmission == null) {
			lookupSubmissionRecord(spec);
		}
		if (!memo.get().equals(recordOfSubmission.getMemo())) {
			/*
			When different clients submit a transaction with same transaction IDs and different memos, They are
			treated as
			Duplicate transactions and except for the client that gets its transaction handled first.. rest of the
			clients
			will submit a similar transaction again, with new transaction IDs. No need to throw an exception. 
			 */
			log.warn("{} {} Memo didn't come from submitted transaction! actual memo {}, recorded {}.",
					spec.logPrefix(), this, memo.get(), recordOfSubmission.getMemo());
			throw new IllegalStateException("Resolved submission record was from a duplicate");
		}
	}

	@Override
	public boolean requiresFinalization(HapiApiSpec spec) {
		return (actualPrecheck == OK) && (deferStatusResolution || hasStatsToCollectDuringFinalization(spec));
	}

	private boolean hasStatsToCollectDuringFinalization(HapiApiSpec spec) {
		return (!suppressStats && spec.setup().measureConsensusLatency());
	}

	@Override
	protected void lookupSubmissionRecord(HapiApiSpec spec) throws Throwable {
		if (actualStatus == UNKNOWN) {
			throw new HapiTxnCheckStateException(
					this + " tried to lookup the submission record before status was known!");
		}
		super.lookupSubmissionRecord(spec);
	}

	@Override
	public void finalizeExecFor(HapiApiSpec spec) throws Throwable {
		boolean explicitStatSuppression = suppressStats;
		suppressStats = true;
		if (deferStatusResolution) {
			resolveStatus(spec);
			updateStateOf(spec);
		}
		if (explicitStatSuppression) {
			return;
		} else {
			if (spec.setup().measureConsensusLatency()) {
				measureConsensusLatency(spec);
			}
			spec.registry().record(stats);
		}
	}

	private void measureConsensusLatency(HapiApiSpec spec) throws Throwable {
		if (acceptAnyStatus) {
			acceptAnyStatus = false;
			acceptAnyKnownStatus = true;
			actualStatus = resolvedStatusOfSubmission(spec);
		}
		if (recordOfSubmission == null) {
			lookupSubmissionRecord(spec);
		}
		Timestamp stamp = recordOfSubmission.getConsensusTimestamp();
		long consensusTime = stamp.getSeconds() * 1_000L + stamp.getNanos() / 1_000_000L;
		stats.setConsensusLatency(consensusTime - submitTime);
	}

	private ResponseCodeEnum resolvedStatusOfSubmission(HapiApiSpec spec) throws Throwable {
		long delayMS = spec.setup().statusPreResolvePauseMs();
		long elapsedMS = System.currentTimeMillis() - submitTime;
		if (elapsedMS <= delayMS) {
			pause(delayMS - elapsedMS);
		}
		long beginWait = Instant.now().toEpochMilli();
		Query receiptQuery = txnReceiptQueryFor(extractTxnId(txnSubmitted));
		do {
			Response response = statusResponse(spec, receiptQuery);
			lastReceipt = response.getTransactionGetReceipt().getReceipt();
			ResponseCodeEnum statusNow = lastReceipt.getStatus();
			if (acceptAnyStatus) {
				expectedStatus = Optional.of(statusNow);
				return statusNow;
			} else if (statusNow != UNKNOWN) {
				if (acceptAnyKnownStatus) {
					expectedStatus = Optional.of(statusNow);
				}
				return statusNow;
			}
			pause(spec.setup().statusWaitSleepMs());
		} while ((Instant.now().toEpochMilli() - beginWait) < spec.setup().statusWaitTimeoutMs());
		return UNKNOWN;
	}

	private Response statusResponse(HapiApiSpec spec, Query receiptQuery) throws Throwable {
		long before = System.currentTimeMillis();
		Response response = null;
		int allowedUnrecognizedExceptions = 10;
		while (response == null) {
			try {
				var cryptoSvcStub = spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls);
				if (cryptoSvcStub == null) {
					response = UNKNOWN_RESPONSE;
				} else {
					response = cryptoSvcStub.getTransactionReceipts(receiptQuery);
				}
			} catch (Exception e) {
				var msg = e.toString();
				if (isRecognizedRecoverable(msg)) {
					log.info("Recognized recoverable runtime exception {}, retrying status resolution...", msg);
					continue;
				}
				log.warn("({}) Status resolution failed with unrecognized exception",
						Thread.currentThread().getName(), e);
				allowedUnrecognizedExceptions--;
				if (allowedUnrecognizedExceptions == 0) {
					response = UNKNOWN_RESPONSE;
				}
			}
		}
		long after = System.currentTimeMillis();
		considerRecordingAdHocReceiptQueryStats(spec.registry(), after - before);
		return response;
	}

	private boolean isRecognizedRecoverable(String msg) {
		return msg.contains("NO_ERROR") ||
				msg.contains("Received unexpected EOS on DATA frame from server") ||
				msg.contains("REFUSED_STREAM");
	}

	private void considerRecordingAdHocReceiptQueryStats(HapiSpecRegistry registry, long responseLatency) {
		if (!suppressStats && !deferStatusResolution) {
			QueryObs adhocStats = new QueryObs(ANSWER_ONLY, TransactionGetReceipt);
			adhocStats.setAccepted(true);
			adhocStats.setResponseLatency(responseLatency);
			registry.record(adhocStats);
		}
	}

	private void pause(long forMs) {
		if (forMs > 0L) {
			try {
				sleep(forMs);
			} catch (InterruptedException ignore) {
			}
		}
	}

	protected KeyGenerator effectiveKeyGen() {
		return (keyGen.orElse(KeyGenerator.Nature.RANDOMIZED) == KeyGenerator.Nature.WITH_OVERLAPPING_PREFIXES)
				? OverlappingKeyGenerator.withDefaultOverlaps()
				: DEFAULT_KEY_GEN;
	}

	protected byte[] gasLongToBytes(final Long gas) {
		return Bytes.wrap(LONG_TUPLE.encode(Tuple.of(gas)).array()).toArray();
	}

	/* Fluent builder methods to chain. */
	public T blankMemo() {
		memo = Optional.of("");
		return self();
	}

	public T memo(String text) {
		memo = Optional.of(text);
		return self();
	}

	public T ensuringResolvedStatusIsntFromDuplicate() {
		ensureResolvedStatusIsntFromDuplicate = true;
		memo = Optional.of(TxnUtils.randomUppercase(64));
		return self();
	}

	public T logged() {
		verboseLoggingOn = true;
		return self();
	}

	public T setRetryLimit(int limit) {
		retryLimits = Optional.of(limit);
		return self();
	}

	public T via(String name) {
		txnName = name;
		shouldRegisterTxn = true;
		return self();
	}

	public T fee(long amount) {
		if (amount >= 0) {
			fee = Optional.of(amount);
		}
		return self();
	}

	public T feeUsd(double price) {
		usdFee = OptionalDouble.of(price);
		return self();
	}

	public T signedBy(String... keys) {
		signers = Optional.of(
				Stream.of(keys)
						.<Function<HapiApiSpec, Key>>map(k -> spec -> spec.registry().getKey(k))
						.collect(toList()));
		return self();
	}

	public T payingWith(String name) {
		payer = Optional.of(name);
		return self();
	}

	public T record(Boolean isGenerated) {
		genRecord = Optional.of(isGenerated);
		return self();
	}

	public T hasPrecheck(ResponseCodeEnum status) {
		expectedPrecheck = Optional.of(status);
		return self();
	}

	public T hasPrecheckFrom(ResponseCodeEnum... statuses) {
		permissiblePrechecks = Optional.of(EnumSet.copyOf(List.of(statuses)));
		return self();
	}

	public T hasRetryPrecheckFrom(ResponseCodeEnum... statuses) {
		retryPrechecks = Optional.of(EnumSet.copyOf(List.of(statuses)));
		return self();
	}

	public T hasKnownStatus(ResponseCodeEnum status) {
		this.expectedStatus = Optional.of(status);
		return self();
	}

	public T hasKnownStatusFrom(ResponseCodeEnum... statuses) {
		permissibleStatuses = Optional.of(EnumSet.copyOf(List.of(statuses)));
		return self();
	}

	public T numPayerSigs(int hardcoded) {
		this.hardcodedNumPayerKeys = Optional.of(hardcoded);
		return self();
	}

	public T ed25519Keys(KeyGenerator.Nature nature) {
		keyGen = Optional.of(nature);
		return self();
	}

	public T sigMapPrefixes(SigMapGenerator gen) {
		sigMapGen = Optional.of(gen);
		return self();
	}

	public T hasAnyKnownStatus() {
		acceptAnyKnownStatus = true;
		return self();
	}

	public T hasAnyStatusAtAll() {
		acceptAnyStatus = true;
		return self();
	}

	public T hasAnyPrecheck() {
		acceptAnyPrecheck = true;
		return self();
	}

	public T sigControl(ControlForKey... overrides) {
		controlOverrides = Optional.of(overrides);
		return self();
	}

	public T deferStatusResolution() {
		deferStatusResolution = true;
		return self();
	}

	public T delayBy(long pauseMs) {
		submitDelay = Optional.of(pauseMs);
		return self();
	}

	public T suppressStats(boolean flag) {
		suppressStats = flag;
		return self();
	}

	public T noLogging() {
		loggingOff = true;
		return self();
	}

	public T logging() {
		loggingOff = false;
		return self();
	}

	public T yahcliLogging() {
		yahcliLogger = true;
		return self();
	}

	public T validDurationSecs(long secs) {
		validDurationSecs = Optional.of(secs);
		return self();
	}

	public T txnId(String name) {
		customTxnId = Optional.of(name);
		return self();
	}

	public T randomNode() {
		useRandomNode = true;
		return self();
	}

	public T unavailableNode() {
		unavailableNode = true;
		return self();
	}

	public T setNode(String account) {
		node = Optional.of(HapiPropertySource.asAccount(account));

		return self();
	}

	public T setNodeFrom(Supplier<String> accountSupplier) {
		nodeSupplier = Optional.of(() -> HapiPropertySource.asAccount(accountSupplier.get()));
		return self();
	}

	public T usePresetTimestamp() {
		usePresetTimestamp = true;
		return self();
	}

	public T scrambleTxnBody(Function<Transaction, Transaction> func) {
		fiddler = Optional.of(func);
		return self();
	}

	public T asTxnWithOnlySigMap() {
		asTxnWithOnlySigMap = true;
		return self();
	}

	public T sansTxnId() {
		omitTxnId = true;
		return self();
	}

	public T withLegacyProtoStructure() {
		alwaysWithLegacyProtoStructure = true;
		return self();
	}

	public T asTxnWithSignedTxnBytesAndSigMap() {
		asTxnWithSignedTxnBytesAndSigMap = true;
		return self();
	}

	public T asTxnWithSignedTxnBytesAndBodyBytes() {
		asTxnWithSignedTxnBytesAndBodyBytes = true;
		return self();
	}

	public T sansNodeAccount() {
		omitNodeAccount = true;
		return self();
	}

	public TransactionReceipt getLastReceipt() {
		return lastReceipt;
	}
}
