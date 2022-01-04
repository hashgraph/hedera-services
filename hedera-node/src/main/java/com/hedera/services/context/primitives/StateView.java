package com.hedera.services.context.primitives;

/*-
 * ‌
 * Hedera Services Node
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
import com.hedera.services.context.StateChildren;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.contracts.sources.AddressKeyedMapFactory;
import com.hedera.services.files.DataMapFactory;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.MetadataMapFactory;
import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.sigs.sourcing.KeyType;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RawTokenRelationship;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.UniqTokenView;
import com.hedera.services.store.tokens.views.UniqTokenViewFactory;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleInfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import com.hederahashgraph.api.proto.java.TokenType;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.store.schedule.ScheduleStore.MISSING_SCHEDULE;
import static com.hedera.services.store.tokens.TokenStore.MISSING_TOKEN;
import static com.hedera.services.store.tokens.views.EmptyUniqTokenViewFactory.EMPTY_UNIQ_TOKEN_VIEW_FACTORY;
import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddressHex;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hedera.services.utils.EntityNum.fromContractId;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static java.util.Collections.unmodifiableMap;

public class StateView {
	private static final Logger log = LogManager.getLogger(StateView.class);

	public static final AccountID WILDCARD_OWNER = AccountID.newBuilder()
			.setAccountNum(0L)
			.build();

	static BiFunction<StateView, EntityNum, List<TokenRelationship>> tokenRelsFn = StateView::tokenRels;

	static final byte[] EMPTY_BYTES = new byte[0];
	static final MerkleMap<?, ?> EMPTY_FCM = new MerkleMap<>();
	static final FCOneToManyRelation<?, ?> EMPTY_FCOTMR = new FCOneToManyRelation<>();

	public static final JKey EMPTY_WACL = new JKeyList();
	public static final MerkleToken REMOVED_TOKEN = new MerkleToken(
			0L, 0L, 0, "", "",
			false, false, MISSING_ENTITY_ID);
	public static final StateView EMPTY_VIEW = new StateView(
			null, null, null, null,
			EMPTY_UNIQ_TOKEN_VIEW_FACTORY);

	private final TokenStore tokenStore;
	private final ScheduleStore scheduleStore;
	private final StateChildren stateChildren;
	private final UniqTokenView uniqTokenView;
	private final NodeLocalProperties nodeLocalProperties;

	Map<byte[], byte[]> contractStorage;
	Map<byte[], byte[]> contractBytecode;
	Map<FileID, byte[]> fileContents;
	Map<FileID, HFileMeta> fileAttrs;

	public StateView(
			@Nullable TokenStore tokenStore,
			@Nullable ScheduleStore scheduleStore,
			@Nullable NodeLocalProperties nodeLocalProperties,
			@Nullable StateChildren stateChildren,
			UniqTokenViewFactory uniqTokenViewFactory
	) {
		this.tokenStore = tokenStore;
		this.scheduleStore = scheduleStore;
		this.nodeLocalProperties = nodeLocalProperties;
		this.stateChildren = stateChildren;

		this.uniqTokenView = uniqTokenViewFactory.viewFor(
				tokenStore,
				this::tokens,
				this::uniqueTokens,
				this::nftsByType,
				this::nftsByOwner,
				this::treasuryNftsByType);

		final Map<String, byte[]> blobStore = unmodifiableMap(
				new FcBlobsBytesStore(MerkleOptionalBlob::new, this::storage));

		fileContents = DataMapFactory.dataMapFrom(blobStore);
		fileAttrs = MetadataMapFactory.metaMapFrom(blobStore);
		contractStorage = AddressKeyedMapFactory.storageMapFrom(blobStore);
		contractBytecode = AddressKeyedMapFactory.bytecodeMapFrom(blobStore);
	}

	public Optional<HFileMeta> attrOf(FileID id) {
		return Optional.ofNullable(fileAttrs.get(id));
	}

	public Optional<byte[]> contentsOf(FileID id) {
		if (stateChildren == null) {
			return Optional.empty();
		}
		final var specialFiles = stateChildren.specialFiles();
		if (specialFiles.contains(id)) {
			return Optional.ofNullable(specialFiles.get(id));
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
		return tokenStore == null || !tokenStore.exists(id)
				? Optional.empty()
				: Optional.of(tokenStore.get(id));
	}

	public Optional<TokenInfo> infoForToken(TokenID tokenID) {
		if (tokenStore == null) {
			return Optional.empty();
		}
		try {
			var id = tokenStore.resolve(tokenID);
			if (id == MISSING_TOKEN) {
				return Optional.empty();
			}
			var token = tokenStore.get(id);
			var info = TokenInfo.newBuilder()
					.setTokenTypeValue(token.tokenType().ordinal())
					.setSupplyTypeValue(token.supplyType().ordinal())
					.setTokenId(id)
					.setDeleted(token.isDeleted())
					.setSymbol(token.symbol())
					.setName(token.name())
					.setMemo(token.memo())
					.setTreasury(token.treasury().toGrpcAccountId())
					.setTotalSupply(token.totalSupply())
					.setMaxSupply(token.maxSupply())
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
			var feeScheduleCandidate = token.feeScheduleKey();
			feeScheduleCandidate.ifPresent(k -> info.setFeeScheduleKey(asKeyUnchecked(k)));

			var pauseCandidate = token.pauseKey();
			pauseCandidate.ifPresentOrElse(k -> {
				info.setPauseKey(asKeyUnchecked(k));
				info.setPauseStatus(tokenPauseStatusOf(token.isPaused()));
			}, () -> info.setPauseStatus(TokenPauseStatus.PauseNotApplicable));

			if (token.hasAutoRenewAccount()) {
				info.setAutoRenewAccount(token.autoRenewAccount().toGrpcAccountId());
				info.setAutoRenewPeriod(Duration.newBuilder().setSeconds(token.autoRenewPeriod()));
			}

			info.addAllCustomFees(token.grpcFeeSchedule());

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
		if (scheduleStore == null) {
			return Optional.empty();
		}
		try {
			var id = scheduleStore.resolve(scheduleID);
			if (id == MISSING_SCHEDULE) {
				return Optional.empty();
			}
			var schedule = scheduleStore.get(id);
			var signatories = schedule.signatories();
			var signatoriesList = KeyList.newBuilder();

			signatories.forEach(pubKey -> signatoriesList.addKeys(grpcKeyReprOf(pubKey)));

			var info = ScheduleInfo.newBuilder()
					.setScheduleID(id)
					.setScheduledTransactionBody(schedule.scheduledTxn())
					.setScheduledTransactionID(schedule.scheduledTransactionId())
					.setCreatorAccountID(schedule.schedulingAccount().toGrpcAccountId())
					.setPayerAccountID(schedule.effectivePayer().toGrpcAccountId())
					.setSigners(signatoriesList)
					.setExpirationTime(Timestamp.newBuilder().setSeconds(schedule.expiry()));
			schedule.memo().ifPresent(info::setMemo);
			if (schedule.isDeleted()) {
				info.setDeletionTime(schedule.deletionTime());
			} else if (schedule.isExecuted()) {
				info.setExecutionTime(schedule.executionTime());
			}

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

	private Key grpcKeyReprOf(final byte[] publicKey) {
		if (publicKey.length == KeyType.ECDSA_SECP256K1.getLength()) {
			return Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(publicKey)).build();
		} else {
			return Key.newBuilder().setEd25519(ByteString.copyFrom(publicKey)).build();
		}
	}

	public Optional<TokenNftInfo> infoForNft(NftID target) {
		final var currentNfts = uniqueTokens();
		final var tokenId = EntityNum.fromTokenId(target.getTokenID());
		final var targetKey = EntityNumPair.fromLongs(tokenId.longValue(), target.getSerialNumber());
		if (!currentNfts.containsKey(targetKey)) {
			return Optional.empty();
		}
		final var targetNft = currentNfts.get(targetKey);
		var accountId = targetNft.getOwner().toGrpcAccountId();

		if (WILDCARD_OWNER.equals(accountId)) {
			var merkleToken = tokens().get(tokenId);
			if (merkleToken == null) {
				return Optional.empty();
			}
			accountId = merkleToken.treasury().toGrpcAccountId();
		}

		final var info = TokenNftInfo.newBuilder()
				.setNftID(target)
				.setAccountID(accountId)
				.setCreationTime(targetNft.getCreationTime().toGrpc())
				.setMetadata(ByteString.copyFrom(targetNft.getMetadata()))
				.build();
		return Optional.of(info);
	}

	public boolean nftExists(NftID id) {
		final var tokenNum = EntityNum.fromTokenId(id.getTokenID());
		final var key = EntityNumPair.fromLongs(tokenNum.longValue(), id.getSerialNumber());
		return uniqueTokens().containsKey(key);
	}

	public Optional<TokenType> tokenType(TokenID tokenID) {
		if (tokenStore == null) {
			return Optional.empty();
		}
		try {
			var id = tokenStore.resolve(tokenID);
			if (id == MISSING_TOKEN) {
				return Optional.empty();
			}
			var token = tokenStore.get(id);
			return Optional.ofNullable(TokenType.forNumber(token.tokenType().ordinal()));
		} catch (Exception unexpected) {
			log.warn(
					"Unexpected failure getting info for token {}!",
					readableId(tokenID),
					unexpected);
			return Optional.empty();
		}
	}

	public boolean tokenExists(TokenID id) {
		return tokenStore != null && tokenStore.resolve(id) != MISSING_TOKEN;
	}

	public boolean scheduleExists(ScheduleID id) {
		return scheduleStore != null && scheduleStore.resolve(id) != MISSING_SCHEDULE;
	}

	public Optional<FileGetInfoResponse.FileInfo> infoForFile(FileID id) {
		int attemptsLeft = 1 + (nodeLocalProperties == null ? 0 : nodeLocalProperties.queryBlobLookupRetries());
		while (attemptsLeft-- > 0) {
			try {
				return getFileInfo(id);
			} catch (com.swirlds.blob.BinaryObjectNotFoundException | com.swirlds.blob.BinaryObjectDeletedException e) {
				if (attemptsLeft > 0) {
					log.debug("Retrying fetch of {} file meta {} more times", readableId(id), attemptsLeft);
					try {
						TimeUnit.MILLISECONDS.sleep(100);
					} catch (InterruptedException ex) {
						log.debug(
								"Interrupted fetching meta for file {}, {} attempts left",
								readableId(id),
								attemptsLeft);
						Thread.currentThread().interrupt();
					}
				}
			} catch (com.swirlds.blob.BinaryObjectException e) {
				log.warn("Unexpected error occurred when getting info for file {}", readableId(id), e);
				break;
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

	public Optional<CryptoGetInfoResponse.AccountInfo> infoForAccount(AccountID id, AliasManager aliasManager) {
		final var accountEntityNum = id.getAlias().isEmpty() ? fromAccountId(id) : aliasManager.lookupIdBy(
				id.getAlias());
		final var account = accounts().get(accountEntityNum);
		if (account == null) {
			return Optional.empty();
		}

		final AccountID accountID = id.getAlias().isEmpty() ? id : accountEntityNum.toGrpcAccountId();

		var info = CryptoGetInfoResponse.AccountInfo.newBuilder()
				.setKey(asKeyUnchecked(account.getAccountKey()))
				.setAccountID(accountID)
				.setAlias(account.getAlias())
				.setReceiverSigRequired(account.isReceiverSigRequired())
				.setDeleted(account.isDeleted())
				.setMemo(account.getMemo())
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(account.getAutoRenewSecs()))
				.setBalance(account.getBalance())
				.setExpirationTime(Timestamp.newBuilder().setSeconds(account.getExpiry()))
				.setContractAccountID(asSolidityAddressHex(accountID))
				.setOwnedNfts(account.getNftsOwned())
				.setMaxAutomaticTokenAssociations(account.getMaxAutomaticAssociations());
		Optional.ofNullable(account.getProxy())
				.map(EntityId::toGrpcAccountId)
				.ifPresent(info::setProxyAccountID);
		final var tokenRels = tokenRelsFn.apply(this, accountEntityNum);
		if (!tokenRels.isEmpty()) {
			info.addAllTokenRelationships(tokenRels);
		}
		return Optional.of(info.build());
	}

	public long numNftsOwnedBy(AccountID target) {
		var account = accounts().get(fromAccountId(target));
		if (account == null) {
			return 0L;
		}
		return account.getNftsOwned();
	}

	public Optional<List<TokenNftInfo>> infoForAccountNfts(@Nonnull AccountID aid, long start, long end) {
		var account = accounts().get(fromAccountId(aid));
		if (account == null) {
			return Optional.empty();
		}
		final var answer = uniqTokenView.ownedAssociations(aid, start, end);
		return Optional.of(answer);
	}

	public Optional<List<TokenNftInfo>> infosForTokenNfts(@Nonnull TokenID tid, long start, long end) {
		if (!tokenExists(tid)) {
			return Optional.empty();
		}
		final var answer = uniqTokenView.typedAssociations(tid, start, end);
		return Optional.of(answer);
	}

	public Optional<ContractGetInfoResponse.ContractInfo> infoForContract(ContractID id) {
		final var contractId = fromContractId(id);
		var contract = contracts().get(contractId);
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
		var tokenRels = tokenRelsFn.apply(this, contractId);
		if (!tokenRels.isEmpty()) {
			info.addAllTokenRelationships(tokenRels);
		}

		try {
			var adminKey = JKey.mapJKey(contract.getAccountKey());
			info.setAdminKey(adminKey);
		} catch (Exception ignore) {
		}

		return Optional.of(info.build());
	}

	public MerkleMap<EntityNum, MerkleTopic> topics() {
		return stateChildren == null ? emptyMm() : stateChildren.topics();
	}

	public MerkleMap<EntityNum, MerkleAccount> accounts() {
		return stateChildren == null ? emptyMm() : stateChildren.accounts();
	}

	public MerkleMap<EntityNum, MerkleAccount> contracts() {
		return stateChildren == null ? emptyMm() : stateChildren.accounts();
	}

	public MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations() {
		return stateChildren == null ? emptyMm() : stateChildren.tokenAssociations();
	}

	public MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens() {
		return stateChildren == null ? emptyMm() : stateChildren.uniqueTokens();
	}

	MerkleMap<String, MerkleOptionalBlob> storage() {
		return stateChildren == null ? emptyMm() : stateChildren.storage();
	}

	MerkleMap<EntityNum, MerkleToken> tokens() {
		return stateChildren == null ? emptyMm() : stateChildren.tokens();
	}

	FCOneToManyRelation<EntityNum, Long> nftsByType() {
		return stateChildren == null ? emptyFcotmr() : stateChildren.uniqueTokenAssociations();
	}

	FCOneToManyRelation<EntityNum, Long> nftsByOwner() {
		return stateChildren == null ? emptyFcotmr() : stateChildren.uniqueOwnershipAssociations();
	}

	FCOneToManyRelation<EntityNum, Long> treasuryNftsByType() {
		return stateChildren == null ? emptyFcotmr() : stateChildren.uniqueOwnershipTreasuryAssociations();
	}

	UniqTokenView uniqTokenView() {
		return uniqTokenView;
	}

	private TokenFreezeStatus tfsFor(boolean flag) {
		return flag ? TokenFreezeStatus.Frozen : TokenFreezeStatus.Unfrozen;
	}

	private TokenKycStatus tksFor(boolean flag) {
		return flag ? TokenKycStatus.Granted : TokenKycStatus.Revoked;
	}

	private TokenPauseStatus tokenPauseStatusOf(boolean flag) {
		return flag ? TokenPauseStatus.Paused : TokenPauseStatus.Unpaused;
	}

	static List<TokenRelationship> tokenRels(final StateView view, final EntityNum id) {
		var account = view.accounts().get(id);
		List<TokenRelationship> relationships = new ArrayList<>();
		var tokenIds = account.tokens().asTokenIds();
		for (TokenID tId : tokenIds) {
			var optionalToken = view.tokenWith(tId);
			var effectiveToken = optionalToken.orElse(REMOVED_TOKEN);
			var relKey = fromAccountTokenRel(id.toGrpcAccountId(), tId);
			var relationship = view.tokenAssociations().get(relKey);
			relationships.add(new RawTokenRelationship(
					relationship.getBalance(),
					tId.getShardNum(),
					tId.getRealmNum(),
					tId.getTokenNum(),
					relationship.isFrozen(),
					relationship.isKycGranted(),
					relationship.isAutomaticAssociation()
			).asGrpcFor(effectiveToken));
		}
		return relationships;
	}

	private static <K, V extends MerkleNode & Keyed<K>> MerkleMap<K, V> emptyMm() {
		return (MerkleMap<K, V>) EMPTY_FCM;
	}

	private static <K, V> FCOneToManyRelation<K, V> emptyFcotmr() {
		return (FCOneToManyRelation<K, V>) EMPTY_FCOTMR;
	}
}
