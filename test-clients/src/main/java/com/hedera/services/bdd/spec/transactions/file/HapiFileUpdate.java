package com.hedera.services.bdd.spec.transactions.file;

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
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileContents;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnFactory;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.EMPTY_MAP;
import static java.util.Collections.EMPTY_SET;

public class HapiFileUpdate extends HapiTxnOp<HapiFileUpdate> {
	static final Logger log = LogManager.getLogger(HapiFileUpdate.class);
	static final ByteString RANDOM_4K = ByteString.copyFrom(TxnUtils.randomUtf8Bytes(TxnUtils.BYTES_4K));

	/* WARNING - set to true only if you really want to replace 0.0.121/2! */
	private boolean dropUnmentionedProperties = false;
	private boolean useBadlyEncodedWacl = false;
	private boolean useEmptyWacl = false;

	private final String file;
	private OptionalLong expiryExtension = OptionalLong.empty();
	private Optional<Long> lifetimeSecs = Optional.empty();
	private Optional<String> newWaclKey = Optional.empty();
	private Optional<String> newContentsPath = Optional.empty();
	private Optional<String> literalNewContents = Optional.empty();
	private Optional<String> basePropsFile = Optional.empty();
	private Optional<ByteString> newContents = Optional.empty();
	private Optional<Set<String>> propDeletions = Optional.empty();
	private Optional<Map<String, String>> propOverrides = Optional.empty();
	private Optional<Function<HapiApiSpec, ByteString>> contentFn = Optional.empty();

	public HapiFileUpdate(String file) {
		this.file = file;
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.FileUpdate;
	}

	public HapiFileUpdate droppingUnmentioned() {
		dropUnmentionedProperties = true;
		return this;
	}

	public HapiFileUpdate lifetime(long secs) {
		lifetimeSecs = Optional.of(secs);
		return this;
	}

	public HapiFileUpdate extendingExpiryBy(long secs) {
		expiryExtension = OptionalLong.of(secs);
		return this;
	}

	public HapiFileUpdate wacl(String name) {
		newWaclKey = Optional.of(name);
		return this;
	}

	public HapiFileUpdate contents(Function<HapiApiSpec, ByteString> fn) {
		contentFn = Optional.of(fn);
		return this;
	}

	public HapiFileUpdate settingProps(String path) {
		return settingProps(path, EMPTY_MAP);
	}

	public HapiFileUpdate settingProps(String path, Map<String, String> overrides) {
		basePropsFile = Optional.of(path);
		propOverrides = Optional.of(overrides);
		return this;
	}

	public HapiFileUpdate overridingProps(Map<String, String> overrides) {
		propOverrides = Optional.of(overrides);
		return this;
	}

	public HapiFileUpdate erasingProps(Set<String> tbd) {
		propDeletions = Optional.of(tbd);
		return this;
	}

	private Setting asSetting(String name, String value) {
		return Setting.newBuilder().setName(name).setValue(value).build();
	}

	public HapiFileUpdate contents(ByteString byteString) {
		newContents = Optional.of(byteString);
		return this;
	}

	public HapiFileUpdate contents(byte[] literal) {
		newContents = Optional.of(ByteString.copyFrom(literal));
		return this;
	}

	public HapiFileUpdate contents(String literal) {
		literalNewContents = Optional.of(literal);
		contents(literal.getBytes());
		return this;
	}

	public HapiFileUpdate path(String path) {
		newContentsPath = Optional.of(path);
		return this;
	}

	private Key emptyWacl() {
		return Key.newBuilder()
				.setKeyList(KeyList.getDefaultInstance())
				.build();
	}

	private Key badlyEncodedWacl() {
		return Key.newBuilder()
				.setKeyList(KeyList.newBuilder()
						.addKeys(Key.getDefaultInstance())
						.addKeys(Key.getDefaultInstance()))
				.build();
	}

