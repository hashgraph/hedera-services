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

import com.hedera.services.config.EntityNumbers;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.sigs.metadata.TopicSigningMetadata;
import com.hederahashgraph.api.proto.java.*;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.AdminKeyNotExistException;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.legacy.exception.InvalidAutoRenewAccountIDException;
import com.hedera.services.legacy.exception.InvalidContractIDException;
import com.hedera.services.legacy.exception.InvalidFileIDException;
import com.hedera.services.legacy.exception.InvalidTopicIDException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * Encapsulates all policies related to:
 * <ol>
 *     <li>Which Hedera keys must have active signatures for a given gRPC transaction to be valid; and,</li>
 *     <li>The <i>order</i> in which Hedera {@link com.hederahashgraph.api.proto.java.Signature}
 *         instances must be supplied to test activation of these keys when the gRPC transaction has a
 *         {@link com.hederahashgraph.api.proto.java.SignatureList}.</li>
 * </ol>
 * The second item is really an implementation detail, as logically this class could just as well
 * return a {@code Set<JKey>} instead of a {@code List<JKey>}. However, until there are no clients
 * using the deprecated {@code SignatureList}, it is an absolutely crucial detail.
 *
 * Two strategy predicates are injected into this class, one with logic to decide if the WACL for a
 * file targeted by the gRPC transaction must have an active signature; and one with logic to make an
 * equivalent decision for a crypto account.
 *
 * @author Michael Tinker
 */
public class HederaSigningOrder {
	private static final Logger log = LogManager.getLogger(HederaSigningOrder.class);

	final EntityNumbers entityNums;
	final SigMetadataLookup sigMetaLookup;
	final Predicate<TransactionBody> updateAccountSigns;
	final BiPredicate<TransactionBody, HederaFunctionality> targetWaclSigns;

	public HederaSigningOrder(
			EntityNumbers entityNums,
			SigMetadataLookup sigMetaLookup,
			Predicate<TransactionBody> updateAccountSigns,
			BiPredicate<TransactionBody, HederaFunctionality> targetWaclSigns
	) {
		this.entityNums = entityNums;
		this.sigMetaLookup = sigMetaLookup;
		this.targetWaclSigns = targetWaclSigns;
		this.updateAccountSigns = updateAccountSigns;
	}

	/**
	 * Uses the provided factory to summarize an attempt to compute the canonical signing order
	 * of the Hedera key(s) that must be active for the payer of the given gRPC transaction.
	 *
	 * @param txn the gRPC transaction of interest.
	 * @param factory the result factory to use to summarize the listing attempt.
	 * @param <T> the type of error report created by the factory.
	 * @return a {@link SigningOrderResult} summarizing the listing attempt.
	 */
	public <T> SigningOrderResult<T> keysForPayer(TransactionBody txn, SigningOrderResultFactory<T> factory) {
		SigningOrderResult<T> payerSigningOrder = keyOrder(factory, () -> List.of(forPayer(txn, factory)));
		log.debug("Signing order result for payer Hedera keys of txn {} was {}", txn, payerSigningOrder);
		return payerSigningOrder;
	}

	/**
	 * Uses the provided factory to summarize an attempt to compute the canonical signing order
	 * of the Hedera key(s) that must be active for any Hedera entities involved in a non-payer
	 * role in the given gRPC transaction. (Which could also include the payer crypto account.)
	 *
	 * @param txn the gRPC transaction of interest.
	 * @param factory the result factory to use to summarize the listing attempt.
	 * @param <T> the type of error report created by the factory.
	 * @return a {@link SigningOrderResult} summarizing the listing attempt.
	 */
	public <T> SigningOrderResult<T> keysForOtherParties(TransactionBody txn, SigningOrderResultFactory<T> factory) {
		SigningOrderResult<T> othersSigningOrder = keyOrder(factory, () -> forOtherInvolvedParties(txn, factory));
		log.debug("Signing order result for non-payer Hedera keys of txn {} was {}", txn, othersSigningOrder);
		return othersSigningOrder;
	}

