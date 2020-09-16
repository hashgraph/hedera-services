package com.hedera.test.factories.scenarios;

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

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.files.HederaFs;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.tokens.TokenStore;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.keys.OverlappingKeyGenerator;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.swirlds.fcmap.FCMap;

import static com.hedera.test.factories.keys.KeyTree.withRoot;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static com.hedera.test.factories.accounts.MockFCMapFactory.newAccounts;
import static com.hedera.test.utils.IdUtils.*;
import static com.hedera.test.factories.txns.SignedTxnFactory.*;
import static com.hedera.test.factories.accounts.MapValueFactory.newAccount;
import static com.hedera.test.factories.accounts.MapValueFactory.newContract;
import static com.hedera.test.factories.keys.NodeFactory.*;

public interface TxnHandlingScenario {
	PlatformTxnAccessor platformTxn() throws Throwable;

	KeyFactory overlapFactory = new KeyFactory(OverlappingKeyGenerator.withDefaultOverlaps());

	default FCMap<MerkleEntityId, MerkleAccount> accounts() throws Exception {
		return newAccounts()
				.withAccount(FIRST_TOKEN_SENDER_ID,
						newAccount()
								.balance(10_000L)
								.accountKeys(FIRST_TOKEN_SENDER_KT).get())
				.withAccount(SECOND_TOKEN_SENDER_ID,
						newAccount()
								.balance(10_000L)
								.accountKeys(SECOND_TOKEN_SENDER_KT).get())
				.withAccount(TOKEN_RECEIVER_ID,
						newAccount()
								.balance(0L).get())
				.withAccount(DEFAULT_NODE_ID,
						newAccount()
								.balance(0L)
								.accountKeys(DEFAULT_PAYER_KT).get())
				.withAccount(
						DEFAULT_PAYER_ID,
						newAccount()
								.balance(DEFAULT_PAYER_BALANCE)
								.accountKeys(DEFAULT_PAYER_KT).get())
				.withAccount(
						MASTER_PAYER_ID,
						newAccount()
								.balance(DEFAULT_PAYER_BALANCE)
								.accountKeys(DEFAULT_PAYER_KT).get())
				.withAccount(
						TREASURY_PAYER_ID,
						newAccount()
								.balance(DEFAULT_PAYER_BALANCE)
								.accountKeys(DEFAULT_PAYER_KT).get())
				.withAccount(
						NO_RECEIVER_SIG_ID,
						newAccount()
								.receiverSigRequired(false)
								.balance(DEFAULT_BALANCE)
								.accountKeys(NO_RECEIVER_SIG_KT).get()
				).withAccount(
						RECEIVER_SIG_ID,
						newAccount()
								.receiverSigRequired(true)
								.balance(DEFAULT_BALANCE)
								.accountKeys(RECEIVER_SIG_KT).get()
				).withAccount(
						MISC_ACCOUNT_ID,
						newAccount()
								.balance(DEFAULT_BALANCE)
								.accountKeys(MISC_ACCOUNT_KT).get()
				).withAccount(
						COMPLEX_KEY_ACCOUNT_ID,
						newAccount()
								.balance(DEFAULT_BALANCE)
								.accountKeys(COMPLEX_KEY_ACCOUNT_KT).get()
				).withAccount(
						CARELESS_SIGNING_PAYER_ID,
						newAccount()
								.balance(DEFAULT_BALANCE)
								.accountKeys(CARELESS_SIGNING_PAYER_KT).get()
				).withAccount(
						DILIGENT_SIGNING_PAYER_ID,
						newAccount()
								.balance(DEFAULT_BALANCE)
								.accountKeys(DILIGENT_SIGNING_PAYER_KT).get()
				).withAccount(
						FROM_OVERLAP_PAYER_ID,
						newAccount()
								.balance(DEFAULT_BALANCE)
								.keyFactory(overlapFactory)
								.accountKeys(FROM_OVERLAP_PAYER_KT).get()
				).withContract(
						MISC_CONTRACT_ID,
						newContract()
								.balance(DEFAULT_BALANCE)
								.accountKeys(MISC_ADMIN_KT).get()
				).get();
	}

