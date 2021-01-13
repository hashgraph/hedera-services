package com.hedera.services.sigs.order;

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

import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.legacy.crypto.SignatureStatusCode;
import com.hedera.services.sigs.metadata.ContractSigningMetadata;
import com.hedera.services.sigs.metadata.FileSigningMetadata;
import com.hedera.services.sigs.metadata.lookups.FileSigMetaLookup;
import com.hedera.services.sigs.metadata.lookups.SafeLookupResult;
import com.hedera.services.sigs.metadata.lookups.AccountSigMetaLookup;
import com.hedera.services.sigs.metadata.lookups.TopicSigMetaLookup;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.files.HederaFs;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.sigs.metadata.TopicSigningMetadata;
import com.hedera.services.sigs.metadata.lookups.ContractSigMetaLookup;
import com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import static com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup.defaultLookupsFor;
import static com.hedera.test.factories.scenarios.ConsensusDeleteTopicScenarios.CONSENSUS_DELETE_TOPIC_MISSING_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusDeleteTopicScenarios.CONSENSUS_DELETE_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.*;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.*;
import static com.hedera.test.factories.txns.ConsensusCreateTopicFactory.SIMPLE_TOPIC_ADMIN_KEY;
import static java.util.stream.Collectors.toList;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import static com.hedera.test.factories.txns.FileCreateFactory.*;
import static com.hedera.test.factories.txns.CryptoCreateFactory.*;
import static com.hedera.test.factories.txns.ContractCreateFactory.*;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.MatcherAssert.assertThat;
import static com.hedera.test.utils.IdUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static com.hedera.test.factories.scenarios.BadPayerScenarios.*;
import static com.hedera.test.factories.scenarios.CryptoCreateScenarios.*;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.*;
import static com.hedera.test.factories.scenarios.CryptoDeleteScenarios.*;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.*;
import static com.hedera.test.factories.scenarios.FileCreateScenarios.*;
import static com.hedera.test.factories.scenarios.FileAppendScenarios.*;
import static com.hedera.test.factories.scenarios.FileUpdateScenarios.*;
import static com.hedera.test.factories.scenarios.FileDeleteScenarios.*;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.*;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.*;
import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.*;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.*;
import static com.hedera.test.factories.scenarios.ScheduleSignScenarios.*;
import static com.hedera.test.factories.scenarios.ScheduleDeleteScenarios.*;
import static com.hedera.test.factories.scenarios.SystemDeleteScenarios.*;
import static com.hedera.test.factories.scenarios.SystemUndeleteScenarios.*;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.*;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.*;
import static com.hedera.test.factories.scenarios.TokenFreezeScenarios.*;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.*;
import static com.hedera.test.factories.scenarios.TokenKycGrantScenarios.*;
import static com.hedera.test.factories.scenarios.TokenKycRevokeScenarios.*;
import static com.hedera.test.factories.scenarios.TokenMintScenarios.*;
import static com.hedera.test.factories.scenarios.TokenBurnScenarios.*;
import static com.hedera.test.factories.scenarios.TokenDeleteScenarios.*;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.*;
import static com.hedera.test.factories.scenarios.TokenWipeScenarios.*;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.*;
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.*;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;

@RunWith(JUnitPlatform.class)
public class HederaSigningOrderTest {
	private static class TopicAdapter {
		public static TopicSigMetaLookup with(ThrowingTopicLookup delegate) {
			return new TopicSigMetaLookup() {
				@Override
				public SafeLookupResult<TopicSigningMetadata> safeLookup(TopicID id) {
					throw new UnsupportedOperationException();
				}
			};
		}

		public static TopicSigMetaLookup withSafe(
				Function<TopicID, SafeLookupResult<TopicSigningMetadata>> fn
		) {
			return new TopicSigMetaLookup() {
				@Override
				public SafeLookupResult<TopicSigningMetadata> safeLookup(TopicID id) {
					return fn.apply(id);
				}
			};
		}
	}

	private static class FileAdapter {
		public static FileSigMetaLookup with(ThrowingFileLookup lookup) {
			return new FileSigMetaLookup() {
				@Override
				public SafeLookupResult<FileSigningMetadata> safeLookup(FileID id) {
					throw new UnsupportedOperationException();
				}
			};
		}
		public static FileSigMetaLookup withSafe(
				Function<FileID, SafeLookupResult<FileSigningMetadata>> fn
		) {
			return new FileSigMetaLookup() {
				@Override
				public SafeLookupResult<FileSigningMetadata> safeLookup(FileID id) {
					return fn.apply(id);
				}
			};
		}
	}

