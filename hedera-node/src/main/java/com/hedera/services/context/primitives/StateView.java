package com.hedera.services.context.primitives;

/*-
 * ‌
 * Hedera Services Node
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

import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.contracts.sources.AddressKeyedMapFactory;
import com.hedera.services.files.DataMapFactory;
import com.hedera.services.files.MetadataMapFactory;
import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.RawTokenRelationship;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleInfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;
import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static com.hedera.services.state.merkle.MerkleEntityId.fromContractId;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.store.schedule.ExceptionalScheduleStore.NOOP_SCHEDULE_STORE;
import static com.hedera.services.store.schedule.ScheduleStore.MISSING_SCHEDULE;
import static com.hedera.services.store.tokens.ExceptionalTokenStore.NOOP_TOKEN_STORE;
import static com.hedera.services.store.tokens.TokenStore.MISSING_TOKEN;
import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddressHex;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static java.util.Collections.unmodifiableMap;

public class StateView {
	private static final Logger log = LogManager.getLogger(StateView.class);

	static BiFunction<StateView, AccountID, List<TokenRelationship>> tokenRelsFn = StateView::tokenRels;

	private static final byte[] EMPTY_BYTES = new byte[0];
	public static final JKey EMPTY_WACL = new JKeyList();
	public static final MerkleToken GONE_TOKEN = new MerkleToken(0L, 0L, 0, "", "", false, false, MISSING_ENTITY_ID);

	public static final FCMap<MerkleEntityId, MerkleTopic> EMPTY_TOPICS =
			new FCMap<>();
	public static final Supplier<FCMap<MerkleEntityId, MerkleTopic>> EMPTY_TOPICS_SUPPLIER =
			() -> EMPTY_TOPICS;

	public static final FCMap<MerkleEntityId, MerkleAccount> EMPTY_ACCOUNTS =
			new FCMap<>();
	public static final Supplier<FCMap<MerkleEntityId, MerkleAccount>> EMPTY_ACCOUNTS_SUPPLIER =
			() -> EMPTY_ACCOUNTS;

	public static final FCMap<MerkleBlobMeta, MerkleOptionalBlob> EMPTY_STORAGE =
			new FCMap<>();
	public static final Supplier<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> EMPTY_STORAGE_SUPPLIER =
			() -> EMPTY_STORAGE;

	public static final FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> EMPTY_TOKEN_ASSOCIATIONS =
			new FCMap<>();
	public static final Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> EMPTY_TOKEN_ASSOCS_SUPPLIER =
			() -> EMPTY_TOKEN_ASSOCIATIONS;

	public static final StateView EMPTY_VIEW = new StateView(
			EMPTY_TOPICS_SUPPLIER,
			EMPTY_ACCOUNTS_SUPPLIER,
			null, null);

	Map<byte[], byte[]> contractStorage;
	Map<byte[], byte[]> contractBytecode;
	Map<FileID, byte[]> fileContents;
	Map<FileID, HFileMeta> fileAttrs;
	private final TokenStore tokenStore;
	private final ScheduleStore scheduleStore;
	private final Supplier<MerkleDiskFs> diskFs;
	private final Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;
	private final Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenAssociations;

	private final NodeLocalProperties properties;

	public StateView(
			Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			NodeLocalProperties properties,
			Supplier<MerkleDiskFs> diskFs
	) {
		this(NOOP_TOKEN_STORE, NOOP_SCHEDULE_STORE, topics, accounts, EMPTY_STORAGE_SUPPLIER,
				EMPTY_TOKEN_ASSOCS_SUPPLIER, diskFs, properties);
	}

	public StateView(
			TokenStore tokenStore,
			ScheduleStore scheduleStore,
			Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			NodeLocalProperties properties,
			Supplier<MerkleDiskFs> diskFs
	) {
		this(tokenStore, scheduleStore, topics, accounts, EMPTY_STORAGE_SUPPLIER, EMPTY_TOKEN_ASSOCS_SUPPLIER, diskFs,
				properties);
	}

	public StateView(
			TokenStore tokenStore,
			ScheduleStore scheduleStore,
			Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			Supplier<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> storage,
			Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenAssociations,
			Supplier<MerkleDiskFs> diskFs,
			NodeLocalProperties properties
	) {
		this.topics = topics;
		this.accounts = accounts;
		this.tokenStore = tokenStore;
		this.tokenAssociations = tokenAssociations;
		this.scheduleStore = scheduleStore;

		Map<String, byte[]> blobStore = unmodifiableMap(new FcBlobsBytesStore(MerkleOptionalBlob::new, storage));

		fileContents = DataMapFactory.dataMapFrom(blobStore);
		fileAttrs = MetadataMapFactory.metaMapFrom(blobStore);
		contractStorage = AddressKeyedMapFactory.storageMapFrom(blobStore);
		contractBytecode = AddressKeyedMapFactory.bytecodeMapFrom(blobStore);
		this.properties = properties;
		this.diskFs = diskFs;
	}

	public static List<TokenRelationship> tokenRels(StateView view, AccountID id) {
		var account = view.accounts().get(fromAccountId(id));
		List<TokenRelationship> relationships = new ArrayList<>();
		var tokenIds = account.tokens().asIds();
		for (TokenID tId : tokenIds) {
			var optionalToken = view.tokenWith(tId);
			var effectiveToken = optionalToken.orElse(GONE_TOKEN);
			var relKey = fromAccountTokenRel(id, tId);
			var relationship = view.tokenAssociations().get().get(relKey);
			relationships.add(new RawTokenRelationship(
					relationship.getBalance(),
					tId.getShardNum(),
					tId.getRealmNum(),
					tId.getTokenNum(),
					relationship.isFrozen(),
					relationship.isKycGranted()
			).asGrpcFor(effectiveToken));
		}
		return relationships;
	}

	public Optional<HFileMeta> attrOf(FileID id) {
		return Optional.ofNullable(fileAttrs.get(id));
	}

	public Optional<byte[]> contentsOf(FileID id) {
		if (diskFs.get().contains(id)) {
			return Optional.ofNullable(diskFs.get().contentsOf(id));
		} else {
			return Optional.ofNullable(fileContents.get(id));
		}
	}

	public Optional<byte[]> bytecodeOf(ContractID id) {
		return Optional.ofNullable(contractBytecode.get(asSolidityAddress(id)));
	}

	public Optional<byte[]> storageOf(ContractID id) {
		return Optional.ofNullable(contractStorage.get(asSolidityAddress(id)));
	}

	public Optional<MerkleToken> tokenWith(TokenID id) {
		return !tokenStore.exists(id)
				? Optional.empty()
				: Optional.of(tokenStore.get(id));
	}

	public Optional<TokenInfo> infoForToken(TokenID tokenID) {
		try {
			var id = tokenStore.resolve(tokenID);
			if (id == MISSING_TOKEN) {
				return Optional.empty();
			}
			var token = tokenStore.get(id);
			var info = TokenInfo.newBuilder()
					.setTokenId(id)
					.setDeleted(token.isDeleted())
					.setSymbol(token.symbol())
					.setName(token.name())
					.setTreasury(token.treasury().toGrpcAccountId())
					.setTotalSupply(token.totalSupply())
					.setDecimals(token.decimals())
					.setExpiry(Timestamp.newBuilder().setSeconds(token.expiry()));

			var adminCandidate = token.adminKey();
			adminCandidate.ifPresent(k -> info.setAdminKey(asKeyUnchecked(k)));

			var freezeCandidate = token.freezeKey();
			freezeCandidate.ifPresentOrElse(k -> {
				info.setDefaultFreezeStatus(tfsFor(token.accountsAreFrozenByDefault()));
				info.setFreezeKey(asKeyUnchecked(k));
			}, () -> info.setDefaultFreezeStatus(TokenFreezeStatus.FreezeNotApplicable));

			var kycCandidate = token.kycKey();
			kycCandidate.ifPresentOrElse(k -> {
				info.setDefaultKycStatus(tksFor(token.accountsKycGrantedByDefault()));
				info.setKycKey(asKeyUnchecked(k));
			}, () -> info.setDefaultKycStatus(TokenKycStatus.KycNotApplicable));

			var supplyCandidate = token.supplyKey();
			supplyCandidate.ifPresent(k -> info.setSupplyKey(asKeyUnchecked(k)));
			var wipeCandidate = token.wipeKey();
			wipeCandidate.ifPresent(k -> info.setWipeKey(asKeyUnchecked(k)));

			if (token.hasAutoRenewAccount()) {
				info.setAutoRenewAccount(token.autoRenewAccount().toGrpcAccountId());
				info.setAutoRenewPeriod(Duration.newBuilder().setSeconds(token.autoRenewPeriod()));
			}

			return Optional.of(info.build());
		} catch (Exception unexpected) {
			log.warn(
					"Unexpected failure getting info for token {}!",
					readableId(tokenID),
					unexpected);
			return Optional.empty();
		}
	}

	public Optional<ScheduleInfo> infoForSchedule(ScheduleID scheduleID) {
		try {
			var id = scheduleStore.resolve(scheduleID);
			if (id == MISSING_SCHEDULE) {
				return Optional.empty();
			}
			var schedule = scheduleStore.get(id);
			var signatories = schedule.signatories();
			var signatoriesList = KeyList.newBuilder();
			signatories.forEach(a -> signatoriesList.addKeys(Key.newBuilder().setEd25519(ByteString.copyFrom(a))));

			var info = ScheduleInfo.newBuilder()
					.setScheduleID(id)
					.setTransactionBody(ByteString.copyFrom(schedule.transactionBody()))
					.setCreatorAccountID(schedule.schedulingAccount().toGrpcAccountId())
					.setPayerAccountID(schedule.payer().toGrpcAccountId())
					.setSignatories(signatoriesList)
					.setExpirationTime(Timestamp.newBuilder().setSeconds(schedule.expiry()));
			schedule.memo().ifPresent(info::setMemo);

			var adminCandidate = schedule.adminKey();
			adminCandidate.ifPresent(k -> info.setAdminKey(asKeyUnchecked(k)));

			return Optional.of(info.build());
		} catch (Exception unexpected) {
			log.warn(
					"Unexpected failure getting info for schedule {}!",
					readableId(scheduleID),
					unexpected);
			return Optional.empty();
		}
	}

	TokenFreezeStatus tfsFor(boolean flag) {
		return flag ? TokenFreezeStatus.Frozen : TokenFreezeStatus.Unfrozen;
	}

	TokenKycStatus tksFor(boolean flag) {
		return flag ? TokenKycStatus.Granted : TokenKycStatus.Revoked;
	}

	public boolean tokenExists(TokenID id) {
		return tokenStore.resolve(id) != MISSING_TOKEN;
	}

	public boolean scheduleExists(ScheduleID id) {
		return scheduleStore.resolve(id) != MISSING_SCHEDULE;
	}

	public Optional<FileGetInfoResponse.FileInfo> infoForFile(FileID id) {
		int attemptsLeft = 1 + properties.queryBlobLookupRetries();
		while (attemptsLeft-- > 0) {
			try {
				return getFileInfo(id);
			} catch (com.swirlds.blob.BinaryObjectNotFoundException e) {
				if (attemptsLeft > 0) {
					log.debug("Retrying fetch of {} file meta {} more times", readableId(id), attemptsLeft);
					try {
						TimeUnit.MILLISECONDS.sleep(100);
					} catch (InterruptedException ignore) { }
				}
			}
		}
		return Optional.empty();
	}

	private Optional<FileGetInfoResponse.FileInfo> getFileInfo(FileID id) {
		var attr = fileAttrs.get(id);
		if (attr == null) {
			return Optional.empty();
		}
		var info = FileGetInfoResponse.FileInfo.newBuilder()
				.setFileID(id)
				.setMemo(attr.getMemo())
				.setDeleted(attr.isDeleted())
				.setExpirationTime(Timestamp.newBuilder().setSeconds(attr.getExpiry()))
				.setSize(Optional.ofNullable(fileContents.get(id)).orElse(EMPTY_BYTES).length);
		if (!attr.getWacl().isEmpty()) {
			info.setKeys(MiscUtils.asKeyUnchecked(attr.getWacl()).getKeyList());
		}
		return Optional.of(info.build());
	}

	public Optional<ContractGetInfoResponse.ContractInfo> infoForContract(ContractID id) {
		var contract = contracts().get(fromContractId(id));
		if (contract == null) {
			return Optional.empty();
		}

		var mirrorId = asAccount(id);

		var storageSize = storageOf(id).orElse(EMPTY_BYTES).length;
		var bytecodeSize = bytecodeOf(id).orElse(EMPTY_BYTES).length;
		var totalBytesUsed = storageSize + bytecodeSize;
		var info = ContractGetInfoResponse.ContractInfo.newBuilder()
				.setAccountID(mirrorId)
				.setDeleted(contract.isDeleted())
				.setContractID(id)
				.setMemo(contract.getMemo())
				.setStorage(totalBytesUsed)
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(contract.getAutoRenewSecs()))
				.setBalance(contract.getBalance())
				.setExpirationTime(Timestamp.newBuilder().setSeconds(contract.getExpiry()))
				.setContractAccountID(asSolidityAddressHex(mirrorId));
		var tokenRels = tokenRelsFn.apply(this, mirrorId);
		if (!tokenRels.isEmpty()) {
			info.addAllTokenRelationships(tokenRels);
		}

		try {
			var adminKey = JKey.mapJKey(contract.getKey());
			info.setAdminKey(adminKey);
		} catch (Exception ignore) {
		}

		return Optional.of(info.build());
	}

	public FCMap<MerkleEntityId, MerkleTopic> topics() {
		return topics.get();
	}

	public FCMap<MerkleEntityId, MerkleAccount> accounts() {
		return accounts.get();
	}

	public FCMap<MerkleEntityId, MerkleAccount> contracts() {
		return accounts.get();
	}

	public Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenAssociations() {
		return tokenAssociations;
	}
}