	default HederaFs hfs() throws Exception {
		HederaFs hfs = mock(HederaFs.class);
		given(hfs.exists(MISC_FILE)).willReturn(true);
		given(hfs.exists(SYS_FILE)).willReturn(true);
		given(hfs.getattr(MISC_FILE)).willReturn(JFileInfo.convert(MISC_FILE_INFO));
		given(hfs.getattr(SYS_FILE)).willReturn(JFileInfo.convert(SYS_FILE_INFO));
		given(hfs.exists(IMMUTABLE_FILE)).willReturn(true);
		given(hfs.getattr(IMMUTABLE_FILE)).willReturn(JFileInfo.convert(IMMUTABLE_FILE_INFO));
		return hfs;
	}

	default FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage() {
		@SuppressWarnings("unchecked")
		FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage = (FCMap<MerkleBlobMeta, MerkleOptionalBlob>)mock(FCMap.class);

		return storage;
	}

	default FCMap<MerkleEntityId, MerkleTopic> topics() {
		var topics = (FCMap<MerkleEntityId, MerkleTopic>) mock(FCMap.class);
		given(topics.get(EXISTING_TOPIC)).willReturn(new MerkleTopic());
		return topics;
	}

	default TokenStore tokenStore() {
		var tokenStore = mock(TokenStore.class);

		var adminKey = TOKEN_ADMIN_KT.asJKeyUnchecked();
		var optionalKycKey = TOKEN_KYC_KT.asJKeyUnchecked();
		var optionalWipeKey = TOKEN_WIPE_KT.asJKeyUnchecked();
		var optionalSupplyKey = TOKEN_SUPPLY_KT.asJKeyUnchecked();
		var optionalFreezeKey = TOKEN_FREEZE_KT.asJKeyUnchecked();

		var immutableToken = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"ImmutableToken", false, false,
				new EntityId(1, 2, 3));
		given(tokenStore.resolve(IdUtils.asIdRef(KNOWN_TOKEN_IMMUTABLE_ID)))
				.willReturn(KNOWN_TOKEN_IMMUTABLE);
		given(tokenStore.get(KNOWN_TOKEN_IMMUTABLE)).willReturn(immutableToken);