	private <T> SigningOrderResult<T> keyOrder(
			SigningOrderResultFactory<T> factory,
			OrderSupplier supplier
	) {
		try {
			return factory.forValidOrder(supplier.get());
		} catch (SigningOrderException soe) {
			@SuppressWarnings("unchecked")
			SigningOrderResult<T> summary = (SigningOrderResult<T>)soe.getErrorReport();
			return summary;
		}
	}

	private JKey forPayer(
			TransactionBody txn,
			SigningOrderResultFactory<?> factory
	) throws SigningOrderException {
		AccountID payer = AccountID.getDefaultInstance();
		try {
			payer = txn.getTransactionID().getAccountID();
			return sigMetaLookup.lookup(payer).getKey();
		} catch (InvalidAccountIDException iae) {
			throw new SigningOrderException(factory.forInvalidAccount(payer, txn.getTransactionID()));
		} catch (Exception e) {
			throw new SigningOrderException(factory.forGeneralPayerError(payer, txn.getTransactionID()));
		}
	}

	private List<JKey> forOtherInvolvedParties(
			TransactionBody txn,
			SigningOrderResultFactory<?> factory
	) throws SigningOrderException {
		try {
			return Stream.of(
					forInvolvedFiles(txn),
					forInvolvedAccounts(txn),
					forInvolvedContracts(txn),
					forInvolvedTopics(txn)
			).flatMap(List::stream).collect(toList());
		} catch (InvalidFileIDException ife) {
			throw new SigningOrderException(factory.forMissingFile(ife.getFileId(), txn.getTransactionID()));
		} catch (InvalidAccountIDException iae) {
			throw new SigningOrderException(factory.forMissingAccount(iae.getAccountId(), txn.getTransactionID()));
		} catch (InvalidContractIDException ice) {
			throw new SigningOrderException(factory.forInvalidContract(ice.getContractId(), txn.getTransactionID()));
		} catch (InvalidTopicIDException ite) {
			throw new SigningOrderException(factory.forMissingTopic(ite.getTopicId(), txn.getTransactionID()));
		} catch (AdminKeyNotExistException ane) {
			throw new SigningOrderException(factory.forImmutableContract(ane.getContractId(), txn.getTransactionID()));
		} catch (InvalidAutoRenewAccountIDException e) {
			throw new SigningOrderException(factory.forMissingAutoRenewAccount(e.getAccountId(), txn.getTransactionID()));
		} catch (Exception e) {
			throw new SigningOrderException(factory.forGeneralError(txn.getTransactionID()));
		}
	}

	private List<JKey> forInvolvedContracts(TransactionBody txn) throws Exception {
		if (txn.hasContractCreateInstance()) {
			return forContractCreate(txn.getContractCreateInstance());
		} else if (txn.hasContractUpdateInstance()) {
			return forContractUpdate(txn.getContractUpdateInstance());
		} else if (txn.hasContractDeleteInstance()) {
			return forContractDelete(txn.getContractDeleteInstance());
		} else {
			return EMPTY_LIST;
		}
	}

	private List<JKey> forInvolvedAccounts(TransactionBody txn) throws Exception {
		if (txn.hasCryptoCreateAccount()) {
			return forCryptoCreate(txn.getCryptoCreateAccount());
		} else if (txn.hasCryptoTransfer()) {
			return forCryptoTransfer(txn.getCryptoTransfer());
		} else if (txn.hasCryptoUpdateAccount()) {
			return forCryptoUpdate(txn.getCryptoUpdateAccount(), updateAccountSigns.test(txn));
		} else if (txn.hasCryptoDelete()) {
			return forCryptoDelete(txn.getCryptoDelete());
	    } else {
			return EMPTY_LIST;
		}
	}

