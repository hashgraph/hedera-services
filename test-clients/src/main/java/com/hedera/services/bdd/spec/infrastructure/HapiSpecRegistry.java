package com.hedera.services.bdd.spec.infrastructure;

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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.listeners.TokenAccountRegistryRel;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCall;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCallLocal;
import com.hedera.services.bdd.spec.infrastructure.meta.SupportedContract;
import com.hedera.services.bdd.spec.stats.OpObs;
import com.hedera.services.bdd.spec.stats.ThroughputObs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.utility.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccountString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asScheduleString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTokenString;
import static com.hedera.services.bdd.spec.keys.KeyFactory.payerKey;
import static com.hedera.services.bdd.suites.HapiApiSuite.DEFAULT_CONTRACT_RECEIVER;
import static com.hedera.services.bdd.suites.HapiApiSuite.DEFAULT_CONTRACT_SENDER;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class HapiSpecRegistry {
	static final Logger log = LogManager.getLogger(HapiSpecRegistry.class);

	private final Map<String, Object> registry = new HashMap<>();
	private final HapiSpecSetup setup;
	private final List<OpObs> obs = new ArrayList<>();
	private final List<ThroughputObs> throughputObs = new ArrayList<>();
	private Map<Class, List<RegistryChangeListener>> listenersByType = new HashMap<>();

	private static final Integer ZERO = 0;

	public HapiSpecRegistry(HapiSpecSetup setup) throws Exception {
		this.setup = setup;

		final var key = payerKey(setup);
		final var genesisKey = asPublicKey(CommonUtils.hex(key.getAbyte()));

		saveAccountId(setup.genesisAccountName(), setup.genesisAccount());
		saveKey(setup.genesisAccountName(), asKeyList(genesisKey));
		saveAccountId(setup.defaultPayerName(), setup.defaultPayer());
		saveKey(setup.defaultPayerName(), asKeyList(genesisKey));
		// The default contract sender is the default payer unless using Ethereum transactions
		saveAccountId(DEFAULT_CONTRACT_SENDER, setup.defaultPayer());
		saveKey(DEFAULT_CONTRACT_SENDER, asKeyList(genesisKey));
		saveAccountId(DEFAULT_CONTRACT_RECEIVER, setup.fundingAccount());
		saveKey(DEFAULT_CONTRACT_RECEIVER, asKeyList(genesisKey));
		saveAccountId(setup.defaultNodeName(), setup.defaultNode());
		saveAccountId(setup.fundingAccountName(), setup.fundingAccount());
		saveContractId(setup.invalidContractName(), setup.invalidContract());
		saveAccountId(setup.stakingRewardAccountName(), setup.stakingRewardAccount());
		saveAccountId(setup.nodeRewardAccountName(), setup.nodeRewardAccount());

		saveAccountId(setup.strongControlName(), setup.strongControlAccount());
		saveKey(setup.strongControlName(), asKeyList(genesisKey));
		saveAccountId(setup.systemDeleteAdminName(), setup.systemDeleteAdmin());
		saveKey(setup.systemDeleteAdminName(), asKeyList(genesisKey));
		saveAccountId(setup.systemUndeleteAdminName(), setup.systemUndeleteAdmin());
		saveKey(setup.systemUndeleteAdminName(), asKeyList(genesisKey));
		saveAccountId(setup.freezeAdminName(), setup.freezeAdminId());
		saveKey(setup.freezeAdminName(), asKeyList(genesisKey));

		/* (system file 1) :: Address Book */
		saveFileId(setup.addressBookName(), setup.addressBookId());
		saveKey(setup.addressBookName(), asKeyList(genesisKey));
		saveAccountId(setup.addressBookControlName(), setup.addressBookControl());
		saveKey(setup.addressBookControlName(), asKeyList(genesisKey));
		/* (system file 2) :: Node Details */
		saveFileId(setup.nodeDetailsName(), setup.nodeDetailsId());
		saveKey(setup.nodeDetailsName(), asKeyList(genesisKey));
		/* (system file 3) :: Exchange Rates */
		saveFileId(setup.exchangeRatesName(), setup.exchangeRatesId());
		saveKey(setup.exchangeRatesName(), asKeyList(genesisKey));
		saveAccountId(setup.exchangeRatesControlName(), setup.exchangeRatesControl());
		saveKey(setup.exchangeRatesControlName(), asKeyList(genesisKey));
		/* (system 4) :: Fee Schedule */
		saveFileId(setup.feeScheduleName(), setup.feeScheduleId());
		saveKey(setup.feeScheduleName(), asKeyList(genesisKey));
		saveAccountId(setup.feeScheduleControlName(), setup.feeScheduleControl());
		saveKey(setup.feeScheduleControlName(), asKeyList(genesisKey));
		/* (system 5) :: API Permissions */
		saveFileId(setup.apiPermissionsFile(), setup.apiPermissionsId());
		saveKey(setup.apiPermissionsFile(), asKeyList(genesisKey));
		/* (system 6) :: App Properties */
		saveFileId(setup.appPropertiesFile(), setup.appPropertiesId());
		saveKey(setup.appPropertiesFile(), asKeyList(genesisKey));
		/* (system 7) :: Update Feature */
		saveFileId(setup.updateFeatureName(), setup.updateFeatureId());
		saveKey(setup.updateFeatureName(), asKeyList(genesisKey));
		/* (system 8) :: Throttle Definitions */
		saveFileId(setup.throttleDefinitionsName(), setup.throttleDefinitionsId());
		saveKey(setup.throttleDefinitionsName(), asKeyList(genesisKey));

		saveKey(HapiApiSuite.NONSENSE_KEY, nonsenseKey());
	}

	private Key nonsenseKey() {
		return Key.getDefaultInstance();
	}

	private Key asPublicKey(String pubKeyHex) {
		return Key.newBuilder()
				.setEd25519(ByteString.copyFrom(CommonUtils.unhex(pubKeyHex)))
				.build();
	}

	private Key asKeyList(Key key) {
		return Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(key)).build();
	}

	public void register(RegistryChangeListener<?> listener) {
		Class<?> type = listener.forType();
		listenersByType.computeIfAbsent(type, ignore -> new ArrayList<>()).add(listener);
	}

	synchronized public void record(OpObs stat) {
		obs.add(stat);
	}

	public List<OpObs> stats() {
		return obs;
	}

	public void saveThroughputObs(ThroughputObs obs) {
		put(obs.getName(), obs);
		throughputObs.add(obs);
	}

	public ThroughputObs getThroughputObs(String name) {
		return get(name, ThroughputObs.class);
	}

	public List<ThroughputObs> throughputObs() {
		return throughputObs;
	}

	public void saveContractChoice(String name, SupportedContract choice) {
		put(name, choice);
	}

	public SupportedContract getContractChoice(String name) {
		return get(name, SupportedContract.class);
	}

	public boolean hasContractChoice(String name) {
		return hasVia(this::getContractChoice, name);
	}

	public void removeContractChoice(String name) {
		remove(name, SupportedContract.class);
	}

	public ActionableContractCall getActionableCall(String name) {
		return get(name, ActionableContractCall.class);
	}

	public void saveActionableCall(String name, ActionableContractCall call) {
		put(name, call);
	}

	public void removeActionableCall(String name) {
		remove(name, ActionableContractCall.class);
	}

	public void saveActionableLocalCall(String name, ActionableContractCallLocal call) {
		put(name, call);
	}

	public void removeActionableLocalCall(String name) {
		remove(name, ActionableContractCallLocal.class);
	}

	public ActionableContractCallLocal getActionableLocalCall(String name) {
		return get(name, ActionableContractCallLocal.class);
	}

	public void saveBalanceSnapshot(String name, Long balance) {
		put(name, balance);
	}

	public long getBalanceSnapshot(String name) {
		return get(name, Long.class);
	}

	public boolean hasTimestamp(String label) {
		return registry.containsKey(full(label, Timestamp.class));
	}

	public Timestamp getTimestamp(String label) {
		return get(label, Timestamp.class);
	}

	public void saveTimestamp(String label, Timestamp when) {
		put(label, when, Timestamp.class);
	}

	public void removeTimestamp(String label) {
		try {
			remove(label, Timestamp.class);
		} catch (Exception ignore) {
		}
	}

	public void saveKey(String name, Key key) {
		put(name, key, Key.class);
	}

	public void forgetAdminKey(String name) {
		remove(name + "Admin", Key.class);
	}

	public void saveAdminKey(String name, Key key) {
		put(name + "Admin", key, Key.class);
	}

	public boolean hasAdminKey(String name) {
		return has(name + "Admin", Key.class);
	}

	public void saveFreezeKey(String name, Key key) {
		put(name + "Freeze", key, Key.class);
	}

	public boolean hasFeeScheduleKey(String name) {
		return has(name + "FeeSchedule", Key.class);
	}

	public void forgetFeeScheduleKey(String name) {
		remove(name + "FeeSchedule", Key.class);
	}

	public void saveFeeScheduleKey(String name, Key key) {
		put(name + "FeeSchedule", key, Key.class);
	}

	public Key getFeeScheduleKey(String name) {
		return get(name + "FeeSchedule", Key.class);
	}

	public void savePauseKey(String name, Key key) {
		put(name + "Pause", key, Key.class);
	}

	public boolean hasPauseKey(String name) {
		return has(name + "Pause", Key.class);
	}

	public Key getPauseKey(String name) {
		return get(name + "Pause", Key.class);
	}

	public void forgetPauseKey(String name) {
		remove(name + "Pause", Key.class);
	}

	public boolean hasFreezeKey(String name) {
		return has(name + "Freeze", Key.class);
	}

	public void forgetFreezeKey(String name) {
		remove(name + "Freeze", Key.class);
	}

	public void saveExpiry(String name, Long value) {
		put(name + "Expiry", value, Long.class);
	}

	public void saveCreationTime(String name, Timestamp value) {
		put(name + "CreationTime", value, Timestamp.class);
	}

	public void saveSupplyKey(String name, Key key) {
		put(name + "Supply", key, Key.class);
	}

	public boolean hasSupplyKey(String name) {
		return has(name + "Supply", Key.class);
	}

	public void saveWipeKey(String name, Key key) {
		put(name + "Wipe", key, Key.class);
	}

	public boolean hasWipeKey(String name) {
		return has(name + "Wipe", Key.class);
	}

	public void forgetWipeKey(String name) {
		remove(name + "Wipe", Key.class);
	}

	public void forgetSupplyKey(String name) {
		remove(name + "Supply", Key.class);
	}

	public boolean hasKycKey(String name) {
		return has(name + "Kyc", Key.class);
	}

	public void saveKycKey(String name, Key key) {
		put(name + "Kyc", key, Key.class);
	}

	public void forgetKycKey(String name) {
		remove(name + "Kyc", Key.class);
	}

	public void saveSymbol(String token, String symbol) {
		put(token + "Symbol", symbol, String.class);
	}

	public void saveName(String token, String name) {
		put(token + "Name", name, String.class);
	}

	public void saveMemo(String entity, String memo) {
		put(entity + "Memo", memo, String.class);
	}

	public String getMemo(String entity) {
		return get(entity + "Memo", String.class);
	}

	public String getSymbol(String token) {
		return get(token + "Symbol", String.class);
	}

	public void forgetSymbol(String token) {
		remove(token + "Symbol", String.class);
	}

	public String getName(String token) {
		return get(token + "Name", String.class);
	}

	public void forgetName(String token) {
		remove(token + "Name", String.class);
	}

	public Key getKey(String name) {
		return get(name, Key.class);
	}

	public Key getAdminKey(String name) {
		return get(name + "Admin", Key.class);
	}

	public Key getFreezeKey(String name) {
		return get(name + "Freeze", Key.class);
	}

	public Key getSupplyKey(String name) {
		return get(name + "Supply", Key.class);
	}

	public Key getWipeKey(String name) {
		return get(name + "Wipe", Key.class);
	}

	public Key getKycKey(String name) {
		return get(name + "Kyc", Key.class);
	}

	public Long getExpiry(String name) {
		return get(name + "Expiry", Long.class);
	}

	public Timestamp getCreationTime(String name) {
		return get(name + "CreationTime", Timestamp.class);
	}

	public boolean hasKey(String name) {
		return hasVia(this::getKey, name);
	}

	public void removeKey(String name) {
		try {
			remove(name, Key.class);
		} catch (Exception ignore) {
		}
	}

	public void saveTopicMeta(String name, ConsensusCreateTopicTransactionBody meta, Long approxConsensusTime) {
		put(name, meta);
		put(name, approxConsensusTime + meta.getAutoRenewPeriod().getSeconds() + 60);
	}

	public void saveTopicMeta(String name, ConsensusUpdateTopicTransactionBody txn) {
		ConsensusCreateTopicTransactionBody.Builder meta;
		if (hasTopicMeta(name)) {
			meta = getTopicMeta(name).toBuilder();
		} else {
			meta = ConsensusCreateTopicTransactionBody.newBuilder();
		}
		if (txn.hasAdminKey()) {
			meta.setAdminKey(txn.getAdminKey());
		}
		if (txn.hasAutoRenewAccount()) {
			meta.setAutoRenewAccount(txn.getAutoRenewAccount());
		}
		if (txn.hasAutoRenewPeriod()) {
			meta.setAutoRenewPeriod(txn.getAutoRenewPeriod());
		}
		if (txn.hasSubmitKey()) {
			meta.setSubmitKey(txn.getSubmitKey());
		}
		if (txn.hasMemo()) {
			meta.setMemo(txn.getMemo().getValue());
		}
		put(name, meta.build());
		if (txn.hasExpirationTime()) {
			put(name, txn.getExpirationTime().getSeconds());
		}
	}

	public ConsensusCreateTopicTransactionBody getTopicMeta(String name) {
		return get(name, ConsensusCreateTopicTransactionBody.class);
	}

	public long getTopicExpiry(String name) {
		return get(name, Long.class);
	}

	public boolean hasTopicMeta(String name) {
		return hasVia(this::getTopicMeta, name);
	}

	public void saveBytes(String name, ByteString bytes) {
		put(name, bytes, ByteString.class);
	}

	public byte[] getBytes(String name) {
		return get(name, ByteString.class).toByteArray();
	}

	public void saveAmount(String name, Long amount) {
		put(name, amount);
	}

	public Long getAmount(String name) {
		return get(name, Long.class);
	}

	public void saveIntValue(String name, Integer intVal) {
		put(name, intVal);
	}

	public Integer getIntValue(String name) {
		return get(name, Integer.class);
	}

	public void saveFloatValue(String name, Float floatVal) {
		put(name, floatVal);
	}

	public Float getFloatValue(String name) {
		return get(name, Float.class);
	}

	public void saveDoubleValue(String name, Double doubleVal) {
		put(name, doubleVal);
	}

	public Double getDoubleValue(String name) {
		return get(name, Double.class);
	}

	public void saveSigRequirement(String name, Boolean isRequired) {
		put(name, isRequired);
	}

	public void removeSigRequirement(String name) {
		remove(name, Boolean.class);
	}

	public boolean isSigRequired(String name) {
		return registry.containsKey(full(name, Boolean.class))
				? get(name, Boolean.class)
				: setup.defaultReceiverSigRequired();
	}

	public boolean hasSigRequirement(String name) {
		return hasVia(this::isSigRequired, name);
	}

	private <T> boolean hasVia(Function<String, T> tGetter, String thing) {
		try {
			tGetter.apply(thing);
			return true;
		} catch (Throwable ignore) {
			return false;
		}
	}

	public void saveScheduledTxn(String name, SchedulableTransactionBody scheduledTxn) {
		put(name, scheduledTxn);
	}

	public SchedulableTransactionBody getScheduledTxn(String name) {
		return get(name, SchedulableTransactionBody.class);
	}

	public void saveTxnId(String name, TransactionID txnId) {
		put(name, txnId);
	}

	public TransactionID getTxnId(String name) {
		return get(name, TransactionID.class);
	}

	public void saveAccountId(String name, AccountID id) {
		put(name, id);
		put(asAccountString(id), name);
	}

	public void saveScheduleId(String name, ScheduleID id) {
		put(name, id);
		put(asScheduleString(id), name);
	}

	public ScheduleID getScheduleId(String name) {
		return get(name, ScheduleID.class);
	}

	public void saveTokenId(String name, TokenID id) {
		put(name, id);
		put(asTokenString(id), name);
	}

	public void saveAccountAlias(String alias, AccountID id) {
		put(alias, id);
	}

	public AccountID getAccountAlias(String name) {
		return get(name, AccountID.class);
	}

	public void forgetTokenId(String name) {
		try {
			var id = getTokenID(name);
			remove(name, TokenID.class);
			remove(asTokenString(id), String.class);
		} catch (Throwable ignore) {
		}
	}

	public void saveTokenRel(String account, String token) {
		put(tokenRelKey(account, token), new TokenAccountRegistryRel(token, account));
	}

	private String tokenRelKey(String account, String token) {
		return account + "|" + token;
	}

	public void saveTreasury(String token, String treasury) {
		put(token + "Treasury", treasury);
	}

	public void forgetTreasury(String token) {
		remove(token + "Treasury", String.class);
	}

	public String getTreasury(String token) {
		return get(token + "Treasury", String.class);
	}

	public void setRecharging(String account, long amount) {
		put(account, Boolean.TRUE);
		put(account + "Recharge", amount);
	}

	public boolean isRecharging(String account) {
		return registry.containsKey(full(account, Boolean.class));
	}

	public Long getRechargeAmount(String account) {
		return get(account + "Recharge", Long.class);
	}

	public void setRechargingTime(String account, Instant time) {
		put(account + "RechargeTime", time);
	}

	public Instant getRechargingTime(String account) {
		try {
			return get(account + "RechargeTime", Instant.class);
		} catch (Exception ignore) {
			return Instant.MIN;
		}
	}

	public void setRechargingWindow(String account, Integer seconds) {
		put(account + "RechargeWindow", seconds);
	}

	public boolean hasRechargingWindow(String rechargingAccount) {
		return registry.get(full(rechargingAccount + "RechargeWindow", Integer.class)) != null;
	}

	public Integer getRechargingWindow(String account) {
		return getOrElse(account + "RechargeWindow", Integer.class, ZERO);
	}

	public boolean hasTokenId(String name) {
		return hasVia(this::getTokenID, name);
	}

	public boolean hasAccountId(String name) {
		return registry.get(full(name, AccountID.class)) != null;
	}

	public AccountID getAccountID(String name) {
		return get(name, AccountID.class);
	}

	public AccountID aliasIdFor(String keyName) {
		final var key = get(keyName, Key.class);
		return AccountID.newBuilder()
				.setAlias(key.toByteString())
				.build();
	}

	public String getAccountIdName(AccountID account) {
		return get(asAccountString(account), String.class);
	}

	public boolean hasAccountIdName(AccountID accountId) {
		return registry.get(full(asAccountString(accountId), String.class)) != null;
	}

	public void removeAccount(String name) {
		try {
			var id = getAccountID(name);
			remove(name, AccountID.class);
			remove(asAccountString(id), String.class);
		} catch (Exception ignore) {
		}
	}

	public void saveTopicId(String name, TopicID id) {
		put(name, id);
		put(HapiPropertySource.asTopicString(id), name);
	}

	public TopicID getTopicID(String name) {
		return get(name, TopicID.class);
	}

	public TokenID getTokenID(String name) {
		return get(name, TokenID.class);
	}

	public boolean hasFileId(String name) {
		return hasVia(this::getFileId, name);
	}

	public void saveFileId(String name, FileID id) {
		put(name, id);
	}

	public FileID getFileId(String name) {
		return get(name, FileID.class);
	}

	public void removeFileId(String name) {
		try {
			remove(name, FileID.class);
		} catch (Exception ignore) {
		}
	}

	public void saveContractList(String name, List<ContractID> list) {
		long listSize = list.size();
		saveAmount(name + "Size", listSize);
		for (int i = 0; i < listSize; i++) {
			saveContractId(name + i, list.get(i));
		}
	}

	public void saveContractId(String name, ContractID id) {
		put(name, id);
	}

	public ContractID getContractId(String name) {
		return get(name, ContractID.class);
	}

	public boolean hasContractId(String name) {
		return registry.containsKey(full(name, ContractID.class));
	}

	public void removeContractId(String name) {
		try {
			remove(name, ContractID.class);
		} catch (Exception ignore) {
		}
	}

	public void saveContractInfo(String name, ContractGetInfoResponse.ContractInfo info) {
		put(name, info);
	}

	public void removeContractInfo(String name) {
		try {
			remove(name, ContractGetInfoResponse.ContractInfo.class);
		} catch (Exception ignore) {
		}
	}

	public TransactionRecord getTransactionRecord(String name) {
		return get(name, TransactionRecord.class);
	}


	public void saveTransactionRecord(String name, TransactionRecord txnRecord) {
		put(name, txnRecord);
	}

	public void removeTransactionRecord(String name) {
		try {
			remove(name, TransactionRecord.class);
		} catch (Exception ignore) {
		}
	}

	public ContractGetInfoResponse.ContractInfo getContractInfo(String name) {
		return get(name, ContractGetInfoResponse.ContractInfo.class);
	}


	public void saveFileInfo(String name, FileGetInfoResponse.FileInfo info) {
		put(name, info);
	}

	public void removeFileInfo(String name) {
		try {
			remove(name, ContractGetInfoResponse.ContractInfo.class);
		} catch (Exception ignore) {
		}
	}

	public FileGetInfoResponse.FileInfo getFileInfo(String name) {
		return get(name, FileGetInfoResponse.FileInfo.class);
	}

	public void saveAccountInfo(String name, CryptoGetInfoResponse.AccountInfo info) {
		put(name, info);
	}

	public void saveAccountDetails(String name, GetAccountDetailsResponse.AccountDetails details) {
		put(name, details);
	}

	public void removeAccountInfo(String name) {
		try {
			remove(name, CryptoGetInfoResponse.AccountInfo.class);
		} catch (Exception ignore) {
		}
	}

	public CryptoGetInfoResponse.AccountInfo getAccountInfo(String name) {
		return get(name, CryptoGetInfoResponse.AccountInfo.class);
	}

	public GetAccountDetailsResponse.AccountDetails getAccountDetails(String name) {
		return get(name, GetAccountDetailsResponse.AccountDetails.class);
	}

	public <T> T getId(String name, Class<T> type) {
		return get(name, type);
	}

	private synchronized void remove(String name, Class<?> type, Optional<HapiSpecOperation> cause) {
		registry.remove(full(name, type));
		notifyAllOnDelete(type, name, cause);
	}

	private synchronized void remove(String name, Class<?> type) {
		remove(name, type, Optional.empty());
	}

	private void notifyAllOnDelete(Class type, String name, Optional<HapiSpecOperation> cause) {
		Optional.ofNullable(listenersByType.get(type)).ifPresent(a -> a.forEach(l -> l.onDelete(name, cause)));
	}

	private synchronized void put(String name, Object obj, Optional<HapiSpecOperation> cause, Class type) {
		if (obj == null) {
			return;
		}
		registry.put(full(name, type), obj);
		notifyAllOnPut(type, name, obj, cause);
	}

	private synchronized void put(String name, Object obj, Class<?> type) {
		put(name, obj, Optional.empty(), type);
	}

	private synchronized void put(String name, Object obj) {
		put(name, obj, obj.getClass());
	}

	private void notifyAllOnPut(Class type, String name, Object value, Optional<HapiSpecOperation> cause) {
		Optional.ofNullable(listenersByType.get(type)).ifPresent(a -> a.forEach(l -> {
			Class<?> lType = l.forType();
			notifyOnPut(l, lType, name, value, cause);
		}));
	}

	private <T> void notifyOnPut(
			RegistryChangeListener<T> listener,
			Class<T> type,
			String name,
			Object value,
			Optional<HapiSpecOperation> cause
	) {
		listener.onPut(name, type.cast(value), cause);
	}

	private synchronized <T> T get(String name, Class<T> type) {
		Object v = registry.get(full(name, type));
		if (v == null) {
			throw new RegistryNotFound("Missing " + type.getSimpleName() + " '" + name + "'!");
		}
		return type.cast(v);
	}

	private <T> boolean has(String name, Class<T> type) {
		return registry.containsKey(full(name, type));
	}

	private synchronized <T> T getOrElse(String name, Class<T> type, T defaultValue) {
		Object v = registry.get(full(name, type));
		if (v == null) {
			return defaultValue;
		}
		return type.cast(v);
	}

	private String full(String name, Class<?> type) {
		String typeName = type.getSimpleName();
		return typeName + "-" + name;
	}

	public Map<Class, Long> typeCounts() {
		return registry.values()
				.stream()
				.collect(groupingBy(Object::getClass, counting()));
	}

	public List<String> stringValues() {
		return registry.entrySet()
				.stream()
				.filter(entry -> entry.getValue().getClass().equals(String.class))
				.map(entry -> String.format("%s -> %s", entry.getKey(), entry.getValue().toString()))
				.collect(toList());
	}
}
