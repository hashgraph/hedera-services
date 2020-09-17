package com.hedera.services.bdd.spec;

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
import com.hedera.services.bdd.spec.props.NodeConnectInfo;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.extractTxnId;
import static java.util.Collections.EMPTY_LIST;
import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.ConsensusServiceFeeBuilder;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hederahashgraph.fee.FeeBuilder;
import com.hederahashgraph.fee.FileFeeBuilder;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.keys.SigMapGenerator;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.stats.OpObs;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class HapiSpecOperation {
	private static final Logger log = LogManager.getLogger(HapiSpecOperation.class);

	private Random r = new Random();

	/* Note that an op may _be_ a txn; or just a query that submits a txn as payment. */
	protected String txnName = UUID.randomUUID().toString().substring(0, 8);
	protected Transaction txnSubmitted;
	protected TransactionRecord recordOfSubmission;

	protected FeeBuilder fees = new FeeBuilder();
	protected FileFeeBuilder fileFees = new FileFeeBuilder();
	protected CryptoFeeBuilder cryptoFees = new CryptoFeeBuilder();
	protected SmartContractFeeBuilder scFees = new SmartContractFeeBuilder();
	protected ConsensusServiceFeeBuilder hcsFees = new ConsensusServiceFeeBuilder();

	protected boolean loggingOff = false;
	protected boolean suppressStats = false;
	protected boolean verboseLoggingOn = false;
	protected boolean shouldRegisterTxnId = false;
	protected boolean useDefaultTxnAsCostAnswerPayment = false;
	protected boolean useDefaultTxnAsAnswerOnlyPayment = false;
	protected boolean usePresetTimestamp = false;

	protected boolean useTls = false;
	protected boolean useRandomNode = false;
	protected Optional<Integer> hardcodedNumPayerKeys = Optional.empty();
	protected Optional<SigMapGenerator.Nature> sigMapGen = Optional.empty();
	protected Optional<List<Function<HapiApiSpec, Key>>> signers = Optional.empty();
	protected Optional<ControlForKey[]> controlOverrides = Optional.empty();
	protected Map<Key, SigControl> overrides = Collections.EMPTY_MAP;

	protected Optional<Long> fee = Optional.empty();
	protected Optional<Long> submitDelay = Optional.empty();
	protected Optional<Long> validDurationSecs = Optional.empty();
	protected Optional<String> customTxnId = Optional.empty();
	protected Optional<String> memo = Optional.empty();
	protected Optional<String> payer = Optional.empty();
	protected Optional<Boolean> genRecord = Optional.empty();
	protected Optional<AccountID> node = Optional.empty();
	protected Optional<Supplier<AccountID>> nodeSupplier = Optional.empty();

	abstract protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable;

	abstract protected boolean submitOp(HapiApiSpec spec) throws Throwable;

	protected Key lookupKey(HapiApiSpec spec, String name) {
		return spec.registry().getKey(name);
	}

	protected void updateStateOf(HapiApiSpec spec) throws Throwable { /* no-op. */ }

	protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable { /* no-op. */ }

	public HederaFunctionality type() {
		return HederaFunctionality.UNRECOGNIZED;
	}

	public void finalizeExecFor(HapiApiSpec spec) throws Throwable { /* no-op. */ }

	public boolean requiresFinalization(HapiApiSpec spec) {
		return false;
	}

	protected AccountID targetNodeFor(HapiApiSpec spec) {
		fixNodeFor(spec);
		return node.get();
	}

	protected void configureTlsFor(HapiApiSpec spec) {
		useTls = spec.setup().getConfigTLS();
	}

	protected void fixNodeFor(HapiApiSpec spec) {
		if (node.isPresent()) {
			return;
		}
		if (nodeSupplier.isPresent()) {
			node = Optional.of(nodeSupplier.get().get());
		} else {
			if (useRandomNode || spec.setup().nodeSelector() == HapiSpecSetup.NodeSelection.RANDOM) {
				node = Optional.of(randomNodeFrom(spec));
			} else {
				node = Optional.of(spec.setup().defaultNode());
			}
		}
	}

	private AccountID randomNodeFrom(HapiApiSpec spec) {
		List<NodeConnectInfo> nodes = spec.setup().nodes();
		return nodes.get(r.nextInt(nodes.size())).getAccount();
	}

	public Optional<Throwable> execFor(HapiApiSpec spec) {
		pauseIfRequested();
		try {
			boolean hasCompleteLifecycle = submitOp(spec);

			if (!(this instanceof UtilOp)) {
				spec.incrementNumLedgerOps();
			}

			if (shouldRegisterTxnId) {
				saveTxnId(spec);
			}

			if (hasCompleteLifecycle) {
				assertExpectationsGiven(spec);
				updateStateOf(spec);
			}
		} catch (Throwable t) {
			if (!loggingOff) {
				log.warn(spec.logPrefix() + this + " failed!", t);
			}
			return Optional.of(t);
		}
		return Optional.empty();
	}

	private void pauseIfRequested() {
		submitDelay.ifPresent(l -> {
			try {
				Thread.sleep(l);
			} catch (InterruptedException ignore) {
			}
		});
	}

	private void saveTxnId(HapiApiSpec spec) throws Throwable {
		if (txnSubmitted != Transaction.getDefaultInstance()) {
			TransactionID txnId = extractTxnId(txnSubmitted);
			spec.registry().saveTxnId(txnName, txnId);
		}
	}

	protected Consumer<TransactionBody.Builder> bodyDef(HapiApiSpec spec) {
		return builder -> {
			payer.ifPresent(payerId -> {
				var id = TxnUtils.asId(payerId, spec);
				TransactionID txnId = builder.getTransactionID().toBuilder().setAccountID(id).build();
				builder.setTransactionID(txnId);
			});
			if (usePresetTimestamp) {
				TransactionID txnId = builder.getTransactionID().toBuilder().setTransactionValidStart(
						spec.registry().getTimestamp(txnName)).build();
				builder.setTransactionID(txnId);
			}
			customTxnId.ifPresent(name -> {
				TransactionID id = spec.registry().getTxnId(name);
				builder.setTransactionID(id);
			});

			node.ifPresent(nodeId -> builder.setNodeAccountID(nodeId));
			validDurationSecs.ifPresent(s -> {
				builder.setTransactionValidDuration(Duration.newBuilder().setSeconds(s).build());
			});
			genRecord.ifPresent(g -> builder.setGenerateRecord(g));
			memo.ifPresent(m -> builder.setMemo(m));
		};
	}

	protected Transaction finalizedTxn(HapiApiSpec spec, Consumer<TransactionBody.Builder> opDef) throws Throwable {
		return finalizedTxn(spec, opDef, false);
	}

	protected Transaction finalizedTxn(
			HapiApiSpec spec,
			Consumer<TransactionBody.Builder> opDef,
			boolean forCost
	) throws Throwable {
		if ((forCost && useDefaultTxnAsCostAnswerPayment) || (!forCost && useDefaultTxnAsAnswerOnlyPayment)) {
			return Transaction.getDefaultInstance();
		}

		Transaction txn;
		Consumer<TransactionBody.Builder> minDef = bodyDef(spec);
		Consumer<TransactionBody.Builder> netDef = fee
				.map(amount -> minDef.andThen(b -> b.setTransactionFee(amount)))
				.orElse(minDef).andThen(opDef);

		setKeyControlOverrides(spec);
		List<Key> keys = signersToUseFor(spec);
		Transaction.Builder builder = spec.txns().getReadyToSign(netDef);
		Transaction provisional = getSigned(spec, builder, keys);
		if (fee.isPresent()) {
			txn = provisional;
		} else {
			Key payerKey = spec.registry().getKey(payer.orElse(spec.setup().defaultPayerName()));
			int numPayerKeys = hardcodedNumPayerKeys.orElse(spec.keys().controlledKeyCount(payerKey, overrides));
			long customFee = feeFor(spec, provisional, numPayerKeys);
			netDef = netDef.andThen(b -> b.setTransactionFee(customFee));
			txn = getSigned(spec, spec.txns().getReadyToSign(netDef), keys);
		}

		SignedTransaction signedTransaction = SignedTransaction.newBuilder()
				.setBodyBytes(CommonUtils.extractTransactionBodyByteString(txn))
				.setSigMap(CommonUtils.extractSignatureMap(txn))
				.build();

		return Transaction.newBuilder().setSignedTransactionBytes(signedTransaction.toByteString()).build();
	}

	private Transaction getSigned(HapiApiSpec spec, Transaction.Builder builder, List<Key> keys) throws Throwable {
		return sigMapGen.isPresent()
				? spec.keys().sign(builder, keys, overrides, sigMapGen.get())
				: spec.keys().sign(builder, keys, overrides);
	}

	private void setKeyControlOverrides(HapiApiSpec spec) {
		if (controlOverrides.isPresent()) {
			overrides = new HashMap<>();
			Stream
					.of(controlOverrides.get())
					.forEach(c -> overrides.put(lookupKey(spec, c.getKeyName()), c.getController()));
		}
	}

	List<Key> signersToUseFor(HapiApiSpec spec) {
		List<Key> active = signers
				.orElse(defaultSigners())
				.stream()
				.map(f -> f.apply(spec))
				.filter(k -> k != Key.getDefaultInstance())
				.collect(toList());
		if (!signers.isPresent()) {
			active.addAll(variableDefaultSigners().apply(spec));
		}
		return active;
	}

	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		return Arrays.asList(spec -> spec.registry().getKey(effectivePayer(spec)));
	}

	protected Function<HapiApiSpec, List<Key>> variableDefaultSigners() {
		return spec -> EMPTY_LIST;
	}

	protected String effectivePayer(HapiApiSpec spec) {
		return payer.orElse(spec.setup().defaultPayerName());
	}

	protected void lookupSubmissionRecord(HapiApiSpec spec) throws Throwable {
		HapiGetTxnRecord subOp = QueryVerbs
				.getTxnRecord(extractTxnId(txnSubmitted))
				.noLogging()
				.suppressStats(true)
				.nodePayment(spec.setup().defaultNodePaymentTinyBars());
		Optional<Throwable> error = subOp.execFor(spec);
		if (error.isPresent()) {
			throw error.get();
		}
		recordOfSubmission = subOp.getResponse().getTransactionGetRecord().getTransactionRecord();
	}

	@Override
	public String toString() {
		return toStringHelper().toString();
	}

	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this);
		if (txnSubmitted != null) {
			helper.add("sigs", FeeBuilder.getSignatureCount(txnSubmitted));
		}
		payer.ifPresent(a -> helper.add("payer", a));
		node.ifPresent(id -> helper.add("node", HapiPropertySource.asAccountString(id)));
		return helper;
	}

	public Optional<String> getPayer() {
		return payer;
	}
	protected void considerRecording(HapiApiSpec spec, OpObs obs) {
		if (!suppressStats) {
			spec.registry().record(obs);
		}
	}
}