	private List<JKey> forInvolvedFiles(TransactionBody txn) throws Exception {
		if (isFileTxn(txn)) {
			var isSuperuser = entityNums.accounts().isSuperuser(txn.getTransactionID().getAccountID().getAccountNum());
			if (txn.hasFileCreate()) {
				return forFileCreate(txn.getFileCreate());
			} else if (txn.hasFileAppend()) {
				var waclShouldSign = targetWaclSigns.test(txn, HederaFunctionality.FileAppend);
				return forFileAppend(txn.getFileAppend(), waclShouldSign, isSuperuser);
			} else if (txn.hasFileUpdate()) {
				var waclShouldSign = targetWaclSigns.test(txn, HederaFunctionality.FileUpdate);
				return forFileUpdate(txn.getFileUpdate(), waclShouldSign, isSuperuser);
			} else if (txn.hasFileDelete()) {
				return forFileDelete(txn.getFileDelete());
			} else {
				return EMPTY_LIST;
			}
		}
		return EMPTY_LIST;
	}

	private boolean isFileTxn(TransactionBody txn) {
		return txn.hasFileCreate() || txn.hasFileAppend() || txn.hasFileUpdate() || txn.hasFileDelete();
	}

	private boolean isTopicTxn(TransactionBody txn) {
		return txn.hasConsensusCreateTopic() || txn.hasConsensusSubmitMessage() || txn.hasConsensusUpdateTopic() ||
				txn.hasConsensusDeleteTopic();
	}

	private List<JKey> forInvolvedTopics(TransactionBody txn) throws Exception {
		if (isTopicTxn(txn)) {
			if (txn.hasConsensusCreateTopic()) {
				return forConsensusCreateTopic(txn.getConsensusCreateTopic());
			} else if (txn.hasConsensusSubmitMessage()) {
				return forConsensusSubmitMessage(txn.getConsensusSubmitMessage());
			} else if (txn.hasConsensusUpdateTopic()) {
				return forConsensusUpdateTopic(txn.getConsensusUpdateTopic());
			} else if (txn.hasConsensusDeleteTopic()) {
				return forConsensusDeleteTopic(txn.getConsensusDeleteTopic());
			} else {
				return EMPTY_LIST;
			}
		} else {
			return EMPTY_LIST;
		}
	}

	private List<JKey> forContractDelete(ContractDeleteTransactionBody op) throws Exception {
		return List.of(sigMetaLookup.lookup(op.getContractID()).getKey());
	}

	private List<JKey> forContractUpdate(ContractUpdateTransactionBody op) throws Exception {
		return accumulated(keys -> {
			if (needsCurrentAdminSig(op)) {
				keys.add(sigMetaLookup.lookup(op.getContractID()).getKey());
			}
			if (hasNondeprecatedAdminKey(op)) {
				keys.add(JKey.mapKey(op.getAdminKey()));
			}
		});
	}
	private boolean needsCurrentAdminSig(ContractUpdateTransactionBody op) {
		return !op.hasExpirationTime()
				|| hasNondeprecatedAdminKey(op)
				|| op.hasProxyAccountID()
				|| op.hasAutoRenewPeriod()
				|| op.hasFileID()
				|| op.getMemo().length() > 0;
	}
	private boolean hasNondeprecatedAdminKey(ContractUpdateTransactionBody op) {
		return op.hasAdminKey() && !op.getAdminKey().hasContractID();
	}

	private List<JKey> forContractCreate(ContractCreateTransactionBody op) throws Exception {
		return op.hasAdminKey() && !op.getAdminKey().hasContractID()
				? List.of(JKey.mapKey(op.getAdminKey()))
				: EMPTY_LIST;
	}

	private List<JKey> forFileDelete(FileDeleteTransactionBody op) throws Exception {
		return forPossiblyImmutableFile(op.getFileID());
	}