	private static class AccountAdapter {
		public static AccountSigMetaLookup withSafe(
				Function<AccountID, SafeLookupResult<AccountSigningMetadata>> fn
		) {
			return new AccountSigMetaLookup() {
				@Override
				public SafeLookupResult<AccountSigningMetadata> safeLookup(AccountID id) {
					return fn.apply(id);
				}
			};
		}
	}

	private static class ContractAdapter {
		public static ContractSigMetaLookup with(ThrowingContractLookup lookup) {
			return new ContractSigMetaLookup() {
				@Override
				public SafeLookupResult<ContractSigningMetadata> safeLookup(ContractID id) {
					throw new UnsupportedOperationException();
				}
			};
		}

		public static ContractSigMetaLookup withSafe(
				Function<ContractID, SafeLookupResult<ContractSigningMetadata>> fn
		) {
			return new ContractSigMetaLookup() {
				@Override
				public SafeLookupResult<ContractSigningMetadata> safeLookup(ContractID id) {
					return fn.apply(id);
				}
			};
		}
	}

	@FunctionalInterface
	private interface ThrowingFileLookup {
		FileSigningMetadata lookup(FileID id) throws Exception;
	}
	@FunctionalInterface
	private interface ThrowingContractLookup {
		ContractSigningMetadata lookup(ContractID id) throws Exception;
	}
	@FunctionalInterface
	private interface ThrowingTopicLookup {
		TopicSigningMetadata lookup(TopicID id) throws Exception;
	}