	public HapiFileUpdate useBadWacl() {
		useBadlyEncodedWacl = true;
		return this;
	}
	public HapiFileUpdate useEmptyWacl() {
		useEmptyWacl = true;
		return this;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) throws Throwable {
		if (actualStatus != ResponseCodeEnum.SUCCESS) {
			return;
		}
		newWaclKey.ifPresent(k -> spec.registry().saveKey(file, spec.registry().getKey(k)));
		expiryExtension.ifPresent(extension -> {
			try {
				spec.registry().saveTimestamp(
						file,
						Timestamp.newBuilder().setSeconds(
								spec.registry().getTimestamp(file).getSeconds() + extension).build());
			} catch (Exception ignore) { }
		});
		if (file.equals(spec.setup().exchangeRatesName()) && newContents.isPresent()) {
			var newRateSet = ExchangeRateSet.parseFrom(newContents.get());
			spec.ratesProvider().updateRateSet(newRateSet);
		}

		if (verboseLoggingOn) {
			log.info("Updated file  {} with ID {}.", file, lastReceipt.getFileID());
		}

	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		Optional<Key> wacl = useBadlyEncodedWacl
				? Optional.of(badlyEncodedWacl())
				: (useEmptyWacl ? Optional.of(emptyWacl()) : newWaclKey.map(spec.registry()::getKey));
		if (newContentsPath.isPresent()) {
			newContents = Optional.of(ByteString.copyFrom(Files.toByteArray(new File(newContentsPath.get()))));
		} else if (contentFn.isPresent()) {
			newContents = Optional.of(contentFn.get().apply(spec));
		} else if (propOverrides.isPresent() || propDeletions.isPresent()) {
			if (propOverrides.isEmpty()) {
				propOverrides = Optional.of(Collections.emptyMap());
			}

			ServicesConfigurationList defaults = readBaseProps(spec);
			ServicesConfigurationList.Builder list = ServicesConfigurationList.newBuilder();
			Map<String, String> overrides = propOverrides.get();
			Map<String, String> defaultPairs = defaults.getNameValueList()
					.stream()
					.collect(Collectors.toMap(Setting::getName, Setting::getValue));

			Set<String> keys = new HashSet<>();
			defaults.getNameValueList()
					.stream()
					.map(Setting::getName)
					.filter(key -> !propDeletions.orElse(EMPTY_SET).contains(key))
					.forEach(keys::add);
			overrides.keySet().stream().forEach(keys::add);

			keys.forEach(key -> {
				if (overrides.containsKey(key))	{
					list.addNameValue(asSetting(key, overrides.get(key)));
				} else {
					list.addNameValue(asSetting(key, defaultPairs.get(key)));
				}
			});

			newContents = Optional.of(list.build().toByteString());
		}

		long nl = -1;
		if (expiryExtension.isPresent()) {
			try {
				var oldExpiry = spec.registry().getTimestamp(file).getSeconds();
				nl = oldExpiry - Instant.now().getEpochSecond() + expiryExtension.getAsLong();
			} catch (Exception ignore) { }
		} else if (lifetimeSecs.isPresent()) {
			nl = lifetimeSecs.get();
		}
		final OptionalLong newLifetime = (nl == -1) ? OptionalLong.empty() : OptionalLong.of(nl);
		var fid = TxnUtils.asFileId(file, spec);
		FileUpdateTransactionBody opBody = spec
				.txns()
				.<FileUpdateTransactionBody, FileUpdateTransactionBody.Builder>body(
						FileUpdateTransactionBody.class, builder -> {
							builder.setFileID(fid);
							wacl.ifPresent(k -> builder.setKeys(k.getKeyList()));
							newContents.ifPresent(b -> builder.setContents(b));
							newLifetime.ifPresent(s -> builder.setExpirationTime(TxnFactory.expiryGiven(s)));
						}
				);
		return builder -> builder.setFileUpdate(opBody);
	}

	private ServicesConfigurationList readBaseProps(HapiApiSpec spec) {
		if (dropUnmentionedProperties) {
			return ServicesConfigurationList.getDefaultInstance();
		}

		if (!basePropsFile.isPresent()) {
			if (!file.equals(HapiApiSuite.API_PERMISSIONS) && !file.equals(HapiApiSuite.APP_PROPERTIES)) {
				throw new IllegalStateException("Property overrides make no sense for file '" + file + "'!");
			}
			HapiGetFileContents subOp = QueryVerbs.getFileContents(file);
			CustomSpecAssert.allRunFor(spec, subOp);
			try {
				byte[] bytes = subOp.getResponse().getFileGetContents().getFileContents().getContents().toByteArray();
				ServicesConfigurationList defaults = ServicesConfigurationList.parseFrom(bytes);
				return defaults;
			} catch (Exception e) {
				log.error("No available defaults for " + file + " --- aborting!", e);
				throw new IllegalStateException("Property overrides via fileUpdate must have available defaults!");
			}
		} else {
			String defaultsPath = basePropsFile.get();
			try {
				byte[] bytes = java.nio.file.Files.readAllBytes(new File(defaultsPath).toPath());
				ServicesConfigurationList defaults = ServicesConfigurationList.parseFrom(bytes);
				return defaults;
			} catch (Exception e) {
				log.error("No available defaults for " + file + " --- aborting!", e);
				throw new IllegalStateException("Property overrides via fileUpdate must have available defaults!");
			}
		}
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		List<Function<HapiApiSpec, Key>> signers = new ArrayList<>(oldDefaults());
		if (newWaclKey.isPresent()) {
			signers.add(spec -> spec.registry().getKey(newWaclKey.get()));
		}
		return signers;
	}

	private List<Function<HapiApiSpec, Key>> oldDefaults() {
		return List.of(
			spec -> spec.registry().getKey(effectivePayer(spec)),
			spec -> spec.registry().getKey(file));
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getFileSvcStub(targetNodeFor(spec), useTls)::updateFile;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		Timestamp newExpiry = TxnFactory.expiryGiven(lifetimeSecs.orElse(spec.setup().defaultExpirationSecs()));
		Timestamp oldExpiry = bestKnownCurrentExpiry(spec);
		final Timestamp expiry = TxnUtils.inConsensusOrder(oldExpiry, newExpiry) ? newExpiry : oldExpiry;
		FeeCalculator.ActivityMetrics metricsCalc = (txBody, sigUsage) ->
				fileFees.getFileUpdateTxFeeMatrices(txBody, expiry, sigUsage);
		var saferTxnBuilder = CommonUtils.extractTransactionBody(txn).toBuilder();
		saferTxnBuilder.getFileUpdateBuilder().setContents(RANDOM_4K);
		final var saferTxn = txn.toBuilder().setBodyBytes(saferTxnBuilder.build().toByteString()).build();
		return spec.fees().forActivityBasedOp(HederaFunctionality.FileUpdate, metricsCalc, saferTxn, numPayerKeys);
	}

	private Timestamp bestKnownCurrentExpiry(HapiApiSpec spec) throws Throwable {
		Timestamp oldExpiry = null;
		if (spec.registry().hasTimestamp(file)) {
			oldExpiry = spec.registry().getTimestamp(file);
		}
		if (oldExpiry == null) {
			oldExpiry = TxnUtils.currExpiry(file, spec);
		}
		return oldExpiry;
	}

	@Override
	protected HapiFileUpdate self() {
		return this;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("fileName", file);
		newContentsPath.ifPresent(p -> helper.add("path", p));
		literalNewContents.ifPresent(l -> helper.add("contents", l));
		return helper;
	}
}