	private List<JKey> forFileUpdate(
			FileUpdateTransactionBody op,
			boolean waclShouldSign,
			boolean payerIsSuperuser
	) throws Exception {
		var target = op.getFileID();
		if (payerIsSuperuser && entityNums.isSystemFile(target)) {
			return emptyList();
		} else {
			return accumulated(keys -> {
				if (waclShouldSign) {
					keys.addAll(forPossiblyImmutableFile(op.getFileID()));
					if (op.hasKeys()) {
						keys.add(asJKey(op.getKeys()));
					}
				}
			});
		}
	}

	private List<JKey> forFileAppend(
			FileAppendTransactionBody op,
			boolean waclShouldSign,
			boolean payerIsSuperuser
	) throws Exception {
		var target = op.getFileID();
		if (payerIsSuperuser && entityNums.isSystemFile(target)) {
			return emptyList();
		} else {
			return waclShouldSign ? forPossiblyImmutableFile(target) : emptyList();
		}
	}

	private List<JKey> forPossiblyImmutableFile(FileID fid) throws Exception {
		var wacl = sigMetaLookup.lookup(fid).getWacl();
		return wacl.isEmpty() ? EMPTY_LIST : List.of(wacl);
	}

	private List<JKey> forFileCreate(FileCreateTransactionBody op) throws Exception {
		return List.of(asJKey(op.getKeys()));
	}
	private JKey asJKey(KeyList keyList) throws Exception {
		return JKey.mapKey(Key.newBuilder().setKeyList(keyList).build());
	}

	private List<JKey> forCryptoDelete(CryptoDeleteTransactionBody op) throws Exception {
		return accumulated(keys -> {
			AccountSigningMetadata targetSigMeta = sigMetaLookup.lookup(op.getDeleteAccountID());
			keys.add(targetSigMeta.getKey());
			AccountSigningMetadata transferSigMeta = sigMetaLookup.lookup(op.getTransferAccountID());
			if (transferSigMeta.isReceiverSigRequired()) {
				keys.add(transferSigMeta.getKey());
			}
		});
	}

	private List<JKey> forCryptoUpdate(CryptoUpdateTransactionBody op, boolean targetMustSign) throws Exception {
		return accumulated(keys -> {
			AccountID target = op.getAccountIDToUpdate();
			AccountSigningMetadata sigMeta = sigMetaLookup.lookup(target);
			if (targetMustSign) {
				keys.add(sigMeta.getKey());
			}
			if (hasNewAccountKey(op)) {
				keys.add(JKey.mapKey(op.getKey()));
			}
		});
	}
	private boolean hasNewAccountKey(CryptoUpdateTransactionBody op) {
		return op.getKey().hasKeyList() || op.getKey().hasThresholdKey() || !op.getKey().getEd25519().isEmpty();
	}

	private List<JKey> forCryptoTransfer(CryptoTransferTransactionBody op) throws Exception {
		return accumulated(keys -> {
			for (AccountAmount delta : op.getTransfers().getAccountAmountsList()) {
				AccountSigningMetadata accountMeta = sigMetaLookup.lookup(delta.getAccountID());
				if (delta.getAmount() < 0L || accountMeta.isReceiverSigRequired()) {
					keys.add(accountMeta.getKey());
				}
			}
		});
	}

	private List<JKey> forCryptoCreate(CryptoCreateTransactionBody op) throws Exception {
		return op.getReceiverSigRequired() ? List.of(JKey.mapKey(op.getKey())) : EMPTY_LIST;
	}

	/**
	 * Verify that:
	 * <ul>
	 *   <li>if the ConsensusCreateTopic transaction specifies an adminKey - that key must sign the transaction</li>
	 *   <li>if the ConsensusCreateTopic transaction specifies an autoRenewAccount - that account's key must sign the
	 *   transaction</li>
	 * </ul>
	 * @param op
	 * @return
	 * @throws Exception if the autorenew account does not exist
	 */
	private List<JKey> forConsensusCreateTopic(ConsensusCreateTopicTransactionBody op) throws Exception {
		return accumulated(keys -> {
			if (op.hasAdminKey()) {
				keys.add(JKey.mapKey(op.getAdminKey()));
			}
			if (op.hasAutoRenewAccount()) {
				try {
					keys.add(sigMetaLookup.lookup(op.getAutoRenewAccount()).getKey());
				} catch (InvalidAccountIDException e) {
					throw new InvalidAutoRenewAccountIDException(e.getMessage(), e.getAccountId());
				}

			}
		});
	}

