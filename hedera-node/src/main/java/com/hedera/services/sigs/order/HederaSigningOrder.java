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
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.sigs.metadata.TokenSigningMetadata;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_TOPIC;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_AUTORENEW_ACCOUNT;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * Encapsulates all policies related to:
 * <ol>
 * <li>Which Hedera keys must have active signatures for a given gRPC transaction to be valid; and,</li>
 * <li>The <i>order</i> in which Hedera {@link com.hederahashgraph.api.proto.java.Signature}
 * instances must be supplied to test activation of these keys when the gRPC transaction has a
 * {@link com.hederahashgraph.api.proto.java.SignatureList}.</li>
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
	 * @param txn
	 * 		the gRPC transaction of interest.
	 * @param factory
	 * 		the result factory to use to summarize the listing attempt.
	 * @param <T>
	 * 		the type of error report created by the factory.
	 * @return a {@link SigningOrderResult} summarizing the listing attempt.
	 */
	public <T> SigningOrderResult<T> keysForPayer(TransactionBody txn, SigningOrderResultFactory<T> factory) {
		return orderForPayer(txn, factory);
	}

	/**
	 * Uses the provided factory to summarize an attempt to compute the canonical signing order
	 * of the Hedera key(s) that must be active for any Hedera entities involved in a non-payer
	 * role in the given gRPC transaction. (Which could also include the payer crypto account.)
	 *
	 * @param txn
	 * 		the gRPC transaction of interest.
	 * @param factory
	 * 		the result factory to use to summarize the listing attempt.
	 * @param <T>
	 * 		the type of error report created by the factory.
	 * @return a {@link SigningOrderResult} summarizing the listing attempt.
	 */
	public <T> SigningOrderResult<T> keysForOtherParties(TransactionBody txn, SigningOrderResultFactory<T> factory) {
		var cryptoOrder = forCrypto(txn, factory);
		if (cryptoOrder.isPresent()) {
			return cryptoOrder.get();
		}

		var consensusOrder = forConsensus(txn, factory);
		if (consensusOrder.isPresent()) {
			return consensusOrder.get();
		}

		var tokenOrder = forToken(txn, factory);
		if (tokenOrder.isPresent()) {
			return tokenOrder.get();
		}

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
			SigningOrderResult<T> summary = (SigningOrderResult<T>) soe.getErrorReport();
			return summary;
		}
	}

	private <T> SigningOrderResult<T> orderForPayer(
			TransactionBody txn,
			SigningOrderResultFactory<T> factory
	) {
		var payer = txn.getTransactionID().getAccountID();
		var result = sigMetaLookup.accountSigningMetaFor(payer);
		if (result.succeeded()) {
			return factory.forValidOrder(List.of(result.metadata().getKey()));
		} else {
			if (result.failureIfAny() == MISSING_ACCOUNT) {
				return factory.forInvalidAccount(payer, txn.getTransactionID());
			} else {
				return factory.forGeneralPayerError(payer, txn.getTransactionID());
			}
		}
	}

	private List<JKey> forOtherInvolvedParties(
			TransactionBody txn,
			SigningOrderResultFactory<?> factory
	) throws SigningOrderException {
		try {
			return Stream.of(
					forInvolvedFiles(txn),
					forInvolvedContracts(txn)
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
			throw new SigningOrderException(
					factory.forMissingAutoRenewAccount(e.getAccountId(), txn.getTransactionID()));
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

	private <T> Optional<SigningOrderResult<T>> forCrypto(
			TransactionBody txn,
			SigningOrderResultFactory<T> factory
	) {
		if (txn.hasCryptoCreateAccount()) {
			return Optional.of(cryptoCreate(
					txn.getCryptoCreateAccount(), factory));
		} else if (txn.hasCryptoTransfer()) {
			return Optional.of(cryptoTransfer(
					txn.getTransactionID(), txn.getCryptoTransfer(), factory));
		} else if (txn.hasCryptoUpdateAccount()) {
			return Optional.of(cryptoUpdate(
					txn.getTransactionID(), updateAccountSigns.test(txn), txn.getCryptoUpdateAccount(), factory));
		} else if (txn.hasCryptoDelete()) {
			return Optional.of(cryptoDelete(
					txn.getTransactionID(), txn.getCryptoDelete(), factory));
		} else {
			return Optional.empty();
		}
	}

	private <T> Optional<SigningOrderResult<T>> forToken(
			TransactionBody txn,
			SigningOrderResultFactory<T> factory
	) {
		if (txn.hasTokenCreation()) {
			return Optional.of(tokenCreate(txn.getTransactionID(), txn.getTokenCreation(), factory));
		} else if (txn.hasTokenTransfers()) {
			return Optional.of(tokenTransact(txn.getTransactionID(), txn.getTokenTransfers(), factory));
		} else if (txn.hasTokenFreeze()) {
			return Optional.of(tokenFreezing(txn.getTransactionID(), txn.getTokenFreeze().getToken(), factory));
		} else if (txn.hasTokenUnfreeze()) {
			return Optional.of(tokenFreezing(txn.getTransactionID(), txn.getTokenUnfreeze().getToken(), factory));
		} else if (txn.hasTokenGrantKyc()) {
			return Optional.of(tokenKnowing(txn.getTransactionID(), txn.getTokenGrantKyc().getToken(), factory));
		} else if (txn.hasTokenRevokeKyc()) {
			return Optional.of(tokenKnowing(txn.getTransactionID(), txn.getTokenRevokeKyc().getToken(), factory));
		} else if (txn.hasTokenMint()) {
			return Optional.of(tokenRefloating(txn.getTransactionID(), txn.getTokenMint().getToken(), factory));
		} else if (txn.hasTokenBurn()) {
			return Optional.of(tokenRefloating(txn.getTransactionID(), txn.getTokenBurn().getToken(), factory));
		} else if (txn.hasTokenWipe()) {
			return Optional.of(tokenWiping(txn.getTransactionID(), txn.getTokenWipe().getToken(), factory));
		} else if (txn.hasTokenDeletion()) {
			return Optional.of(tokenMutates(txn.getTransactionID(), txn.getTokenDeletion().getToken(), factory));
		} else if (txn.hasTokenUpdate()) {
			return Optional.of(tokenUpdates(txn.getTransactionID(), txn.getTokenUpdate(), factory));
		} else {
			return Optional.empty();
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

	private <T> Optional<SigningOrderResult<T>> forConsensus(
			TransactionBody txn,
			SigningOrderResultFactory<T> factory
	) {
		if (txn.hasConsensusCreateTopic()) {
			return Optional.of(topicCreate(
					txn.getTransactionID(), txn.getConsensusCreateTopic(), factory));
		} else if (txn.hasConsensusSubmitMessage()) {
			return Optional.of(messageSubmit(
					txn.getTransactionID(), txn.getConsensusSubmitMessage(), factory));
		} else if (txn.hasConsensusUpdateTopic()) {
			return Optional.of(topicUpdate(
					txn.getTransactionID(), txn.getConsensusUpdateTopic(), factory));
		} else if (txn.hasConsensusDeleteTopic()) {
			return Optional.of(topicDelete(
					txn.getTransactionID(), txn.getConsensusDeleteTopic(), factory));
		} else {
			return Optional.empty();
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

	private <T> SigningOrderResult<T> cryptoDelete(
			TransactionID txnId,
			CryptoDeleteTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = EMPTY_LIST;

		var target = op.getDeleteAccountID();
		var targetResult = sigMetaLookup.accountSigningMetaFor(target);
		if (!targetResult.succeeded()) {
			return accountFailure(target, txnId, targetResult.failureIfAny(), factory);
		}
		required = mutable(required);
		required.add(targetResult.metadata().getKey());

		var beneficiary = op.getTransferAccountID();
		var beneficiaryResult = sigMetaLookup.accountSigningMetaFor(beneficiary);
		if (!beneficiaryResult.succeeded()) {
			return accountFailure(beneficiary, txnId, beneficiaryResult.failureIfAny(), factory);
		} else if (beneficiaryResult.metadata().isReceiverSigRequired()) {
			required.add(beneficiaryResult.metadata().getKey());
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> cryptoUpdate(
			TransactionID txnId,
			boolean targetMustSign,
			CryptoUpdateTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = EMPTY_LIST;

		var target = op.getAccountIDToUpdate();
		var result = sigMetaLookup.accountSigningMetaFor(target);
		if (!result.succeeded()) {
			return accountFailure(target, txnId, result.failureIfAny(), factory);
		} else if (targetMustSign) {
			required = mutable(required);
			required.add(result.metadata().getKey());
		}

		if (op.hasKey()) {
			required = mutable(required);
			var candidate = asUsableFcKey(op.getKey());
			candidate.ifPresent(required::add);
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> cryptoTransfer(
			TransactionID txnId,
			CryptoTransferTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = EMPTY_LIST;
		for (AccountAmount adjustment : op.getTransfers().getAccountAmountsList()) {
			var account = adjustment.getAccountID();
			var result = sigMetaLookup.accountSigningMetaFor(account);
			if (result.succeeded()) {
				if (adjustment.getAmount() < 0L || result.metadata().isReceiverSigRequired()) {
					required = mutable(required);
					required.add(result.metadata().getKey());
				}
			} else {
				return accountFailure(account, txnId, result.failureIfAny(), factory);
			}
		}
		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> accountFailure(
			AccountID id,
			TransactionID txnId,
			KeyOrderingFailure type,
			SigningOrderResultFactory<T> factory
	) {
		if (type == MISSING_ACCOUNT) {
			return factory.forMissingAccount(id, txnId);
		} else if (type == MISSING_AUTORENEW_ACCOUNT) {
			return factory.forMissingAutoRenewAccount(id, txnId);
		} else {
			return factory.forGeneralError(txnId);
		}
	}

	private <T> SigningOrderResult<T> topicFailure(
			TopicID id,
			TransactionID txnId,
			KeyOrderingFailure type,
			SigningOrderResultFactory<T> factory
	) {
		if (type == INVALID_TOPIC) {
			return factory.forMissingTopic(id, txnId);
		} else {
			return factory.forGeneralError(txnId);
		}
	}

	private List<JKey> mutable(List<JKey> required) {
		return (required == EMPTY_LIST) ? new ArrayList<>() : required;
	}

	private <T> SigningOrderResult<T> cryptoCreate(
			CryptoCreateTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		if (!op.getReceiverSigRequired()) {
			return SigningOrderResult.noKnownKeys();
		} else {
			var candidate = asUsableFcKey(op.getKey());
			return candidate.isPresent()
					? factory.forValidOrder(List.of(candidate.get()))
					: SigningOrderResult.noKnownKeys();
		}
	}

	private <T> SigningOrderResult<T> topicCreate(
			TransactionID txnId,
			ConsensusCreateTopicTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = new ArrayList<>();

		addToMutableReqIfPresent(
				op,
				ConsensusCreateTopicTransactionBody::hasAdminKey,
				ConsensusCreateTopicTransactionBody::getAdminKey,
				required);
		if (!addAutoRenew(
				op,
				ConsensusCreateTopicTransactionBody::hasAutoRenewAccount,
				ConsensusCreateTopicTransactionBody::getAutoRenewAccount,
				required)) {
			return accountFailure(op.getAutoRenewAccount(), txnId, MISSING_AUTORENEW_ACCOUNT, factory);
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> tokenCreate(
			TransactionID txnId,
			TokenCreateTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = new ArrayList<>();

		if (!addAutoRenew(op, TokenCreateTransactionBody::hasAutoRenewAccount, TokenCreateTransactionBody::getAutoRenewAccount, required)) {
			return accountFailure(op.getAutoRenewAccount(), txnId, MISSING_AUTORENEW_ACCOUNT, factory);
		}
		addToMutableReqIfPresent(op, TokenCreateTransactionBody::hasAdminKey, TokenCreateTransactionBody::getAdminKey, required);

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> tokenFreezing(
			TransactionID txnId,
			TokenRef ref,
			SigningOrderResultFactory<T> factory
	) {
		return tokenAdjusts(txnId, ref, factory, TokenSigningMetadata::optionalFreezeKey);
	}

	private <T> SigningOrderResult<T> tokenKnowing(
			TransactionID txnId,
			TokenRef ref,
			SigningOrderResultFactory<T> factory
	) {
		return tokenAdjusts(txnId, ref, factory, TokenSigningMetadata::optionalKycKey);
	}

	private <T> SigningOrderResult<T> tokenRefloating(
			TransactionID txnId,
			TokenRef ref,
			SigningOrderResultFactory<T> factory
	) {
		return tokenAdjusts(txnId, ref, factory, TokenSigningMetadata::optionalSupplyKey);
	}

	private <T> SigningOrderResult<T> tokenWiping(
			TransactionID txnId,
			TokenRef ref,
			SigningOrderResultFactory<T> factory
	) {
		return tokenAdjusts(txnId, ref, factory, TokenSigningMetadata::optionalWipeKey);
	}

	private <T> SigningOrderResult<T> tokenUpdates(
			TransactionID txnId,
			TokenManagement op,
			SigningOrderResultFactory<T> factory
	) {
		List<Function<TokenSigningMetadata, Optional<JKey>>> nonAdminReqs = Collections.emptyList();
		var basic = tokenMutates(txnId, op.getToken(), factory, nonAdminReqs);
		var required = basic.getOrderedKeys();
		if (!addAutoRenew(
				op,
				TokenManagement::hasAutoRenewAccount,
				TokenManagement::getAutoRenewAccount,
				required)) {
			return accountFailure(op.getAutoRenewAccount(), txnId, MISSING_AUTORENEW_ACCOUNT, factory);
		}
		addToMutableReqIfPresent(op, TokenManagement::hasAdminKey, TokenManagement::getAdminKey, required);
		return basic;
	}

	private <T> boolean addAutoRenew(T op, Predicate<T> isPresent, Function<T, AccountID> getter, List<JKey> reqs) {
		if (isPresent.test(op)) {
			var result = sigMetaLookup.accountSigningMetaFor(getter.apply(op));
			if (result.succeeded()) {
				reqs.add(result.metadata().getKey());
			} else {
				return false;
			}
		}
		return true;
	}

	private <T> SigningOrderResult<T> tokenMutates(
			TransactionID txnId,
			TokenRef ref,
			SigningOrderResultFactory<T> factory
	) {
		return tokenMutates(txnId, ref, factory, Collections.emptyList());
	}

	private <T> SigningOrderResult<T> tokenMutates(
			TransactionID txnId,
			TokenRef ref,
			SigningOrderResultFactory<T> factory,
			List<Function<TokenSigningMetadata, Optional<JKey>>> optionalKeyLookups
	) {
		List<JKey> required = new ArrayList<>();

		var result = sigMetaLookup.tokenSigningMetaFor(ref);
		if (result.succeeded()) {
			var meta = result.metadata();
			if (meta.adminKey().isPresent()) {
				required.add(meta.adminKey().get());
			}
			optionalKeyLookups.forEach(lookup -> {
				var candidate = lookup.apply(meta);
				candidate.ifPresent(required::add);
			});
		} else {
			return factory.forMissingToken(ref, txnId);
		}
		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> tokenAdjusts(
			TransactionID txnId,
			TokenRef ref,
			SigningOrderResultFactory<T> factory,
			Function<TokenSigningMetadata, Optional<JKey>> optionalKeyLookup
	) {
		List<JKey> required = EMPTY_LIST;

		var result = sigMetaLookup.tokenSigningMetaFor(ref);
		if (result.succeeded()) {
			var optionalKey = optionalKeyLookup.apply(result.metadata());
			if (optionalKey.isPresent()) {
				required = mutable(required);
				required.add(optionalKey.get());
			} else {
				return SigningOrderResult.noKnownKeys();
			}
		} else {
			return factory.forMissingToken(ref, txnId);
		}
		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> tokenTransact(
			TransactionID txnId,
			TokenTransfers op,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = EMPTY_LIST;

		for (TokenRefTransferList xfers : op.getTokenTransfersList()) {
			for (AccountAmount adjust : xfers.getTransfersList()) {
				if (adjust.getAmount() < 0) {
					var account = adjust.getAccountID();
					var result = sigMetaLookup.accountSigningMetaFor(account);
					if (result.succeeded()) {
						required = mutable(required);
						required.add(result.metadata().getKey());
					} else {
						return factory.forMissingAccount(account, txnId);
					}
				}
			}
		}

		return factory.forValidOrder(required);
	}

	private <T> void addToMutableReqIfPresent(
			T op,
			Predicate<T> checker,
			Function<T, Key> getter,
			List<JKey> required
	) {
		if (checker.test(op)) {
			var candidate = asUsableFcKey(getter.apply(op));
			candidate.ifPresent(required::add);
		}
	}

	private <T> SigningOrderResult<T> messageSubmit(
			TransactionID txnId,
			ConsensusSubmitMessageTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = EMPTY_LIST;
		var target = op.getTopicID();
		var result = sigMetaLookup.topicSigningMetaFor(target);
		if (!result.succeeded()) {
			return topicFailure(target, txnId, result.failureIfAny(), factory);
		}
		if (result.metadata().hasSubmitKey()) {
			required = mutable(required);
			required.add(result.metadata().getSubmitKey());
		}
		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> topicUpdate(
			TransactionID txnId,
			ConsensusUpdateTopicTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = EMPTY_LIST;
		if (onlyExtendsExpiry(op)) {
			return factory.forValidOrder(required);
		}

		var target = op.getTopicID();
		var targetResult = sigMetaLookup.topicSigningMetaFor(target);
		if (!targetResult.succeeded()) {
			return topicFailure(target, txnId, targetResult.failureIfAny(), factory);
		}
		var meta = targetResult.metadata();
		if (meta.hasAdminKey()) {
			required = mutable(required);
			required.add(meta.getAdminKey());
		}

		if (op.hasAdminKey()) {
			required = mutable(required);
			var candidate = asUsableFcKey(op.getAdminKey());
			candidate.ifPresent(required::add);
		}
		if (op.hasAutoRenewAccount() && !isEliding(op.getAutoRenewAccount())) {
			var account = op.getAutoRenewAccount();
			var autoRenewResult = sigMetaLookup.accountSigningMetaFor(account);
			if (autoRenewResult.succeeded()) {
				required = mutable(required);
				required.add(autoRenewResult.metadata().getKey());
			} else {
				return accountFailure(account, txnId, MISSING_AUTORENEW_ACCOUNT, factory);
			}
		}

		return factory.forValidOrder(required);
	}

	private boolean isEliding(AccountID id) {
		return id.getShardNum() == 0 && id.getRealmNum() == 0 && id.getAccountNum() == 0;
	}

	private boolean onlyExtendsExpiry(ConsensusUpdateTopicTransactionBody op) {
		return op.hasExpirationTime() &&
				!op.hasMemo() &&
				!op.hasAdminKey() &&
				!op.hasSubmitKey() &&
				!op.hasAutoRenewPeriod() &&
				!op.hasAutoRenewAccount();
	}

	private <T> SigningOrderResult<T> topicDelete(
			TransactionID txnId,
			ConsensusDeleteTopicTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = EMPTY_LIST;

		var target = op.getTopicID();
		var targetResult = sigMetaLookup.topicSigningMetaFor(target);
		if (!targetResult.succeeded()) {
			return topicFailure(target, txnId, targetResult.failureIfAny(), factory);
		} else if (targetResult.metadata().hasAdminKey()) {
			required = mutable(required);
			required.add(targetResult.metadata().getAdminKey());
		}
		return factory.forValidOrder(required);
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
