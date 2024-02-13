/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.services.bdd.spec.transactions;

import static com.hedera.services.bdd.spec.fees.Payment.Reason.TXN_FEE;
import static com.hedera.services.bdd.spec.keys.DefaultKeyGen.DEFAULT_KEY_GEN;
import static com.hedera.services.bdd.spec.queries.QueryUtils.txnReceiptQueryFor;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.extractTxnId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.txnToString;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetReceipt;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static java.lang.Thread.sleep;
import static java.util.stream.Collectors.toList;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.exceptions.HapiTxnCheckStateException;
import com.hedera.services.bdd.spec.exceptions.HapiTxnPrecheckStateException;
import com.hedera.services.bdd.spec.fees.Payment;
import com.hedera.services.bdd.spec.infrastructure.DelegatingOpFinisher;
import com.hedera.services.bdd.spec.infrastructure.HapiApiClients;
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
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public abstract class HapiTxnOp<T extends HapiTxnOp<T>> extends HapiSpecOperation {
    private static final Logger log = LogManager.getLogger(HapiTxnOp.class);

    private static final Response UNKNOWN_RESPONSE = Response.newBuilder()
            .setTransactionGetReceipt(TransactionGetReceiptResponse.newBuilder()
                    .setReceipt(TransactionReceipt.newBuilder().setStatus(UNKNOWN)))
            .build();

    private long submitTime = 0L;
    private TxnObs stats;
    private boolean ensureResolvedStatusIsntFromDuplicate = false;
    private final TupleType LONG_TUPLE = TupleType.parse("(int64)");

    protected boolean deferStatusResolution = false;
    protected boolean unavailableStatusIsOk = false;
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

    public Optional<EnumSet<ResponseCodeEnum>> getPermissibleStatuses() {
        return permissibleStatuses;
    }

    public ResponseCodeEnum getExpectedStatus() {
        return expectedStatus.orElse(SUCCESS);
    }

    public ResponseCodeEnum getExpectedPrecheck() {
        return expectedPrecheck.orElse(OK);
    }

    protected abstract T self();

    protected abstract Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable;

    protected abstract Function<Transaction, TransactionResponse> callToUse(HapiSpec spec);

    public byte[] serializeSignedTxnFor(HapiSpec spec) throws Throwable {
        return finalizedTxn(spec, opBodyDef(spec)).toByteArray();
    }

    public Transaction signedTxnFor(HapiSpec spec) throws Throwable {
        return finalizedTxn(spec, opBodyDef(spec));
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        stats = new TxnObs(type());
        fixNodeFor(spec);
        configureTlsFor(spec);
        int retryCount = 1;
        while (true) {
            Transaction txn = finalizedTxn(spec, opBodyDef(spec));

            if (!loggingOff) {
                String message = String.format("%s submitting %s via %s", spec.logPrefix(), this, txnToString(txn));
                log.info(message);
            }

            TransactionResponse response;
            try {
                if (fiddler.isPresent()) {
                    txn = fiddler.get().apply(txn);
                }
                response = timedCall(spec, txn);
            } catch (StatusRuntimeException e) {
                if (respondToSRE(e, "submitting transaction")) {
                    continue;
                } else {
                    if (spec.setup().suppressUnrecoverableNetworkFailures()) {
                        return false;
                    }
                    log.error(
                            "{} Status resolution failed due to unrecoverable runtime exception, "
                                    + "possibly network connection lost.",
                            TxnUtils.toReadableString(txn),
                            e);
                    if (unavailableStatusIsOk) {
                        // If we expect the status to be unavailable (because e.g. the
                        // submitted transaction exceeds 6144 bytes and will have its
                        // gRPC request terminated immediately), then don't throw, just
                        // return true to signal to the HapiSpec that this operation's
                        // lifecycle has ended
                        return true;
                    } else {
                        throw new HapiTxnCheckStateException("Unable to resolve txn status!");
                    }
                }
            }

            /* Used by superclass to perform standard housekeeping. */
            txnSubmitted = txn;

            actualPrecheck = response.getNodeTransactionPrecheckCode();
            if (retryPrechecks.isPresent()
                    && retryPrechecks.get().contains(actualPrecheck)
                    && isWithInRetryLimit(retryCount)) {
                retryCount++;
                try {
                    sleep(10);
                } catch (InterruptedException e) {
                    log.error("Interrupted while sleeping before retry");
                    throw new RuntimeException(e);
                }
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
            final var expectedIngestStatus = getExpectedPrecheck();
            if (expectedIngestStatus != OK
                    && spec.setup().streamlinedIngestChecks().contains(expectedIngestStatus)) {
                // Since INVALID_ALIAS_KEY was in ingest in mono-service the precheck fails
                // but, in modular code it is moved to handle, so the tests fail with INVALID_SIGNATURE. This is a
                // temporary fix to make the tests pass.
                if (expectedIngestStatus != INVALID_ALIAS_KEY) {
                    expectedStatus = Optional.of(expectedIngestStatus);
                } else {
                    permissibleStatuses = Optional.of(EnumSet.copyOf(List.of(INVALID_ALIAS_KEY, INVALID_SIGNATURE)));
                }
                permissiblePrechecks = Optional.of(EnumSet.of(OK, expectedIngestStatus));
            }
            if (permissiblePrechecks.isPresent()) {
                if (permissiblePrechecks.get().contains(actualPrecheck)) {
                    expectedPrecheck = Optional.of(actualPrecheck);
                } else {
                    throw new HapiTxnPrecheckStateException(String.format(
                            "Wrong precheck status! Expected one of %s, actual %s",
                            permissiblePrechecks.get(), actualPrecheck));
                }
            } else {
                if (getExpectedPrecheck() != actualPrecheck) {
                    // Change to an info until HapiClientValidator can be modified and can
                    // understand new errors
                    log.info(
                            "{} {} Wrong actual precheck status {}, expecting {}",
                            spec.logPrefix(),
                            this,
                            actualPrecheck,
                            getExpectedPrecheck());
                    throw new HapiTxnPrecheckStateException(String.format(
                            "Wrong precheck status! Expected %s, actual %s", getExpectedPrecheck(), actualPrecheck));
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

    private TransactionResponse timedCall(HapiSpec spec, Transaction txn) {
        submitTime = System.currentTimeMillis();
        TransactionResponse response = callToUse(spec).apply(txn);
        long after = System.currentTimeMillis();
        stats.setResponseLatency(after - submitTime);
        return response;
    }

    private void resolveStatus(HapiSpec spec) throws Throwable {
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
                        "{} {} Wrong actual status {}, not one of {}!",
                        spec.logPrefix(),
                        this,
                        actualStatus,
                        permissibleStatuses.get());
                throw new HapiTxnCheckStateException(String.format(
                        "Wrong status! Expected one of %s, was %s", permissibleStatuses.get(), actualStatus));
            }
        } else {
            if (getExpectedStatus() != actualStatus) {
                // Change to an info until HapiClientValidator can be modified and can understand
                // new errors
                log.info(
                        "{} {} Wrong actual status {}, expected {}",
                        spec.logPrefix(),
                        this,
                        actualStatus,
                        getExpectedStatus());
                throw new HapiTxnCheckStateException(
                        String.format("Wrong status! Expected %s, was %s", getExpectedStatus(), actualStatus));
            }
        }
        if (!deferStatusResolution) {
            if (spec.setup().costSnapshotMode() != HapiSpec.CostSnapshotMode.OFF) {
                publishFeeChargedTo(spec);
            }
        }
        if (ensureResolvedStatusIsntFromDuplicate) {
            assertRecordHasExpectedMemo(spec);
        }
    }

    private void rechargePayerFor(HapiSpec spec) {
        long rechargeAmount = spec.registry().getRechargeAmount(payer.get());
        var bank = spec.setup().defaultPayerName();
        spec.registry().setRechargingTime(payer.get(), Instant.now()); // record timestamp of last recharge event
        allRunFor(spec, cryptoTransfer(tinyBarsFromTo(bank, payer.get(), rechargeAmount)));
    }

    private boolean payerIsRechargingFor(HapiSpec spec) {
        return payer.map(spec.registry()::isRecharging).orElse(Boolean.FALSE);
    }

    private synchronized boolean payerNotRecentlyRecharged(HapiSpec spec) {
        Instant lastInstant = payer.map(spec.registry()::getRechargingTime).orElse(Instant.MIN);
        Integer rechargeWindow = payer.map(spec.registry()::getRechargingWindow).orElse(0);
        return !lastInstant.plusSeconds(rechargeWindow).isAfter(Instant.now());
    }

    private void addIpbToPermissiblePrechecks() {
        if (permissiblePrechecks.isEmpty()) {
            permissiblePrechecks =
                    Optional.of(EnumSet.copyOf(List.of(OK, INSUFFICIENT_PAYER_BALANCE, INSUFFICIENT_TX_FEE)));
        } else if (!permissiblePrechecks.get().contains(INSUFFICIENT_PAYER_BALANCE)
                || !permissiblePrechecks.get().contains(INSUFFICIENT_TX_FEE)) {
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

    private void publishFeeChargedTo(HapiSpec spec) throws Throwable {
        if (recordOfSubmission == null) {
            lookupSubmissionRecord(spec);
        }
        long fee = recordOfSubmission.getTransactionFee();
        spec.recordPayment(new Payment(fee, self().getClass().getSimpleName(), TXN_FEE));
    }

    private void assertRecordHasExpectedMemo(HapiSpec spec) throws Throwable {
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
            log.warn(
                    "{} {} Memo didn't come from submitted transaction! actual memo {}, recorded" + " {}.",
                    spec.logPrefix(),
                    this,
                    memo.get(),
                    recordOfSubmission.getMemo());
            throw new IllegalStateException("Resolved submission record was from a duplicate");
        }
    }

    @Override
    public boolean requiresFinalization(HapiSpec spec) {
        return (actualPrecheck == OK) && (deferStatusResolution || hasStatsToCollectDuringFinalization(spec));
    }

    private boolean hasStatsToCollectDuringFinalization(HapiSpec spec) {
        return (!suppressStats && spec.setup().measureConsensusLatency());
    }

    @Override
    protected void lookupSubmissionRecord(HapiSpec spec) throws Throwable {
        if (actualStatus == UNKNOWN) {
            throw new HapiTxnCheckStateException(
                    this + " tried to lookup the submission record before status was known!");
        }
        super.lookupSubmissionRecord(spec);
    }

    @Override
    public void finalizeExecFor(HapiSpec spec) throws Throwable {
        boolean explicitStatSuppression = suppressStats;
        suppressStats = true;
        if (deferStatusResolution) {
            resolveStatus(spec);
            updateStateOf(spec);
        }
        if (!explicitStatSuppression) {
            if (spec.setup().measureConsensusLatency()) {
                measureConsensusLatency(spec);
            }
            spec.registry().record(stats);
        }
    }

    private void measureConsensusLatency(HapiSpec spec) throws Throwable {
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

    private ResponseCodeEnum resolvedStatusOfSubmission(HapiSpec spec) throws Throwable {
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

    private Response statusResponse(HapiSpec spec, Query receiptQuery) {
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
            } catch (StatusRuntimeException e) {
                if (!respondToSRE(e, "resolving status")) {
                    log.warn(
                            "({}) Status resolution failed with unrecognized exception",
                            Thread.currentThread().getName(),
                            e);
                    allowedUnrecognizedExceptions--;
                    if (allowedUnrecognizedExceptions == 0) {
                        response = UNKNOWN_RESPONSE;
                    }
                }
            }
        }
        long after = System.currentTimeMillis();
        considerRecordingAdHocReceiptQueryStats(spec.registry(), after - before);
        return response;
    }

    private boolean respondToSRE(@NonNull final StatusRuntimeException e, @NonNull final String context) {
        final var msg = e.toString();
        try {
            if (isRecognizedRecoverable(msg)) {
                log.info("Recognized recoverable runtime exception {} when {}", msg, context);
                Thread.sleep(250L);
                return true;
            } else if (isInternalError(e)) {
                log.warn("Internal HTTP/2 error when {}, rebuilding channels", context);
                HapiApiClients.rebuildChannels();
                Thread.sleep(250L);
                return true;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted when responding to StatusRuntimeException");
            throw new RuntimeException(ex);
        }
        return false;
    }

    private boolean isInternalError(@NonNull final StatusRuntimeException e) {
        return e.toString().contains("INTERNAL: http2 exception");
    }

    private boolean isRecognizedRecoverable(String msg) {
        return msg.contains("NO_ERROR")
                || msg.contains("Received unexpected EOS on DATA frame from server")
                || msg.contains("REFUSED_STREAM")
                || msg.contains("UNAVAILABLE: Channel shutdown invoked");
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
                log.error("Interrupted during {}ms pause", forMs);
                Thread.currentThread().interrupt();
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

    public T blankMetadata() {
        memo = Optional.of("");
        return self();
    }

    public T metaData(String text) {
        metadata = Optional.of(text);
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

    public T signedByPayerAnd(String... keys) {
        final String[] copy = new String[keys.length + 1];
        copy[0] = DEFAULT_PAYER;
        System.arraycopy(keys, 0, copy, 1, keys.length);
        return signedBy(copy);
    }

    public T signedBy(String... keys) {
        signers = Optional.of(Stream.of(keys)
                .<Function<HapiSpec, Key>>map(k -> spec -> spec.registry().getKey(k))
                .collect(toList()));
        return self();
    }

    public T orUnavailableStatus() {
        unavailableStatusIsOk = true;
        return self();
    }

    public T payingWith(String name) {
        payer = Optional.of(name);
        return self();
    }

    public T withUnknownFieldIn(final UnknownFieldLocation location) {
        unknownFieldLocation = location;
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

    public T withTxnTransform(UnaryOperator<Transaction> func) {
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

    public T withProtoStructure(HapiSpecSetup.TxnProtoStructure protoStructure) {
        explicitProtoStructure = protoStructure;
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

    public ResponseCodeEnum getActualPrecheck() {
        return actualPrecheck;
    }

    public boolean hasActualStatus() {
        return lastReceipt != null;
    }

    public ResponseCodeEnum getActualStatus() {
        return lastReceipt.getStatus();
    }
}
