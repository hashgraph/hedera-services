package com.hedera.services.bdd.spec.queries;

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

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.exceptions.HapiQueryCheckStateException;
import com.hedera.services.bdd.spec.exceptions.HapiQueryPrecheckStateException;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.fees.Payment;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.SigMapGenerator;
import com.hedera.services.bdd.spec.stats.QueryObs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hedera.services.bdd.spec.HapiApiSpec.CostSnapshotMode.OFF;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.*;
import static com.hedera.services.bdd.spec.queries.QueryUtils.*;
import static java.util.stream.Collectors.toList;
import static com.hedera.services.bdd.spec.fees.Payment.Reason.*;

public abstract class HapiQueryOp<T extends HapiQueryOp<T>> extends HapiSpecOperation {
	private static final Logger log = LogManager.getLogger(HapiQueryOp.class);

	private String nodePaymentName;
	private boolean recordsNodePayment = false;
	private boolean stopAfterCostAnswer = false;
	private boolean expectStrictCostAnswer = false;
	protected Response response = null;
	private Optional<ResponseCodeEnum> answerOnlyPrecheck = Optional.empty();
	private Optional<Function<HapiApiSpec, Long>> nodePaymentFn = Optional.empty();
	private Optional<EnumSet<ResponseCodeEnum>> permissibleAnswerOnlyPrechecks = Optional.empty();
	private Optional<EnumSet<ResponseCodeEnum>> permissibleCostAnswerPrechecks = Optional.empty();
	private ResponseCodeEnum expectedCostAnswerPrecheck() { return costAnswerPrecheck.orElse(OK); }
	private ResponseCodeEnum expectedAnswerOnlyPrecheck() { return answerOnlyPrecheck.orElse(OK); }

	protected Optional<Long> nodePayment = Optional.empty();
	protected Optional<ResponseCodeEnum> costAnswerPrecheck = Optional.empty();
	protected Optional<HapiCryptoTransfer> explicitPayment = Optional.empty();

	/* WARNING: Must set `response` as a side effect! */
	protected abstract void submitWith(HapiApiSpec spec, Transaction payment) throws Throwable;
	protected abstract boolean needsPayment();

	protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable { return 0L; }
	protected long costOnlyNodePayment(HapiApiSpec spec) throws Throwable { return 0L; };

	public Response getResponse() {
		return response;
	}

	protected long costFrom(Response response) throws Throwable {
		ResponseCodeEnum actualPrecheck = reflectForPrecheck(response);
		if (permissibleCostAnswerPrechecks.isPresent()) {
			if (permissibleCostAnswerPrechecks.get().contains(actualPrecheck)) {
				costAnswerPrecheck = Optional.of(actualPrecheck);
			} else {
				String errMsg = String.format("Cost-answer precheck was %s, not one of %s!",
						actualPrecheck,	permissibleCostAnswerPrechecks.get());
				log.error(errMsg);

				throw new HapiQueryCheckStateException(errMsg);
			}
		} else {
			if(expectedCostAnswerPrecheck() != actualPrecheck) {
				String errMsg = String.format("Bad costAnswerPrecheck! expected {}, actual {}", expectedCostAnswerPrecheck(), actualPrecheck);
				log.error(errMsg);
				throw new HapiQueryCheckStateException(errMsg);
			}
		}
		return reflectForCost(response);
	}

	protected abstract T self();

	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		fixNodeFor(spec);
		configureTlsFor(spec);

		/* Note that HapiQueryOp#fittedPayment makes a COST_ANSWER query if necessary. */
		Transaction payment = needsPayment() ? fittedPayment(spec) : Transaction.getDefaultInstance();

		if (stopAfterCostAnswer) {
			return false;
		}

		/* If the COST_ANSWER query was expected to fail, we will not do anything else for this query. */
		if (needsPayment() && !nodePayment.isPresent() && expectedCostAnswerPrecheck() != OK) {
			return false;
		}

		if (needsPayment() && !loggingOff) {
			log.info(spec.logPrefix() + "Paying for " + this + " with " + txnToString(payment));
		}
		timedSubmitWith(spec, payment);