	/**
	 * Verify that topic's submitKey is used (if there is one).
	 * @param op
	 * @return
	 * @throws Exception if the specified topic does not exist.
	 */
	private List<JKey> forConsensusSubmitMessage(ConsensusSubmitMessageTransactionBody op) throws Exception {
		TopicSigningMetadata sigMeta = sigMetaLookup.lookup(op.getTopicID());
		return sigMeta.hasSubmitKey() ? List.of(sigMeta.getSubmitKey()) : EMPTY_LIST;
	}

	/**
	 * Verify that topic's adminKey both before and after the update is validated, and the autoRenewAccount's
	 * key is used if a new autoRenewAccount is set.
	 * Unless the update is expirationTime only. Then no additional keys are used.
	 * @param op
	 * @return
	 * @throws Exception if the specified topic does not exist or autoRenewAccount does not exist.
	 */
	private List<JKey> forConsensusUpdateTopic(ConsensusUpdateTopicTransactionBody op) throws Exception {

		// Updating a topic's expirationTime (only) is allowed for anyone.
		if (op.hasExpirationTime() && !op.hasMemo() && !op.hasAdminKey() && !op.hasSubmitKey() &&
				!op.hasAutoRenewPeriod() && !op.hasAutoRenewAccount()) {
			return EMPTY_LIST;
		}

		return accumulateKeysforConsensusUpdateTopic(op);
	}

	private List<JKey> accumulateKeysforConsensusUpdateTopic(ConsensusUpdateTopicTransactionBody op) throws Exception {
		TopicSigningMetadata sigMeta = sigMetaLookup.lookup(op.getTopicID());
		List<JKey> keys = new ArrayList<>();

		if (sigMeta.hasAdminKey()) {
			keys.add(sigMeta.getAdminKey());
		}
		if (op.hasAdminKey()) {
			keys.add(JKey.mapKey(op.getAdminKey()));
		}
		if (op.hasAutoRenewAccount()) {
			AccountID autoRenewAccount = op.getAutoRenewAccount();
			// If set to 0.0.0, it means autoRenewAccount should be cleared
			if (autoRenewAccount.getShardNum() != 0 || autoRenewAccount.getRealmNum() != 0
					|| autoRenewAccount.getAccountNum() != 0) {
				try {
					keys.add(sigMetaLookup.lookup(op.getAutoRenewAccount()).getKey());
				} catch (InvalidAccountIDException e) {
					throw new InvalidAutoRenewAccountIDException(e.getMessage(), e.getAccountId());
				}
			}
		}

		return keys;
	}

	/**
	 * Verify that topic's adminKey is used (if there is one).
	 * @param op
	 * @return
	 * @throws Exception if the specified topic does not exist.
	 */
	private List<JKey> forConsensusDeleteTopic(ConsensusDeleteTopicTransactionBody op) throws Exception {
		TopicSigningMetadata sigMeta = sigMetaLookup.lookup(op.getTopicID());
		return sigMeta.hasAdminKey() ? List.of(sigMeta.getAdminKey()) : EMPTY_LIST;
	}

	private List<JKey> accumulated(KeyAccumulation using) throws Exception {
		List<JKey> keys = new ArrayList<>();
		using.accept(keys);
		return keys;
	}

	private interface KeyAccumulation {
		void accept(List<JKey> l) throws Exception;
	}

	private interface OrderSupplier {
		List<JKey> get() throws SigningOrderException;
	}
}
