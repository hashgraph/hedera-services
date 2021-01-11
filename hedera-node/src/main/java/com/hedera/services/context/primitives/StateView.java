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

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.contracts.sources.AddressKeyedMapFactory;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.files.DataMapFactory;
import com.hedera.services.files.MetadataMapFactory;
import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hedera.services.state.submerkle.RawTokenRelationship;
import com.hedera.services.tokens.TokenStore;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hederahashgraph.api.proto.java.ScheduleInfo;

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
import static com.hedera.services.tokens.ExceptionalTokenStore.NOOP_TOKEN_STORE;
import static com.hedera.services.tokens.TokenStore.MISSING_TOKEN;
import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddressHex;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.legacy.core.jproto.JKey.mapJKey;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static java.util.Collections.unmodifiableMap;

public class StateView {
	private static final Logger log = LogManager.getLogger(StateView.class);

	static BiFunction<StateView, AccountID, List<TokenRelationship>> tokenRelsFn = StateView::tokenRels;

	private static final byte[] EMPTY_BYTES = new byte[0];
	public static final JKey EMPTY_WACL = new JKeyList();

	public static final FCMap<MerkleEntityId, MerkleTopic> EMPTY_TOPICS =
			new FCMap<>(new MerkleEntityId.Provider(), new MerkleTopic.Provider());
	public static final Supplier<FCMap<MerkleEntityId, MerkleTopic>> EMPTY_TOPICS_SUPPLIER =
			() -> EMPTY_TOPICS;

	public static final FCMap<MerkleEntityId, MerkleAccount> EMPTY_ACCOUNTS =
			new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER);
	public static final Supplier<FCMap<MerkleEntityId, MerkleAccount>> EMPTY_ACCOUNTS_SUPPLIER =
			() -> EMPTY_ACCOUNTS;

	public static final FCMap<MerkleBlobMeta, MerkleOptionalBlob> EMPTY_STORAGE =
			new FCMap<>(new MerkleBlobMeta.Provider(), new MerkleOptionalBlob.Provider());
	public static final Supplier<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> EMPTY_STORAGE_SUPPLIER =
			() -> EMPTY_STORAGE;

	public static final FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> EMPTY_TOKEN_ASSOCIATIONS =
			new FCMap<>(MerkleEntityAssociation.LEGACY_PROVIDER, MerkleTokenRelStatus.LEGACY_PROVIDER);
	public static final Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> EMPTY_TOKEN_ASSOCS_SUPPLIER =
			() -> EMPTY_TOKEN_ASSOCIATIONS;

	public static final StateView EMPTY_VIEW = new StateView(
			EMPTY_TOPICS_SUPPLIER,
			EMPTY_ACCOUNTS_SUPPLIER,
			null, null);

	Map<byte[], byte[]> contractStorage;
	Map<byte[], byte[]> contractBytecode;
	Map<FileID, byte[]> fileContents;
	Map<FileID, JFileInfo> fileAttrs;
	private final TokenStore tokenStore;
	private final Supplier<MerkleDiskFs> diskFs;
	private final Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;
	private final Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenAssociations;

	private final PropertySource properties;

	public StateView(
			Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			PropertySource properties,
			Supplier<MerkleDiskFs> diskFs
	) {
		this(NOOP_TOKEN_STORE, topics, accounts, EMPTY_STORAGE_SUPPLIER, EMPTY_TOKEN_ASSOCS_SUPPLIER, diskFs, properties);
	}

	public StateView(
			TokenStore tokenStore,
			Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			PropertySource properties,
			Supplier<MerkleDiskFs> diskFs
	) {
		this(tokenStore, topics, accounts, EMPTY_STORAGE_SUPPLIER, EMPTY_TOKEN_ASSOCS_SUPPLIER, diskFs, properties);
	}

	public StateView(
			TokenStore tokenStore,
			Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			Supplier<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> storage,
			Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenAssociations,
			Supplier<MerkleDiskFs> diskFs,
			PropertySource properties
	) {
		this.topics = topics;
		this.accounts = accounts;
		this.tokenStore = tokenStore;
		this.tokenAssociations = tokenAssociations;

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
			if (optionalToken.isPresent()) {
				var token = optionalToken.get();
				if (!token.isDeleted()) {
					var relKey = fromAccountTokenRel(id, tId);
					var relationship = view.tokenAssociations().get().get(relKey);
					relationships.add(new RawTokenRelationship(
							relationship.getBalance(),
							tId.getShardNum(),
							tId.getRealmNum(),
							tId.getTokenNum(),
							relationship.isFrozen(),
							relationship.isKycGranted()
					).asGrpcFor(token));
				}
			}
		}
		return relationships;
	}

	public Optional<JFileInfo> attrOf(FileID id) {
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
		// TODO: Implement logic for getting information for schedule from store.
		throw new UnsupportedOperationException();
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
		// TODO: return scheduleStore.resolve(id) != MISSING_SCHEDULE;
		throw new UnsupportedOperationException();
	}

	public Optional<FileGetInfoResponse.FileInfo> infoForFile(FileID id) {
		int retries = properties.getIntProperty("binary.object.query.retry.times");
		while (retries >= 0) {
			try {
				return getFileInfo(id);
			} catch (com.swirlds.blob.BinaryObjectNotFoundException e) {
				log.info("May run into a temp issue getting info for {}, will retry {} times", readableId(id), retries);
				try {
					TimeUnit.MILLISECONDS.sleep(100);
				} catch (InterruptedException ie) {
					// Sleep interrupted, no need to do anything and just try fetch again.
				}
			} catch (Exception unknown) {
				log.warn("Unexpected problem getting info for {}", readableId(id), unknown);
				return Optional.empty();
			}
			retries--;
			if (retries < 0) {
				log.warn("Can't get info for {} at this moment. Try again later", readableId(id));
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

	private Optional<FileGetInfoResponse.FileInfo> getFileInfo(FileID id) throws Exception {
		var attr = fileAttrs.get(id);
		if (attr == null) {
			return Optional.empty();
		}
		var info = FileGetInfoResponse.FileInfo.newBuilder()
				.setFileID(id)
				.setDeleted(attr.isDeleted())
				.setExpirationTime(Timestamp.newBuilder().setSeconds(attr.getExpirationTimeSeconds()))
				.setSize(Optional.ofNullable(fileContents.get(id)).orElse(EMPTY_BYTES).length);
		if (!attr.getWacl().isEmpty()) {
			info.setKeys(mapJKey(attr.getWacl()).getKeyList());
		}
		return Optional.of(info.build());
	}

	public Optional<ContractGetInfoResponse.ContractInfo> infoForContract(ContractID id) {
		var contract = contracts().get(fromContractId(id));
		if (contract == null) {
			return Optional.empty();
		}

		var mirrorId = asAccount(id);

		var	storageSize = storageOf(id).orElse(EMPTY_BYTES).length;
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
		} catch (Exception ignore) { }

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