		var vanillaToken = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"VanillaToken", false, false,
				new EntityId(1, 2, 3));
		vanillaToken.setAdminKey(adminKey);
		given(tokenStore.resolve(IdUtils.asIdRef(KNOWN_TOKEN_NO_SPECIAL_KEYS)))
				.willReturn(KNOWN_TOKEN_NO_SPECIAL_KEYS);
		given(tokenStore.get(KNOWN_TOKEN_NO_SPECIAL_KEYS)).willReturn(vanillaToken);

		var frozenToken = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"FrozenToken", true, false,
				new EntityId(1, 2, 4));
		frozenToken.setAdminKey(adminKey);
		frozenToken.setFreezeKey(optionalFreezeKey);
		given(tokenStore.resolve(IdUtils.asIdRef(KNOWN_TOKEN_WITH_FREEZE)))
				.willReturn(KNOWN_TOKEN_WITH_FREEZE);
		given(tokenStore.get(KNOWN_TOKEN_WITH_FREEZE)).willReturn(frozenToken);

		var kycToken = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"KycToken", false, true,
				new EntityId(1, 2, 4));
		kycToken.setAdminKey(adminKey);
		kycToken.setKycKey(optionalKycKey);
		given(tokenStore.resolve(IdUtils.asIdRef(KNOWN_TOKEN_WITH_KYC)))
				.willReturn(KNOWN_TOKEN_WITH_KYC);
		given(tokenStore.get(KNOWN_TOKEN_WITH_KYC)).willReturn(kycToken);

		var supplyToken = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"SupplyToken", false, false,
				new EntityId(1, 2, 4));
		supplyToken.setAdminKey(adminKey);
		supplyToken.setSupplyKey(optionalSupplyKey);
		given(tokenStore.resolve(IdUtils.asIdRef(KNOWN_TOKEN_WITH_SUPPLY)))
				.willReturn(KNOWN_TOKEN_WITH_SUPPLY);
		given(tokenStore.get(KNOWN_TOKEN_WITH_SUPPLY)).willReturn(supplyToken);

		var wipeToken = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"WipeToken", false, false,
				new EntityId(1, 2, 4));
		wipeToken.setAdminKey(adminKey);
		wipeToken.setWipeKey(optionalWipeKey);
		given(tokenStore.resolve(IdUtils.asIdRef(KNOWN_TOKEN_WITH_WIPE)))
				.willReturn(KNOWN_TOKEN_WITH_WIPE);
		given(tokenStore.get(KNOWN_TOKEN_WITH_WIPE)).willReturn(wipeToken);

		given(tokenStore.resolve(IdUtils.asIdRef(UNKNOWN_TOKEN)))
				.willReturn(TokenStore.MISSING_TOKEN);

		return tokenStore;
	}

	String MISSING_ACCOUNT_ID = "1.2.3";
	AccountID MISSING_ACCOUNT = asAccount(MISSING_ACCOUNT_ID);

	String NO_RECEIVER_SIG_ID = "0.0.1337";
	AccountID NO_RECEIVER_SIG = asAccount(NO_RECEIVER_SIG_ID);
	KeyTree NO_RECEIVER_SIG_KT = withRoot(ed25519());

	String RECEIVER_SIG_ID = "0.0.1338";
	AccountID RECEIVER_SIG = asAccount(RECEIVER_SIG_ID);
	KeyTree RECEIVER_SIG_KT = withRoot(list(ed25519(), threshold(1, ed25519(), ed25519())));

	String MISC_ACCOUNT_ID = "0.0.1339";
	AccountID MISC_ACCOUNT = asAccount(MISC_ACCOUNT_ID);
	KeyTree MISC_ACCOUNT_KT = withRoot(ed25519());

	String DILIGENT_SIGNING_PAYER_ID = "0.0.1340";
	AccountID DILIGENT_SIGNING_PAYER = asAccount(DILIGENT_SIGNING_PAYER_ID);
	KeyTree DILIGENT_SIGNING_PAYER_KT = withRoot(threshold(2, ed25519(true), ed25519(true), ed25519(false)));

	String CARELESS_SIGNING_PAYER_ID = "0.0.1341";
	AccountID CARELESS_SIGNING_PAYER = asAccount(CARELESS_SIGNING_PAYER_ID);
	KeyTree CARELESS_SIGNING_PAYER_KT = withRoot(threshold(2, ed25519(false), ed25519(true), ed25519(false)));

	String COMPLEX_KEY_ACCOUNT_ID = "0.0.1342";
	AccountID COMPLEX_KEY_ACCOUNT = asAccount(COMPLEX_KEY_ACCOUNT_ID);
	KeyTree COMPLEX_KEY_ACCOUNT_KT = withRoot(
			list(
					ed25519(),
					threshold(1,
							list(list(ed25519(), ed25519()), ed25519()), ed25519()),
					ed25519(),
					list(
							threshold(2,
									ed25519(), ed25519(),  ed25519()))));

	String FROM_OVERLAP_PAYER_ID = "0.0.1343";
	AccountID FROM_OVERLAP_PAYER = asAccount(FROM_OVERLAP_PAYER_ID);
	KeyTree FROM_OVERLAP_PAYER_KT = withRoot(threshold(2, ed25519(true), ed25519(true), ed25519(false)));

	KeyTree NEW_ACCOUNT_KT = withRoot(list(ed25519(), threshold(1, ed25519(), ed25519())));
	KeyTree LONG_THRESHOLD_KT = withRoot(threshold(1, ed25519(), ed25519(), ed25519(), ed25519()));

	String MISSING_FILE_ID = "1.2.3";
	FileID MISSING_FILE = asFile(MISSING_FILE_ID);

	String SYS_FILE_ID = "0.0.111";
	FileID SYS_FILE = asFile(SYS_FILE_ID);
	KeyTree SYS_FILE_WACL_KT = withRoot(list(ed25519()));
	FileGetInfoResponse.FileInfo SYS_FILE_INFO = FileGetInfoResponse.FileInfo.newBuilder()
			.setKeys(SYS_FILE_WACL_KT.asKey().getKeyList())
			.setFileID(SYS_FILE)
			.build();

	String MISC_FILE_ID = "0.0.2337";
	FileID MISC_FILE = asFile(MISC_FILE_ID);
	KeyTree MISC_FILE_WACL_KT = withRoot(list(ed25519()));
	FileGetInfoResponse.FileInfo MISC_FILE_INFO = FileGetInfoResponse.FileInfo.newBuilder()
			.setKeys(MISC_FILE_WACL_KT.asKey().getKeyList())
			.setFileID(MISC_FILE)
			.build();

	String IMMUTABLE_FILE_ID = "0.0.2338";
	FileID IMMUTABLE_FILE = asFile(IMMUTABLE_FILE_ID);
	FileGetInfoResponse.FileInfo IMMUTABLE_FILE_INFO = FileGetInfoResponse.FileInfo.newBuilder()
			.setFileID(IMMUTABLE_FILE)
			.build();

	KeyTree SIMPLE_NEW_WACL_KT = withRoot(list(ed25519()));

	String MISSING_CONTRACT_ID = "1.2.3";
	ContractID MISSING_CONTRACT = asContract(MISSING_CONTRACT_ID);

	String MISC_CONTRACT_ID = "0.0.3337";
	ContractID MISC_CONTRACT = asContract(MISC_CONTRACT_ID);
	KeyTree MISC_ADMIN_KT = withRoot(ed25519());

	KeyTree SIMPLE_NEW_ADMIN_KT = withRoot(ed25519());

	Long DEFAULT_BALANCE = 1_000L;
	Long DEFAULT_PAYER_BALANCE = 1_000_000_000_000L;

	String DEFAULT_MEMO = "This is something else.";
	Duration DEFAULT_PERIOD = Duration.newBuilder().setSeconds(1_000L).build();
	Timestamp DEFAULT_EXPIRY = Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1_000L + 86_400L).build();

	String EXISTING_TOPIC_ID = "0.0.7890";
	TopicID EXISTING_TOPIC = asTopic(EXISTING_TOPIC_ID);

	String MISSING_TOPIC_ID = "0.0.12121";
	TopicID MISSING_TOPIC = asTopic(MISSING_TOPIC_ID);

	String KNOWN_TOKEN_IMMUTABLE_ID = "0.0.534";
	TokenID KNOWN_TOKEN_IMMUTABLE = asToken(KNOWN_TOKEN_IMMUTABLE_ID);
	String KNOWN_TOKEN_NO_SPECIAL_KEYS_ID = "0.0.535";
	TokenID KNOWN_TOKEN_NO_SPECIAL_KEYS = asToken(KNOWN_TOKEN_NO_SPECIAL_KEYS_ID);
	String KNOWN_TOKEN_WITH_FREEZE_ID = "0.0.777";
	TokenID KNOWN_TOKEN_WITH_FREEZE = asToken(KNOWN_TOKEN_WITH_FREEZE_ID);
	String KNOWN_TOKEN_WITH_KYC_ID = "0.0.776";
	TokenID KNOWN_TOKEN_WITH_KYC = asToken(KNOWN_TOKEN_WITH_KYC_ID);
	String KNOWN_TOKEN_WITH_SUPPLY_ID = "0.0.775";
	TokenID KNOWN_TOKEN_WITH_SUPPLY = asToken(KNOWN_TOKEN_WITH_SUPPLY_ID);
	String KNOWN_TOKEN_WITH_WIPE_ID = "0.0.774";
	TokenID KNOWN_TOKEN_WITH_WIPE = asToken(KNOWN_TOKEN_WITH_WIPE_ID);

	String FIRST_TOKEN_SENDER_ID = "0.0.888";
	AccountID FIRST_TOKEN_SENDER = asAccount(FIRST_TOKEN_SENDER_ID);
	String SECOND_TOKEN_SENDER_ID = "0.0.999";
	AccountID SECOND_TOKEN_SENDER = asAccount(SECOND_TOKEN_SENDER_ID);
	String TOKEN_RECEIVER_ID = "0.0.1111";
	AccountID TOKEN_RECEIVER = asAccount(TOKEN_RECEIVER_ID);

	String UNKNOWN_TOKEN_ID = "0.0.666";
	TokenID UNKNOWN_TOKEN = asToken(UNKNOWN_TOKEN_ID);

	KeyTree FIRST_TOKEN_SENDER_KT = withRoot(ed25519());
	KeyTree SECOND_TOKEN_SENDER_KT = withRoot(ed25519());
	KeyTree TOKEN_ADMIN_KT = withRoot(ed25519());
	KeyTree TOKEN_FREEZE_KT = withRoot(ed25519());
	KeyTree TOKEN_SUPPLY_KT = withRoot(ed25519());
	KeyTree TOKEN_WIPE_KT = withRoot(ed25519());
	KeyTree TOKEN_KYC_KT = withRoot(ed25519());
	KeyTree TOKEN_REPLACE_KT = withRoot(ed25519());
	KeyTree MISC_TOPIC_SUBMIT_KT = withRoot(ed25519());
	KeyTree MISC_TOPIC_ADMIN_KT = withRoot(ed25519());
	KeyTree UPDATE_TOPIC_ADMIN_KT = withRoot(ed25519());
}