	private static final boolean IN_HANDLE_TXN_DYNAMIC_CTX = false;
	private static final BiPredicate<TransactionBody, HederaFunctionality> WACL_NEVER_SIGNS = (txn, f) -> false;
	private static final BiPredicate<TransactionBody, HederaFunctionality> WACL_ALWAYS_SIGNS = (txn, f) -> true;
	private static final Predicate<TransactionBody> UPDATE_ACCOUNT_ALWAYS_SIGNS = txn -> true;
	private static final Function<ContractSigMetaLookup, SigMetadataLookup> EXC_LOOKUP_FN = contractSigMetaLookup ->
		new DelegatingSigMetadataLookup(
				FileAdapter.with(id -> { throw new Exception(); }),
				AccountAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.MISSING_FILE)),
				contractSigMetaLookup,
				TopicAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.MISSING_FILE)),
				id -> null,
				id -> null);
	private static final SigMetadataLookup EXCEPTION_THROWING_LOOKUP = EXC_LOOKUP_FN.apply(
			ContractAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT))
	);
	private static final SigMetadataLookup INVALID_CONTRACT_THROWING_LOOKUP = EXC_LOOKUP_FN.apply(
			ContractAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT))
	);
	private static final SigMetadataLookup IMMUTABLE_CONTRACT_THROWING_LOOKUP = EXC_LOOKUP_FN.apply(
			ContractAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT))
	);

	private HederaFs hfs;
	private TokenStore tokenStore;
	private ScheduleStore scheduleStore;
	private TransactionBody txn;
	private HederaSigningOrder subject;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	private FCMap<MerkleEntityId, MerkleTopic> topics;
	private SigStatusOrderResultFactory summaryFactory = new SigStatusOrderResultFactory(IN_HANDLE_TXN_DYNAMIC_CTX);
	private SigningOrderResultFactory<SignatureStatus> mockSummaryFactory;

	@Test
	public void reportsInvalidPayerId() throws Throwable {
		// given:
		setupFor(INVALID_PAYER_ID_SCENARIO);
		aMockSummaryFactory();

		// when:
		subject.keysForPayer(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forInvalidAccount(MISSING_ACCOUNT, txn.getTransactionID());
	}

	@Test
	public void reportsGeneralPayerError() throws Throwable {
		// given:
		setupFor(CRYPTO_CREATE_NO_RECEIVER_SIG_SCENARIO, EXCEPTION_THROWING_LOOKUP);
		aMockSummaryFactory();

		// when:
		subject.keysForPayer(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forGeneralPayerError(asAccount(DEFAULT_PAYER_ID), txn.getTransactionID());
	}

	@Test
	public void getsCryptoCreateNoReceiverSigReq() throws Throwable {
		// given:
		setupFor(CRYPTO_CREATE_NO_RECEIVER_SIG_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForPayer(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
	}

	@Test
	public void getsCryptoCreateReceiverSigReq() throws Throwable {
		// given:
		setupFor(CRYPTO_CREATE_RECEIVER_SIG_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_ACCOUNT_KT.asKey()));
	}

	@Test
	public void getsCryptoTransferReceiverNoSigReq() throws Throwable {
		// given:
		setupFor(CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> payerSummary = subject.keysForPayer(txn, summaryFactory);
		SigningOrderResult<SignatureStatus> nonPayerSummary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(payerSummary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
		assertThat(sanityRestored(nonPayerSummary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
	}

	@Test
	public void getsCryptoTransferReceiverSigReq() throws Throwable {
		// given:
		setupFor(CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(DEFAULT_PAYER_KT.asKey(), RECEIVER_SIG_KT.asKey()));
	}

	@Test
	public void reportsMissingCryptoTransferReceiver() throws Throwable {
		// given:
		setupFor(CRYPTO_TRANSFER_MISSING_ACCOUNT_SCENARIO);
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingAccount(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingAccount(MISSING_ACCOUNT, txn.getTransactionID());
	}

	@Test
	public void reportsGeneralErrorInCryptoTransfer() throws Throwable {
		// given:
		setupFor(
				CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO,
				new DelegatingSigMetadataLookup(
						FileAdapter.with(id -> { throw new Exception(); }),
						AccountAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.MISSING_FILE)),
						ContractAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT)),
						TopicAdapter.with(id -> { throw new Exception(); }),
						id -> null,
						id -> null));
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forGeneralError(any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forGeneralError(txn.getTransactionID());
	}

	@Test
	public void getsCryptoUpdateVanillaNewKey() throws Throwable {
		// given:
		@SuppressWarnings("unchecked")
		Predicate<TransactionBody> updateSigReqs = (Predicate<TransactionBody>)mock(Predicate.class);
		setupFor(CRYPTO_UPDATE_WITH_NEW_KEY_SCENARIO, updateSigReqs);
		// and:
		given(updateSigReqs.test(txn)).willReturn(true);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ACCOUNT_KT.asKey(), NEW_ACCOUNT_KT.asKey()));
		verify(updateSigReqs).test(txn);
	}

	@Test
	public void getsCryptoUpdateProtectedNewKey() throws Throwable {
		// given:
		@SuppressWarnings("unchecked")
		Predicate<TransactionBody> updateSigReqs = (Predicate<TransactionBody>)mock(Predicate.class);
		setupFor(CRYPTO_UPDATE_WITH_NEW_KEY_SCENARIO, updateSigReqs);
		// and:
		given(updateSigReqs.test(txn)).willReturn(false);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(NEW_ACCOUNT_KT.asKey()));
		verify(updateSigReqs).test(txn);
	}

	@Test
	public void getsCryptoUpdateProtectedNoNewKey() throws Throwable {
		// given:
		@SuppressWarnings("unchecked")
		Predicate<TransactionBody> updateSigReqs = (Predicate<TransactionBody>)mock(Predicate.class);
		setupFor(CRYPTO_UPDATE_NO_NEW_KEY_SCENARIO, updateSigReqs);
		// and:
		given(updateSigReqs.test(txn)).willReturn(false);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
		verify(updateSigReqs).test(txn);
	}

	@Test
	public void getsCryptoUpdateVanillaNoNewKey() throws Throwable {
		// given:
		@SuppressWarnings("unchecked")
		Predicate<TransactionBody> updateSigReqs = (Predicate<TransactionBody>)mock(Predicate.class);
		setupFor(CRYPTO_UPDATE_NO_NEW_KEY_SCENARIO, updateSigReqs);
		// and:
		given(updateSigReqs.test(txn)).willReturn(true);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
		verify(updateSigReqs).test(txn);
	}

	@Test
	public void reportsCryptoUpdateMissingAccount() throws Throwable {
		setupFor(CRYPTO_UPDATE_MISSING_ACCOUNT_SCENARIO);
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingAccount(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingAccount(MISSING_ACCOUNT, txn.getTransactionID());
	}

	@Test
	public void getsCryptoDeleteNoTransferSigRequired() throws Throwable {
		// given:
		setupFor(CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	public void getsCryptoDeleteTransferSigRequired() throws Throwable {
		// given:
		setupFor(CRYPTO_DELETE_TARGET_RECEIVER_SIG_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ACCOUNT_KT.asKey(), RECEIVER_SIG_KT.asKey()));
	}

	@Test
	public void getsFileCreate() throws Throwable {
		// given:
		setupFor(VANILLA_FILE_CREATE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_WACL_KT.asKey()));
	}

	@Test
	public void getsFileAppend() throws Throwable {
		// given:
		setupFor(VANILLA_FILE_APPEND_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
	}

	@Test
	public void getsFileAppendProtected() throws Throwable {
		// given:
		setupFor(VANILLA_FILE_APPEND_SCENARIO, WACL_NEVER_SIGNS);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsFileAppendImmutable() throws Throwable {
		// given:
		setupFor(IMMUTABLE_FILE_APPEND_SCENARIO, WACL_ALWAYS_SIGNS);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsSysFileAppendByTreasury() throws Throwable {
		// given:
		setupFor(TREASURY_SYS_FILE_APPEND_SCENARIO, WACL_ALWAYS_SIGNS);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsSysFileAppendByMaster() throws Throwable {
		// given:
		setupFor(MASTER_SYS_FILE_APPEND_SCENARIO, WACL_ALWAYS_SIGNS);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsSysFileUpdateByMaster() throws Throwable {
		// given:
		setupFor(MASTER_SYS_FILE_UPDATE_SCENARIO, WACL_ALWAYS_SIGNS);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsSysFileUpdateByTreasury() throws Throwable {
		// given:
		setupFor(TREASURY_SYS_FILE_UPDATE_SCENARIO, WACL_ALWAYS_SIGNS);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void reportsMissingFile() throws Throwable {
		// given:
		setupFor(FILE_APPEND_MISSING_TARGET_SCENARIO);
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingFile(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingFile(TxnHandlingScenario.MISSING_FILE, txn.getTransactionID());
	}

	@Test
	public void getsFileUpdateNoNewWacl() throws Throwable {
		// given:
		setupFor(VANILLA_FILE_UPDATE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
	}

	@Test
	public void getsFileUpdateImmutable() throws Throwable {
		// given:
		setupFor(IMMUTABLE_FILE_UPDATE_SCENARIO, WACL_ALWAYS_SIGNS);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsFileUpdateProtectedNoNewWacl() throws Throwable {
		// given:
		setupFor(VANILLA_FILE_UPDATE_SCENARIO, WACL_NEVER_SIGNS);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsFileUpdateNewWacl() throws Throwable {
		// given:
		setupFor(FILE_UPDATE_NEW_WACL_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(
				summary.getOrderedKeys()),
				contains(MISC_FILE_WACL_KT.asKey(), SIMPLE_NEW_WACL_KT.asKey()));
	}

	@Test
	public void getsFileUpdateProtectedNewWacl() throws Throwable {
		// given:
		setupFor(FILE_UPDATE_NEW_WACL_SCENARIO, WACL_NEVER_SIGNS);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsFileDelete() throws Throwable {
		// given:
		setupFor(VANILLA_FILE_DELETE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
	}

	@Test
	public void getsFileDeleteProtected() throws Throwable {
		// given:
		setupFor(VANILLA_FILE_DELETE_SCENARIO, WACL_NEVER_SIGNS);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
	}

	@Test
	public void getsFileDeleteImmutable() throws Throwable {
		// given:
		setupFor(IMMUTABLE_FILE_DELETE_SCENARIO, WACL_ALWAYS_SIGNS);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsContractCreateNoAdminKey() throws Throwable {
		// given:
		setupFor(CONTRACT_CREATE_NO_ADMIN_KEY);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsContractCreateDeprecatedAdminKey() throws Throwable {
		// given:
		setupFor(CONTRACT_CREATE_DEPRECATED_CID_ADMIN_KEY);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsContractCreateWithAdminKey() throws Throwable {
		// given:
		setupFor(CONTRACT_CREATE_WITH_ADMIN_KEY);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_ADMIN_KT.asKey()));
	}

	@Test
	public void getsContractUpdateWithAdminKey() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_WITH_NEW_ADMIN_KEY);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ADMIN_KT.asKey(), SIMPLE_NEW_ADMIN_KT.asKey()));
	}

	@Test
	public void getsContractUpdateNewExpirationTimeOnly() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_ONLY_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsContractUpdateWithDeprecatedAdminKey() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_DEPRECATED_CID_ADMIN_KEY_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsContractUpdateNewExpirationTimeAndAdminKey() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_ADMIN_KEY_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ADMIN_KT.asKey(), SIMPLE_NEW_ADMIN_KT.asKey()));
	}

	@Test
	public void getsContractUpdateNewExpirationTimeAndProxy() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_PROXY_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ADMIN_KT.asKey()));
	}

	@Test
	public void getsContractUpdateNewExpirationTimeAndAutoRenew() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_AUTORENEW_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ADMIN_KT.asKey()));
	}

	@Test
	public void getsContractUpdateNewExpirationTimeAndFile() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_FILE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ADMIN_KT.asKey()));
	}

	@Test
	public void getsContractUpdateNewExpirationTimeAndMemo() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ADMIN_KT.asKey()));
	}

	@Test
	public void reportsInvalidContract() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO, INVALID_CONTRACT_THROWING_LOOKUP);
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forInvalidContract(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forInvalidContract(MISC_CONTRACT, txn.getTransactionID());
	}

	@Test
	public void reportsImmutableContract() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO, IMMUTABLE_CONTRACT_THROWING_LOOKUP);
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forInvalidContract(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);
	}

	@Test
	public void getsContractDelete() throws Throwable {
		// given:
		setupFor(CONTRACT_DELETE_XFER_ACCOUNT_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ADMIN_KT.asKey(), RECEIVER_SIG_KT.asKey()));
	}

	@Test
	public void getsContractDeleteContractXfer() throws Throwable {
		// given:
		setupFor(CONTRACT_DELETE_XFER_CONTRACT_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ADMIN_KT.asKey(), DILIGENT_SIGNING_PAYER_KT.asKey()));
	}

	@Test
	public void getsSystemDelete() throws Throwable {
		// given:
		setupFor(SYSTEM_DELETE_FILE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	public void getsSystemUndelete() throws Throwable {
		// given:
		setupFor(SYSTEM_UNDELETE_FILE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	public void getsConsensusCreateTopicNoAdminKeyOrAutoRenewAccount() throws Throwable {
		// given:
		setupFor(CONSENSUS_CREATE_TOPIC_NO_ADDITIONAL_KEYS_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForPayer(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
	}

	@Test
	public void getsConsensusCreateTopicAdminKey() throws Throwable {
		// given:
		setupFor(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(SIMPLE_TOPIC_ADMIN_KEY.asKey()));
	}

	@Test
	public void getsConsensusCreateTopicAdminKeyAndAutoRenewAccount() throws Throwable {
		// given:
		setupFor(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()),
				contains(SIMPLE_TOPIC_ADMIN_KEY.asKey(), MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	public void invalidAutoRenewAccountOnConsensusCreateTopicThrows() throws Throwable {
		// given:
		setupFor(CONSENSUS_CREATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO);
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingAutoRenewAccount(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingAutoRenewAccount(MISSING_ACCOUNT, txn.getTransactionID());
	}

	@Test
	public void getsConsensusSubmitMessageNoSubmitKey() throws Throwable {
		// given:
		setupFor(CONSENSUS_SUBMIT_MESSAGE_SCENARIO, hcsMetadataLookup(null, null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsConsensusSubmitMessageWithSubmitKey() throws Throwable {
		// given:
		setupFor(CONSENSUS_SUBMIT_MESSAGE_SCENARIO, hcsMetadataLookup(null, MISC_TOPIC_SUBMIT_KT.asJKey()));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_SUBMIT_KT.asKey()));
	}

	@Test
	public void reportsConsensusSubmitMessageMissingTopic() throws Throwable {
		// given:
		setupFor(CONSENSUS_SUBMIT_MESSAGE_MISSING_TOPIC_SCENARIO);
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingTopic(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingTopic(MISSING_TOPIC, txn.getTransactionID());
	}

	@Test
	public void getsConsensusDeleteTopicNoAdminKey() throws Throwable {
		// given:
		setupFor(CONSENSUS_DELETE_TOPIC_SCENARIO, hcsMetadataLookup(null, null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsConsensusDeleteTopicWithAdminKey() throws Throwable {
		// given:
		setupFor(CONSENSUS_DELETE_TOPIC_SCENARIO, hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KT.asKey()));
	}

	@Test
	public void reportsConsensusDeleteTopicMissingTopic() throws Throwable {
		// given:
		setupFor(CONSENSUS_DELETE_TOPIC_MISSING_TOPIC_SCENARIO);
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingTopic(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingTopic(MISSING_TOPIC, txn.getTransactionID());
	}

	@Test
	public void getsConsensusUpdateTopicNoAdminKey() throws Throwable {
		// given:
		setupFor(CONSENSUS_UPDATE_TOPIC_SCENARIO, hcsMetadataLookup(null, null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void getsConsensusUpdateTopicWithExistingAdminKey() throws Throwable {
		// given:
		setupFor(CONSENSUS_UPDATE_TOPIC_SCENARIO, hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KT.asKey()));
	}

	@Test
	public void getsConsensusUpdateTopicExpiryOnly() throws Throwable {
		// given:
		setupFor(CONSENSUS_UPDATE_TOPIC_EXPIRY_ONLY_SCENARIO,
				hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void reportsConsensusUpdateTopicMissingTopic() throws Throwable {
		setupFor(CONSENSUS_UPDATE_TOPIC_MISSING_TOPIC_SCENARIO, hcsMetadataLookup(null, null));
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingTopic(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingTopic(MISSING_TOPIC, txn.getTransactionID());
	}

	@Test
	public void invalidAutoRenewAccountOnConsensusUpdateTopicThrows() throws Throwable {
		// given:
		setupFor(CONSENSUS_UPDATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO, hcsMetadataLookup(null, null));
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingAutoRenewAccount(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingAutoRenewAccount(MISSING_ACCOUNT, txn.getTransactionID());
	}

	@Test
	public void getsConsensusUpdateTopicNewAdminKey() throws Throwable {
		// given:
		setupFor(CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_SCENARIO, hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KT.asKey(),
				UPDATE_TOPIC_ADMIN_KT.asKey()));
	}

	@Test
	public void getsConsensusUpdateTopicNewAdminKeyAndAutoRenewAccount() throws Throwable {
		// given:
		setupFor(CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO,
				hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KT.asKey(),
				UPDATE_TOPIC_ADMIN_KT.asKey(), MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	public void getsTokenCreateAdminKeyOnly() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_WITH_ADMIN_ONLY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_TREASURY_KT.asKey(), TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	public void getsTokenCreateAdminAndFreeze() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_WITH_ADMIN_AND_FREEZE);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_TREASURY_KT.asKey(), TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	public void getsTokenCreateMissingAdmin() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_MISSING_ADMIN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_TREASURY_KT.asKey()));
	}

	@Test
	public void getsTokenTransactAllSenders() throws Throwable {
		// given:
		setupFor(TOKEN_TRANSACT_WITH_EXTANT_SENDERS);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(FIRST_TOKEN_SENDER_KT.asKey(), SECOND_TOKEN_SENDER_KT.asKey()));
	}

	@Test
	public void getsTokenTransactMovingHbarsReceiverSigReq() throws Throwable {
		// given:
		setupFor(TOKEN_TRANSACT_MOVING_HBARS_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDER);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(FIRST_TOKEN_SENDER_KT.asKey(), RECEIVER_SIG_KT.asKey()));
	}

	@Test
	public void getsTokenTransactMovingHbars() throws Throwable {
		// given:
		setupFor(TOKEN_TRANSACT_MOVING_HBARS_WITH_EXTANT_SENDER);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(FIRST_TOKEN_SENDER_KT.asKey()));
	}

	@Test
	public void getsTokenTransactMissingSenders() throws Throwable {
		// given:
		setupFor(TOKEN_TRANSACT_WITH_MISSING_SENDERS);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	public void getsTokenTransactWithReceiverSigReq() throws Throwable {
		// given:
		setupFor(TOKEN_TRANSACT_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDERS);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(
						FIRST_TOKEN_SENDER_KT.asKey(),
						SECOND_TOKEN_SENDER_KT.asKey(),
						RECEIVER_SIG_KT.asKey()));
	}

	@Test
	public void getsAssociateWithKnownTarget() throws Throwable {
		// given:
		setupFor(TOKEN_ASSOCIATE_WITH_KNOWN_TARGET);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	public void getsAssociateWithMissingTarget() throws Throwable {
		// given:
		setupFor(TOKEN_ASSOCIATE_WITH_MISSING_TARGET);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	public void getsDissociateWithKnownTarget() throws Throwable {
		// given:
		setupFor(TOKEN_DISSOCIATE_WITH_KNOWN_TARGET);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	public void getsDissociateWithMissingTarget() throws Throwable {
		// given:
		setupFor(TOKEN_DISSOCIATE_WITH_MISSING_TARGET);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	public void getsTokenFreezeWithExtantFreezable() throws Throwable {
		// given:
		setupFor(VALID_FREEZE_WITH_EXTANT_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_FREEZE_KT.asKey()));
	}

	@Test
	public void getsTokenUnfreezeWithExtantFreezable() throws Throwable {
		// given:
		setupFor(VALID_UNFREEZE_WITH_EXTANT_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_FREEZE_KT.asKey()));
	}

	@Test
	public void getsTokenGrantKycWithExtantFreezable() throws Throwable {
		// given:
		setupFor(VALID_GRANT_WITH_EXTANT_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_KYC_KT.asKey()));
	}

	@Test
	public void getsTokenRevokeKycWithExtantFreezable() throws Throwable {
		// given:
		setupFor(VALID_REVOKE_WITH_EXTANT_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_KYC_KT.asKey()));
	}

	@Test
	public void getsTokenRevokeKycWithMissingToken() throws Throwable {
		// given:
		setupFor(REVOKE_WITH_MISSING_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_TOKEN_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	public void getsTokenRevokeKycWithoutKyc() throws Throwable {
		// given:
		setupFor(REVOKE_FOR_TOKEN_WITHOUT_KYC);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	public void getsTokenMintWithValidId() throws Throwable {
		// given:
		setupFor(MINT_WITH_SUPPLY_KEYED_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_SUPPLY_KT.asKey()));
	}

	@Test
	public void getsTokenBurnWithValidId() throws Throwable {
		// given:
		setupFor(BURN_WITH_SUPPLY_KEYED_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_SUPPLY_KT.asKey()));
	}

	@Test
	public void getsTokenDeletionWithValidId() throws Throwable {
		// given:
		setupFor(DELETE_WITH_KNOWN_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	public void getsTokenDeletionWithMissingToken() throws Throwable {
		// given:
		setupFor(DELETE_WITH_MISSING_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_TOKEN_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	public void getsTokenDeletionWithNoAdminKey() throws Throwable {
		// given:
		setupFor(DELETE_WITH_MISSING_TOKEN_ADMIN_KEY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	public void getsTokenWipeWithRelevantKey() throws Throwable {
		// given:
		setupFor(VALID_WIPE_WITH_EXTANT_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_WIPE_KT.asKey()));
	}

	@Test
	public void getsUpdateNoSpecialKeys() throws Throwable {
		// given:
		setupFor(UPDATE_WITH_NO_KEYS_AFFECTED);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	public void getsUpdateWithWipe() throws Throwable {
		// given:
		setupFor(UPDATE_WITH_WIPE_KEYED_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	public void getsUpdateWithSupply() throws Throwable {
		// given:
		setupFor(UPDATE_WITH_SUPPLY_KEYED_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	public void getsUpdateWithKyc() throws Throwable {
		// given:
		setupFor(UPDATE_WITH_KYC_KEYED_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	public void getsUpdateWithMissingTreasury() throws Throwable {
		// given:
		setupFor(UPDATE_REPLACING_WITH_MISSING_TREASURY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_ACCOUNT_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	public void getsUpdateWithNewTreasury() throws Throwable {
		// given:
		setupFor(UPDATE_REPLACING_TREASURY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey(), TOKEN_TREASURY_KT.asKey()));
	}

	@Test
	public void getsUpdateWithFreeze() throws Throwable {
		// given:
		setupFor(UPDATE_WITH_FREEZE_KEYED_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	public void getsUpdateReplacingAdmin() throws Throwable {
		// given:
		setupFor(UPDATE_REPLACING_ADMIN_KEY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey(), TOKEN_REPLACE_KT.asKey()));
	}

	@Test
	public void getsTokenUpdateWithMissingToken() throws Throwable {
		// given:
		setupFor(UPDATE_WITH_MISSING_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_TOKEN_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	public void getsTokenUpdateWithNoAdminKey() throws Throwable {
		// given:
		setupFor(UPDATE_WITH_MISSING_TOKEN_ADMIN_KEY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	public void getsTokenCreateWithAutoRenew() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_WITH_AUTO_RENEW);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_TREASURY_KT.asKey(), MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	public void getsTokenCreateWithMissingAutoRenew() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_WITH_MISSING_AUTO_RENEW);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		// and:
		assertEquals(SignatureStatusCode.INVALID_AUTO_RENEW_ACCOUNT_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	public void getsTokenUpdateWithAutoRenew() throws Throwable {
		// given:
		setupFor(TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey(), MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	public void getsTokenUpdateWithMissingAutoRenew() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_WITH_MISSING_AUTO_RENEW);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		// and:
		assertEquals(SignatureStatusCode.INVALID_AUTO_RENEW_ACCOUNT_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	public void getsScheduleCreateAdminKeyOnly() throws Throwable {
		// given:
		setupFor(SCHEDULE_CREATE_MISSING_ADMIN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);
		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	public void getsScheduleCreateNoAdmin() throws Throwable {
		// given:
		setupFor(SCHEDULE_CREATE_WITH_ADMIN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(SCHEDULE_ADMIN_KT.asKey()));
	}

	@Test
	public void getsScheduleSignKnownSchedule() throws Throwable {
		// given:
		setupFor(SCHEDULE_SIGN_KNOWN_SCHEDULE);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);
		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	public void getsScheduleSignWithMissingSchedule() throws Throwable {
		// given:
		setupFor(SCHEDULE_SIGN_MISSING_SCHEDULE);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_SCHEDULE_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	public void getsScheduleDeleteWithMissingSchedule() throws Throwable {
		// given:
		setupFor(SCHEDULE_DELETE_WITH_MISSING_SCHEDULE);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_SCHEDULE_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	public void getsScheduleDeleteWithMissingAdminKey() throws Throwable {
		// given:
		setupFor(SCHEDULE_DELETE_WITH_MISSING_SCHEDULE_ADMIN_KEY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	public void getsScheduleDeleteKnownSchedule() throws Throwable {
		// given:
		setupFor(SCHEDULE_DELETE_WITH_KNOWN_SCHEDULE);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);
		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(SCHEDULE_ADMIN_KT.asKey()));
	}

	private void setupFor(TxnHandlingScenario scenario) throws Throwable {
		setupFor(scenario, WACL_ALWAYS_SIGNS);
	}
	private void setupFor(
			TxnHandlingScenario scenario,
			Predicate<TransactionBody> updateAccountSigns
	) throws Throwable {
		setupFor(scenario, WACL_ALWAYS_SIGNS, updateAccountSigns);
	}
	private void setupFor(
			TxnHandlingScenario scenario,
			BiPredicate<TransactionBody, HederaFunctionality> waclSigns
	) throws Throwable {
		setupFor(scenario, waclSigns, UPDATE_ACCOUNT_ALWAYS_SIGNS);
	}
	private void setupFor(
			TxnHandlingScenario scenario,
			SigMetadataLookup sigMetadataLookup
	) throws Throwable {
		setupFor(scenario, WACL_ALWAYS_SIGNS, UPDATE_ACCOUNT_ALWAYS_SIGNS, Optional.of(sigMetadataLookup));
	}
	private void setupFor(
			TxnHandlingScenario scenario,
			BiPredicate<TransactionBody, HederaFunctionality> waclSigns,
			Predicate<TransactionBody> updateAccountSigns
	) throws Throwable {
		setupFor(scenario, waclSigns, updateAccountSigns, Optional.empty());
	}
	private void setupFor(
			TxnHandlingScenario scenario,
			BiPredicate<TransactionBody, HederaFunctionality> waclSigns,
			Predicate<TransactionBody> updateAccountSigns,
			Optional<SigMetadataLookup> sigMetaLookup
	) throws Throwable {
		txn = scenario.platformTxn().getTxn();
		hfs = scenario.hfs();
		accounts = scenario.accounts();
		topics = scenario.topics();
		tokenStore = scenario.tokenStore();
		scheduleStore = scenario.scheduleStore();

		subject = new HederaSigningOrder(
				new MockEntityNumbers(),
				sigMetaLookup.orElse(
						defaultLookupsFor(
								hfs,
								() -> accounts,
								() -> topics,
								SigMetadataLookup.REF_LOOKUP_FACTORY.apply(tokenStore),
								SigMetadataLookup.SCHEDULE_REF_LOOKUP_FACTORY.apply(scheduleStore))),
				updateAccountSigns,
				waclSigns);
	}

	private void aMockSummaryFactory() {
		mockSummaryFactory = (SigningOrderResultFactory<SignatureStatus>)mock(SigningOrderResultFactory.class);
	}

	private SigMetadataLookup hcsMetadataLookup(JKey adminKey, JKey submitKey) {
		return new DelegatingSigMetadataLookup(
				FileAdapter.with(id -> { throw new Exception(); }),
				AccountAdapter.withSafe(id -> {
					if (id.equals(asAccount(MISC_ACCOUNT_ID))) {
						try {
							return new SafeLookupResult<>(
									new AccountSigningMetadata(MISC_ACCOUNT_KT.asJKey(), false));
						} catch (Exception e) {
							throw new IllegalArgumentException(e);
						}
					} else {
						return SafeLookupResult.failure(KeyOrderingFailure.MISSING_ACCOUNT);
					}
				}),
				ContractAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT)),
				TopicAdapter.withSafe(id -> {
					if (id.equals(asTopic(EXISTING_TOPIC_ID))) {
						return new SafeLookupResult<>(new TopicSigningMetadata(adminKey, submitKey));
					} else {
						return SafeLookupResult.failure(KeyOrderingFailure.INVALID_TOPIC);
					}
				}),
				id -> null,
				id -> null
		);
	}

	private List<Key> sanityRestored(List<JKey> jKeys) {
		return jKeys.stream().map(jKey -> {
					try {
						return JKey.mapJKey(jKey);
					} catch (Exception ignore) { }
					throw new AssertionError("All keys should be mappable!");
				}
			).collect(toList());
	}
}
