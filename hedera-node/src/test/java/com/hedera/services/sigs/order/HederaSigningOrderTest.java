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
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.files.HederaFs;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.sigs.metadata.TopicSigningMetadata;
import com.hedera.services.sigs.metadata.lookups.ContractSigMetaLookup;
import com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.exception.AdminKeyNotExistException;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.legacy.exception.InvalidContractIDException;
import com.hedera.services.legacy.exception.InvalidTopicIDException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
import static com.hedera.test.factories.scenarios.SystemDeleteScenarios.*;
import static com.hedera.test.factories.scenarios.SystemUndeleteScenarios.*;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.*;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;

@RunWith(JUnitPlatform.class)
public class HederaSigningOrderTest {
	private static final boolean IN_HANDLE_TXN_DYNAMIC_CTX = false;
	private static final BiPredicate<TransactionBody, HederaFunctionality> WACL_NEVER_SIGNS = (txn, f) -> false;
	private static final BiPredicate<TransactionBody, HederaFunctionality> WACL_ALWAYS_SIGNS = (txn, f) -> true;
	private static final Predicate<TransactionBody> UPDATE_ACCOUNT_ALWAYS_SIGNS = txn -> true;
	private static final Function<ContractSigMetaLookup, SigMetadataLookup> EXC_LOOKUP_FN = contractSigMetaLookup ->
		new DelegatingSigMetadataLookup(
				id -> { throw new Exception(); },
				id -> { throw new Exception(); },
				contractSigMetaLookup,
				id -> { throw new Exception(); });
	private static final SigMetadataLookup EXCEPTION_THROWING_LOOKUP = EXC_LOOKUP_FN.apply(
			id -> { throw new Exception(); }
	);
	private static final SigMetadataLookup INVALID_CONTRACT_THROWING_LOOKUP = EXC_LOOKUP_FN.apply(
			id -> {
				throw new InvalidContractIDException("Oops!", MISC_CONTRACT);
			}
	);
	private static final SigMetadataLookup IMMUTABLE_CONTRACT_THROWING_LOOKUP = EXC_LOOKUP_FN.apply(
			id -> { throw new AdminKeyNotExistException("Oops!", MISC_CONTRACT); }
	);

	private HederaFs hfs;
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
						id -> { throw new Exception(); },
						id -> {
							if (id.equals(asAccount(DEFAULT_PAYER_ID))) {
								return new AccountSigningMetadata(DEFAULT_PAYER_KT.asJKey(), false);
							} else {
								/* Throw an exception for any account other than the default payer. */
								throw new Exception();
							}
						},
						id -> { throw new Exception(); },
						id -> { throw new Exception(); }
				)
		);
		aMockSummaryFactory();

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
		// given:
		setupFor(CRYPTO_UPDATE_MISSING_ACCOUNT_SCENARIO);
		// and:
		aMockSummaryFactory();

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

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingFile(MISSING_FILE, txn.getTransactionID());
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

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forImmutableContract(MISC_CONTRACT, txn.getTransactionID());
	}

	@Test
	public void getsContractDelete() throws Throwable {
		// given:
		setupFor(CONTRACT_DELETE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ADMIN_KT.asKey()));
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
		setupFor(CONSENSUS_SUBMIT_MESSAGE_SCENARIO, hcsMetadataLookup(null, MISC_TOPIC_SUBMIT_KEY.asJKey()));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_SUBMIT_KEY.asKey()));
	}

	@Test
	public void reportsConsensusSubmitMessageMissingTopic() throws Throwable {
		// given:
		setupFor(CONSENSUS_SUBMIT_MESSAGE_MISSING_TOPIC_SCENARIO);
		// and:
		aMockSummaryFactory();

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
		setupFor(CONSENSUS_DELETE_TOPIC_SCENARIO, hcsMetadataLookup(MISC_TOPIC_ADMIN_KEY.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KEY.asKey()));
	}

	@Test
	public void reportsConsensusDeleteTopicMissingTopic() throws Throwable {
		// given:
		setupFor(CONSENSUS_DELETE_TOPIC_MISSING_TOPIC_SCENARIO);
		// and:
		aMockSummaryFactory();

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
		setupFor(CONSENSUS_UPDATE_TOPIC_SCENARIO, hcsMetadataLookup(MISC_TOPIC_ADMIN_KEY.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KEY.asKey()));
	}

	@Test
	public void getsConsensusUpdateTopicExpiratyOnly() throws Throwable {
		// given:
		setupFor(CONSENSUS_UPDATE_TOPIC_EXPIRY_ONLY_SCENARIO,
				hcsMetadataLookup(MISC_TOPIC_ADMIN_KEY.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	public void reportsConsensusUpdateTopicMissingTopic() throws Throwable {
		// given:
		setupFor(CONSENSUS_UPDATE_TOPIC_MISSING_TOPIC_SCENARIO, hcsMetadataLookup(null, null));
		aMockSummaryFactory();

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

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingAutoRenewAccount(MISSING_ACCOUNT, txn.getTransactionID());
	}

	@Test
	public void getsConsensusUpdateTopicNewAdminKey() throws Throwable {
		// given:
		setupFor(CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_SCENARIO, hcsMetadataLookup(MISC_TOPIC_ADMIN_KEY.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KEY.asKey(),
				UPDATE_TOPIC_ADMIN_KEY.asKey()));
	}

	@Test
	public void getsConsensusUpdateTopicNewAdminKeyAndAutoRenewAccount() throws Throwable {
		// given:
		setupFor(CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO,
				hcsMetadataLookup(MISC_TOPIC_ADMIN_KEY.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KEY.asKey(),
				UPDATE_TOPIC_ADMIN_KEY.asKey(), MISC_ACCOUNT_KT.asKey()));
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

		subject = new HederaSigningOrder(
				new MockEntityNumbers(),
				sigMetaLookup.orElse(defaultLookupsFor(hfs, () -> accounts, () -> topics)),
				updateAccountSigns,
				waclSigns);
	}

	private void aMockSummaryFactory() {
		mockSummaryFactory = (SigningOrderResultFactory<SignatureStatus>)mock(SigningOrderResultFactory.class);
	}

	private SigMetadataLookup hcsMetadataLookup(JKey adminKey, JKey submitKey) {
		return new DelegatingSigMetadataLookup(
				id -> { throw new Exception(); },
				id -> {
					if (id.equals(asAccount(MISC_ACCOUNT_ID))) {
						return new AccountSigningMetadata(MISC_ACCOUNT_KT.asJKey(), false);
					} else {
						/* Throw an exception for any account other than the default payer. */
						throw new InvalidAccountIDException("invalid account", id);
					}
				},
				id -> { throw new Exception(); },
				id -> {
					if (id.equals(asTopic(EXISTING_TOPIC_ID))) {
						return new TopicSigningMetadata(adminKey, submitKey);
					} else {
						/* Throw an exception for any account other than the default payer. */
						throw new InvalidTopicIDException("invalid topic", id);
					}
				}
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
