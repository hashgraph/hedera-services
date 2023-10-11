/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.queries;

import static com.hedera.services.bdd.spec.HapiSpec.CostSnapshotMode.OFF;
import static com.hedera.services.bdd.spec.fees.Payment.Reason.ANSWER_ONLY_QUERY_COST;
import static com.hedera.services.bdd.spec.fees.Payment.Reason.COST_ANSWER_QUERY_COST;
import static com.hedera.services.bdd.spec.queries.QueryUtils.reflectForCost;
import static com.hedera.services.bdd.spec.queries.QueryUtils.reflectForPrecheck;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTransferList;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.txnToString;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static java.lang.Thread.sleep;
import static java.util.stream.Collectors.toList;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.exceptions.HapiQueryCheckStateException;
import com.hedera.services.bdd.spec.exceptions.HapiQueryPrecheckStateException;
import com.hedera.services.bdd.spec.fees.Payment;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.SigMapGenerator;
import com.hedera.services.bdd.spec.stats.QueryObs;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class HapiQueryOp<T extends HapiQueryOp<T>> extends HapiSpecOperation {
    private static final Logger log = LogManager.getLogger(HapiQueryOp.class);

    private String nodePaymentName;
    private boolean recordsNodePayment = false;
    private boolean stopAfterCostAnswer = false;
    private boolean expectStrictCostAnswer = false;
    protected Response response = null;
    protected List<TransactionRecord> childRecords = null;
    protected List<TransactionReceipt> childReceipts = null;
    protected ResponseCodeEnum actualPrecheck = UNKNOWN;
    private Optional<ResponseCodeEnum> answerOnlyPrecheck = Optional.empty();
    private Optional<Function<HapiSpec, Long>> nodePaymentFn = Optional.empty();
    private Optional<EnumSet<ResponseCodeEnum>> permissibleAnswerOnlyPrechecks = Optional.empty();
    private Optional<EnumSet<ResponseCodeEnum>> permissibleCostAnswerPrechecks = Optional.empty();
    /** if response code in the set then allow to resubmit transaction */
    protected Optional<EnumSet<ResponseCodeEnum>> answerOnlyRetryPrechecks = Optional.empty();

    private ResponseCodeEnum expectedCostAnswerPrecheck() {
        return costAnswerPrecheck.orElse(OK);
    }

    private ResponseCodeEnum expectedAnswerOnlyPrecheck() {
        return answerOnlyPrecheck.orElse(OK);
    }

    protected Optional<Long> nodePayment = Optional.empty();
    protected Optional<ResponseCodeEnum> costAnswerPrecheck = Optional.empty();
    protected Optional<HapiCryptoTransfer> explicitPayment = Optional.empty();

    /* WARNING: Must set `response` as a side effect! */
    protected abstract void submitWith(HapiSpec spec, Transaction payment) throws Throwable;

    protected abstract boolean needsPayment();

    protected long lookupCostWith(HapiSpec spec, Transaction payment) throws Throwable {
        return 0L;
    }

    protected long costOnlyNodePayment(HapiSpec spec) throws Throwable {
        return 0L;
    }

    public Response getResponse() {
        return response;
    }

    protected long costFrom(Response response) throws Throwable {
        ResponseCodeEnum actualPrecheck = reflectForPrecheck(response);
        if (permissibleCostAnswerPrechecks.isPresent()) {
            if (permissibleCostAnswerPrechecks.get().contains(actualPrecheck)) {
                costAnswerPrecheck = Optional.of(actualPrecheck);
            } else {
                String errMsg = String.format(
                        "Cost-answer precheck was %s, not one of %s!",
                        actualPrecheck, permissibleCostAnswerPrechecks.get());
                if (!loggingOff) {
                    log.error(errMsg);
                }
                throw new HapiQueryCheckStateException(errMsg);
            }
        } else {
            if (expectedCostAnswerPrecheck() != actualPrecheck) {
                String errMsg = String.format(
                        "Bad costAnswerPrecheck! expected %s, actual %s", expectedCostAnswerPrecheck(), actualPrecheck);
                if (!loggingOff) {
                    log.error(errMsg);
                }
                throw new HapiQueryCheckStateException(errMsg);
            }
        }
        return reflectForCost(response);
    }

    protected abstract T self();

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        fixNodeFor(spec);
        configureTlsFor(spec);

        Transaction payment = Transaction.getDefaultInstance();
        int retryCount = 1;
        while (true) {
            /* Note that HapiQueryOp#fittedPayment makes a COST_ANSWER query if necessary. */
            if (needsPayment()) {
                payment = fittedPayment(spec);
            }

            if (stopAfterCostAnswer) {
                return false;
            }

            /* If the COST_ANSWER query was expected to fail, we will not do anything else for this query. */
            if (needsPayment() && !nodePayment.isPresent() && expectedCostAnswerPrecheck() != OK) {
                return false;
            }

            if (needsPayment() && !loggingOff) {
                String message = String.format("%sPaying for %s with %s", spec.logPrefix(), this, txnToString(payment));
                log.info(message);
            }
            timedSubmitWith(spec, payment);

            actualPrecheck = reflectForPrecheck(response);
            if (answerOnlyRetryPrechecks.isPresent()
                    && answerOnlyRetryPrechecks.get().contains(actualPrecheck)
                    && isWithInRetryLimit(retryCount)) {
                retryCount++;
                sleep(10);
            } else {
                break;
            }
        }
        if (permissibleAnswerOnlyPrechecks.isPresent()) {
            if (permissibleAnswerOnlyPrechecks.get().contains(actualPrecheck)) {
                answerOnlyPrecheck = Optional.of(actualPrecheck);
            } else {
                final String errMsg = String.format(
                        "Answer-only precheck was %s, not one of %s!",
                        actualPrecheck, permissibleAnswerOnlyPrechecks.get());
                if (!loggingOff) {
                    log.error(errMsg);
                }
                throw new HapiQueryPrecheckStateException(errMsg);
            }
        } else {
            if (expectedAnswerOnlyPrecheck() != actualPrecheck) {
                final String errMsg = String.format(
                        "Bad answerOnlyPrecheck! expected %s, actual %s", expectedAnswerOnlyPrecheck(), actualPrecheck);
                if (!loggingOff) {
                    log.error(errMsg);
                }
                throw new HapiQueryPrecheckStateException(errMsg);
            }
        }
        if (expectedCostAnswerPrecheck() != OK || expectedAnswerOnlyPrecheck() != OK) {
            return false;
        }
        txnSubmitted = payment;
        return true;
    }

    private void timedSubmitWith(HapiSpec spec, Transaction payment) throws Throwable {
        if (suppressStats) {
            submitWith(spec, payment);
        } else {
            long before = System.currentTimeMillis();
            submitWith(spec, payment);
            long after = System.currentTimeMillis();

            QueryObs stats = new QueryObs(ResponseType.ANSWER_ONLY, type());
            stats.setAccepted(reflectForPrecheck(response) == OK);
            stats.setResponseLatency(after - before);
            considerRecording(spec, stats);
        }
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        return spec.fees()
                .forActivityBasedOp(
                        HederaFunctionality.CryptoTransfer,
                        (_txn, _svo) -> usageEstimate(_txn, _svo, spec.fees().tokenTransferUsageMultiplier()),
                        txn,
                        numPayerKeys);
    }

    private FeeData usageEstimate(TransactionBody txn, SigValueObj svo, int multiplier) {
        return HapiCryptoTransfer.usageEstimate(txn, svo, multiplier);
    }

    private Transaction fittedPayment(HapiSpec spec) throws Throwable {
        if (explicitPayment.isPresent()) {
            return explicitPayment.get().signedTxnFor(spec);
        } else if (nodePaymentFn.isPresent()) {
            return finalizedTxn(spec, opDef(spec, nodePaymentFn.get().apply(spec)));
        } else if (nodePayment.isPresent()) {
            return finalizedTxn(spec, opDef(spec, nodePayment.get()));
        } else {
            long initNodePayment = costOnlyNodePayment(spec);
            Transaction payment = finalizedTxn(spec, opDef(spec, initNodePayment), true);
            if (!loggingOff) {
                final String message = String.format(
                        "%sPaying for COST_ANSWER of %s with %s", spec.logPrefix(), this, txnToString(payment));
                log.info(message);
            }
            long realNodePayment = timedCostLookupWith(spec, payment);
            if (recordsNodePayment) {
                spec.registry().saveAmount(nodePaymentName, realNodePayment);
            }
            if (!suppressStats) {
                spec.incrementNumLedgerOps();
            }
            if (expectedCostAnswerPrecheck() != OK) {
                return null;
            }
            if (spec.setup().costSnapshotMode() != OFF) {
                spec.recordPayment(
                        new Payment(initNodePayment, self().getClass().getSimpleName(), COST_ANSWER_QUERY_COST));
                spec.recordPayment(
                        new Payment(realNodePayment, self().getClass().getSimpleName(), ANSWER_ONLY_QUERY_COST));
            }
            txnSubmitted = payment;
            if (!loggingOff) {
                final String message = String.format(
                        "%s--> Node payment for %s is %s tinyBars.", spec.logPrefix(), this, realNodePayment);
                log.info(message);
            }
            if (expectStrictCostAnswer) {
                Transaction insufficientPayment = finalizedTxn(spec, opDef(spec, realNodePayment - 1));
                submitWith(spec, insufficientPayment);
                if (INSUFFICIENT_TX_FEE != reflectForPrecheck(response)) {
                    final String errMsg = String.format(
                            "Strict cost of answer! suppose to be %s, but get %s",
                            INSUFFICIENT_TX_FEE, reflectForPrecheck(response));
                    log.error(errMsg);
                    throw new HapiQueryPrecheckStateException(errMsg);
                } else {
                    log.info(
                            "Query with node payment of {} tinyBars got INSUFFICIENT_TX_FEE as" + " expected!",
                            realNodePayment - 1);
                }
            }
            return finalizedTxn(spec, opDef(spec, realNodePayment));
        }
    }

    private long timedCostLookupWith(HapiSpec spec, Transaction payment) throws Throwable {
        if (suppressStats) {
            return lookupCostWith(spec, payment);
        } else {
            long before = System.currentTimeMillis();
            long cost = lookupCostWith(spec, payment);
            long after = System.currentTimeMillis();

            QueryObs stats = new QueryObs(ResponseType.COST_ANSWER, type());
            stats.setAccepted(expectedCostAnswerPrecheck() == OK);
            stats.setResponseLatency(after - before);
            considerRecording(spec, stats);

            return cost;
        }
    }

    private Consumer<TransactionBody.Builder> opDef(HapiSpec spec, long amount) throws Throwable {
        TransferList transfers = asTransferList(
                tinyBarsFromTo(amount, spec.registry().getAccountID(effectivePayer(spec)), targetNodeFor(spec)));
        CryptoTransferTransactionBody opBody = spec.txns()
                .<CryptoTransferTransactionBody, CryptoTransferTransactionBody.Builder>body(
                        CryptoTransferTransactionBody.class, b -> b.setTransfers(transfers));
        return b -> b.setCryptoTransfer(opBody);
    }

    public T hasCostAnswerPrecheck(ResponseCodeEnum precheck) {
        costAnswerPrecheck = Optional.of(precheck);
        return self();
    }

    public T hasCostAnswerPrecheckFrom(ResponseCodeEnum... prechecks) {
        permissibleCostAnswerPrechecks = Optional.of(EnumSet.copyOf(List.of(prechecks)));
        return self();
    }

    public T hasAnswerOnlyPrecheck(ResponseCodeEnum precheck) {
        answerOnlyPrecheck = Optional.of(precheck);
        return self();
    }

    public T hasAnswerOnlyPrecheckFrom(ResponseCodeEnum... prechecks) {
        permissibleAnswerOnlyPrechecks = Optional.of(EnumSet.copyOf(List.of(prechecks)));
        return self();
    }

    public T hasRetryAnswerOnlyPrecheck(ResponseCodeEnum... statuses) {
        answerOnlyRetryPrechecks = Optional.of(EnumSet.copyOf(List.of(statuses)));
        return self();
    }

    public T setRetryLimit(int limit) {
        retryLimits = Optional.of(limit);
        return self();
    }

    public T nodePayment(Function<HapiSpec, Long> fn) {
        nodePaymentFn = Optional.of(fn);
        return self();
    }

    public T nodePayment(long amount) {
        nodePayment = Optional.of(amount);
        return self();
    }

    public T stoppingAfterCostAnswer() {
        stopAfterCostAnswer = true;
        return self();
    }

    public T expectStrictCostAnswer() {
        expectStrictCostAnswer = true;
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

    public T logged() {
        verboseLoggingOn = true;
        return self();
    }

    public T payingWith(String name) {
        payer = Optional.of(name);
        return self();
    }

    public T signedBy(String... keys) {
        signers = Optional.of(Stream.of(keys)
                .<Function<HapiSpec, Key>>map(k -> spec -> spec.registry().getKey(k))
                .collect(toList()));
        return self();
    }

    public T record(Boolean isGenerated) {
        genRecord = Optional.of(isGenerated);
        return self();
    }

    public T sigMapPrefixes(SigMapGenerator gen) {
        sigMapGen = Optional.of(gen);
        return self();
    }

    public T sigControl(ControlForKey... overrides) {
        controlOverrides = Optional.of(overrides);
        return self();
    }

    public T numPayerSigs(int hardcoded) {
        this.hardcodedNumPayerKeys = Optional.of(hardcoded);
        return self();
    }

    public T hasEncodedLedgerId(ByteString ledgerId) {
        this.expectedLedgerId = Optional.of(ledgerId);
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

    public T noYahcliLogging() {
        yahcliLogger = false;
        return self();
    }

    public T withYahcliLogging() {
        yahcliLogger = true;
        return self();
    }

    public T recordNodePaymentAs(String s) {
        recordsNodePayment = true;
        nodePaymentName = s;
        return self();
    }

    public T useEmptyTxnAsCostPayment() {
        useDefaultTxnAsCostAnswerPayment = true;
        return self();
    }

    public T useEmptyTxnAsAnswerPayment() {
        useDefaultTxnAsAnswerOnlyPayment = true;
        return self();
    }

    public T randomNode() {
        useRandomNode = true;
        return self();
    }

    public T unavailableNode() {
        unavailableNode = true;
        return noLogging();
    }

    public T setNode(String account) {
        node = Optional.of(HapiPropertySource.asAccount(account));
        return self();
    }

    public T setNodeFrom(Supplier<String> accountSupplier) {
        nodeSupplier = Optional.of(() -> HapiPropertySource.asAccount(accountSupplier.get()));
        return self();
    }

    public T withPayment(HapiCryptoTransfer txn) {
        explicitPayment = Optional.of(txn);
        return self();
    }
}