		ResponseCodeEnum actualPrecheck = reflectForPrecheck(response);
		if (permissibleAnswerOnlyPrechecks.isPresent()) {
			if (permissibleAnswerOnlyPrechecks.get().contains(actualPrecheck)) {
				answerOnlyPrecheck = Optional.of(actualPrecheck);
			} else {
				String errMsg = String.format("Answer-only precheck was %s, not one of %s!",
						actualPrecheck,	permissibleAnswerOnlyPrechecks.get());
//				Turn this off until HAPIClientValidator needs it and knows how to deal with it
//				log.error(errMsg);
				throw new HapiQueryPrecheckStateException(errMsg);
			}
		} else {
			if(expectedAnswerOnlyPrecheck() != actualPrecheck) {
				String errMsg = String.format("Bad answerOnlyPrecheck! expected %s, actual %s", expectedAnswerOnlyPrecheck(), actualPrecheck);
//				Turn this off until HAPIClientValidator needs it and knows how to deal with it
//				log.error(errMsg);
				throw new HapiQueryPrecheckStateException(errMsg);
			}
		}
		if (expectedCostAnswerPrecheck() != OK || expectedAnswerOnlyPrecheck() != OK) { return false; }
		txnSubmitted = payment;
		return true;
	}
	private void timedSubmitWith(HapiApiSpec spec, Transaction payment) throws Throwable {
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
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.CryptoTransfer,
				cryptoFees::getCryptoTransferTxFeeMatrices,
				txn, numPayerKeys);
	}

	private Transaction fittedPayment(HapiApiSpec spec) throws Throwable {
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
				log.info(spec.logPrefix() + "Paying for COST_ANSWER of " + this + " with " + txnToString(payment));
			}
			long realNodePayment = timedCostLookupWith(spec, payment);
			if (recordsNodePayment) {
				spec.registry().saveAmount(nodePaymentName, realNodePayment);
			}
			if (!suppressStats) { spec.incrementNumLedgerOps(); }
			if (expectedCostAnswerPrecheck() != OK) {
				return null;
			}
			if (spec.setup().costSnapshotMode() != OFF) {
				spec.recordPayment(new Payment(
						initNodePayment,
						self().getClass().getSimpleName(),
						COST_ANSWER_QUERY_COST));
				spec.recordPayment(new Payment(
						realNodePayment,
						self().getClass().getSimpleName(),
						ANSWER_ONLY_QUERY_COST));
			}
			txnSubmitted = payment;
			if (!loggingOff) {
				log.info(spec.logPrefix() + "--> Node payment for " + this + " is " + realNodePayment + " tinyBars.");
			}
			if (expectStrictCostAnswer) {
				Transaction insufficientPayment = finalizedTxn(spec, opDef(spec, realNodePayment - 1));
				submitWith(spec, insufficientPayment);
				if(INSUFFICIENT_TX_FEE != reflectForPrecheck(response)) {
					String errMsg = String.format("Strict cost of answer! suppose to be {}, but get {}",
							INSUFFICIENT_TX_FEE, reflectForPrecheck(response));
					log.error(errMsg);
					throw new HapiQueryPrecheckStateException(errMsg);
				}
				else {
					log.info("Query with node payment of {} tinyBars got INSUFFICIENT_TX_FEE as expected!",
							realNodePayment - 1);
				}
			}
			return finalizedTxn(spec, opDef(spec, realNodePayment));
		}
	}

	private long timedCostLookupWith(HapiApiSpec spec, Transaction payment)	throws Throwable {
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

	private Consumer<TransactionBody.Builder> opDef(HapiApiSpec spec, long amount) throws Throwable {
		TransferList transfers = asTransferList(
				tinyBarsFromTo(
						amount,
						spec.registry().getAccountID(effectivePayer(spec)),
						targetNodeFor(spec)));
		CryptoTransferTransactionBody opBody = spec
				.txns()
				.<CryptoTransferTransactionBody, CryptoTransferTransactionBody.Builder>
						body(CryptoTransferTransactionBody.class, b -> b.setTransfers(transfers));
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
	public T nodePayment(Function<HapiApiSpec, Long> fn) {
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
		signers = Optional.of(
				Stream.of(keys)
						.<Function<HapiApiSpec, Key>>map(k -> spec -> spec.registry().getKey(k))
						.collect(toList()));
		return self();
	}
	public T record(Boolean isGenerated) {
		genRecord = Optional.of(isGenerated);
		return self();
	}
	public T sigMapPrefixes(SigMapGenerator.Nature nature) {
		sigMapGen = Optional.of(nature);
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
