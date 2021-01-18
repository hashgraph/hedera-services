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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.config.EntityNumbers;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.sigs.metadata.TokenSigningMetadata;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.sigs.order.KeyOrderingFailure.IMMUTABLE_CONTRACT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_CONTRACT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_TOPIC;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_AUTORENEW_ACCOUNT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.NONE;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static java.util.Collections.EMPTY_LIST;

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

		var scheduleOrder = forSchedule(txn, factory);
		if (scheduleOrder.isPresent()) {
			return scheduleOrder.get();
		}

		var fileOrder = forFile(txn, factory);
		if (fileOrder.isPresent()) {
			return fileOrder.get();
		}

		var contractOrder = forContract(txn, factory);
		if (contractOrder.isPresent()) {
			return contractOrder.get();
		}

		return SigningOrderResult.noKnownKeys();
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

	private <T> Optional<SigningOrderResult<T>> forContract(
			TransactionBody txn,
			SigningOrderResultFactory<T> factory
	) {
		if (txn.hasContractCreateInstance()) {
			return Optional.of(contractCreate(
					txn.getContractCreateInstance(), factory));
		} else if (txn.hasContractUpdateInstance()) {
			return Optional.of(contractUpdate(
					txn.getTransactionID(), txn.getContractUpdateInstance(), factory));
		} else if (txn.hasContractDeleteInstance()) {
			return Optional.of(contractDelete(
					txn.getTransactionID(), txn.getContractDeleteInstance(), factory));
		} else {
			return Optional.empty();
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

	private <T> Optional<SigningOrderResult<T>> forSchedule(
			TransactionBody txn,
			SigningOrderResultFactory<T> factory
	) {
		if (txn.hasScheduleCreate()) {
			return Optional.of(scheduleCreate(txn.getTransactionID(), txn.getScheduleCreate(), factory));
		} else if (txn.hasScheduleSign()) {
			return Optional.of(scheduleSign(txn.getTransactionID(), txn.getScheduleSign().getScheduleID(), factory));
		} else if (txn.hasScheduleDelete()) {
			return Optional.of(scheduleDelete(txn.getTransactionID(), txn.getScheduleDelete().getScheduleID(), factory));
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
		} else if (txn.hasTokenAssociate()) {
			return Optional.of(tokenAssociate(txn.getTransactionID(), txn.getTokenAssociate(), factory));
		} else if (txn.hasTokenDissociate()) {
			return Optional.of(tokenDissociate(txn.getTransactionID(), txn.getTokenDissociate(), factory));
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

	private <T> Optional<SigningOrderResult<T>> forFile(
			TransactionBody txn,
			SigningOrderResultFactory<T> factory
	) {
		if (txn.hasFileCreate()) {
			return Optional.of(fileCreate(txn.getFileCreate(), factory));
		} else if (txn.hasFileAppend() || txn.hasFileUpdate()) {
			var isSuperuser = entityNums.accounts().isSuperuser(txn.getTransactionID().getAccountID().getAccountNum());
			if (txn.hasFileAppend()) {
				var waclShouldSign = targetWaclSigns.test(txn, HederaFunctionality.FileAppend);
				return Optional.of(fileAppend(
						txn.getTransactionID(), txn.getFileAppend(), waclShouldSign, isSuperuser, factory));
			} else {
				var waclShouldSign = targetWaclSigns.test(txn, HederaFunctionality.FileUpdate);
				return Optional.of(fileUpdate(
						txn.getTransactionID(), txn.getFileUpdate(), waclShouldSign, isSuperuser, factory));
			}
		} else if (txn.hasFileDelete()) {
			return Optional.of(fileDelete(txn.getTransactionID(), txn.getFileDelete(), factory));
		} else {
			return Optional.empty();
		}
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

	private <T> SigningOrderResult<T> contractDelete(
			TransactionID txnId,
			ContractDeleteTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = new ArrayList<>();

		var target = op.getContractID();
		var targetResult = sigMetaLookup.contractSigningMetaFor(target);
		if (!targetResult.succeeded()) {
			return contractFailure(target, txnId, targetResult.failureIfAny(), factory);
		}
		required.add(targetResult.metadata().getKey());

		if (op.hasTransferAccountID()) {
			var beneficiary = op.getTransferAccountID();
			var beneficiaryResult = sigMetaLookup.accountSigningMetaFor(beneficiary);
			if (!beneficiaryResult.succeeded()) {
				return factory.forInvalidAccount(beneficiary, txnId);
			} else if (beneficiaryResult.metadata().isReceiverSigRequired()) {
				required.add(beneficiaryResult.metadata().getKey());
			}
		} else if (op.hasTransferContractID()) {
			var beneficiary = op.getTransferContractID();
			var beneficiaryResult = sigMetaLookup.contractSigningMetaFor(beneficiary);
			if (!beneficiaryResult.succeeded()) {
				return factory.forInvalidContract(beneficiary, txnId);
			} else if (beneficiaryResult.metadata().isReceiverSigRequired()) {
				required.add(beneficiaryResult.metadata().getKey());
			}
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> contractUpdate(
			TransactionID txnId,
			ContractUpdateTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = new ArrayList<>();

		var target = op.getContractID();
		var result = sigMetaLookup.contractSigningMetaFor(target);
		if (needsCurrentAdminSig(op)) {
			if (!result.succeeded()) {
				return contractFailure(target, txnId, result.failureIfAny(), factory);
			}
			required.add(result.metadata().getKey());
		}
		if (hasNondeprecatedAdminKey(op)) {
			var candidate = asUsableFcKey(op.getAdminKey());
			candidate.ifPresent(required::add);
		}

		return factory.forValidOrder(required);
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

	private <T> SigningOrderResult<T> contractCreate(
			ContractCreateTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		var key = op.getAdminKey();
		if (key.hasContractID()) {
			return SigningOrderResult.noKnownKeys();
		}
		var candidate = asUsableFcKey(key);
		return candidate.isPresent()
				? factory.forValidOrder(List.of(candidate.get()))
				: SigningOrderResult.noKnownKeys();
	}

	private <T> SigningOrderResult<T> fileDelete(
			TransactionID txnId,
			FileDeleteTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		var target = op.getFileID();
		var targetResult = sigMetaLookup.fileSigningMetaFor(target);
		if (!targetResult.succeeded()) {
			return factory.forMissingFile(target, txnId);
		} else {
			var wacl = targetResult.metadata().getWacl();
			return wacl.isEmpty() ? SigningOrderResult.noKnownKeys() : factory.forValidOrder(List.of(wacl));
		}
	}

	private <T> SigningOrderResult<T> fileUpdate(
			TransactionID txnId,
			FileUpdateTransactionBody op,
			boolean waclShouldSign,
			boolean payerIsSuperuser,
			SigningOrderResultFactory<T> factory
	) {
		var target = op.getFileID();
		if (payerIsSuperuser && entityNums.isSystemFile(target)) {
			return SigningOrderResult.noKnownKeys();
		} else {
			var targetResult = sigMetaLookup.fileSigningMetaFor(target);
			if (!targetResult.succeeded()) {
				return factory.forMissingFile(target, txnId);
			} else {
				List<JKey> required = new ArrayList<>();
				if (waclShouldSign) {
					var wacl = targetResult.metadata().getWacl();
					if (!wacl.isEmpty()) {
						required.add(wacl);
					}
					if (op.hasKeys()) {
						var candidate = asUsableFcKey(Key.newBuilder().setKeyList(op.getKeys()).build());
						candidate.ifPresent(required::add);
					}
				}
				return factory.forValidOrder(required);
			}
		}
	}

	private <T> SigningOrderResult<T> fileAppend(
			TransactionID txnId,
			FileAppendTransactionBody op,
			boolean waclShouldSign,
			boolean payerIsSuperuser,
			SigningOrderResultFactory<T> factory
	) {
		var target = op.getFileID();
		if (payerIsSuperuser && entityNums.isSystemFile(target)) {
			return SigningOrderResult.noKnownKeys();
		} else {
			var targetResult = sigMetaLookup.fileSigningMetaFor(target);
			if (!targetResult.succeeded()) {
				return factory.forMissingFile(target, txnId);
			} else {
				if (!waclShouldSign) {
					return SigningOrderResult.noKnownKeys();
				} else {
					var wacl = targetResult.metadata().getWacl();
					return wacl.isEmpty() ? SigningOrderResult.noKnownKeys() : factory.forValidOrder(List.of(wacl));
				}
			}
		}
	}

	private <T> SigningOrderResult<T> fileCreate(
			FileCreateTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		var candidate = asUsableFcKey(Key.newBuilder().setKeyList(op.getKeys()).build());
		return candidate.isPresent()
				? factory.forValidOrder(List.of(candidate.get()))
				: SigningOrderResult.noKnownKeys();
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
		List<JKey> required = new ArrayList<>();

		KeyOrderingFailure failure;
		for (TokenTransferList xfers : op.getTokenTransfersList()) {
			for (AccountAmount adjust : xfers.getTransfersList()) {
				if ((failure = includeIfPresentAndNecessary(adjust, required)) != NONE) {
					return accountFailure(adjust.getAccountID(), txnId, failure, factory);
				}
			}
		}
		for (AccountAmount adjust : op.getTransfers().getAccountAmountsList()) {
			if ((failure = includeIfPresentAndNecessary(adjust, required)) != NONE) {
				return accountFailure(adjust.getAccountID(), txnId, failure, factory);
			}
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> contractFailure(
			ContractID id,
			TransactionID txnId,
			KeyOrderingFailure type,
			SigningOrderResultFactory<T> factory
	) {
		if (type == INVALID_CONTRACT) {
			return factory.forInvalidContract(id, txnId);
		} else if (type == IMMUTABLE_CONTRACT) {
			return factory.forImmutableContract(id, txnId);
		} else {
			return factory.forGeneralError(txnId);
		}
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
		if (!addAccount(
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

		var couldAddTreasury = addAccount(
				op,
				TokenCreateTransactionBody::hasTreasury,
				TokenCreateTransactionBody::getTreasury,
				required);
		if (!couldAddTreasury) {
			return accountFailure(op.getTreasury(), txnId, MISSING_ACCOUNT, factory);
		}
		var couldAddAutoRenew = addAccount(
				op,
				TokenCreateTransactionBody::hasAutoRenewAccount,
				TokenCreateTransactionBody::getAutoRenewAccount,
				required);
		if (!couldAddAutoRenew) {
			return accountFailure(op.getAutoRenewAccount(), txnId, MISSING_AUTORENEW_ACCOUNT, factory);
		}
		addToMutableReqIfPresent(
				op,
				TokenCreateTransactionBody::hasAdminKey,
				TokenCreateTransactionBody::getAdminKey,
				required);

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> tokenFreezing(
			TransactionID txnId,
			TokenID id,
			SigningOrderResultFactory<T> factory
	) {
		return tokenAdjusts(txnId, id, factory, TokenSigningMetadata::optionalFreezeKey);
	}

	private <T> SigningOrderResult<T> tokenKnowing(
			TransactionID txnId,
			TokenID id,
			SigningOrderResultFactory<T> factory
	) {
		return tokenAdjusts(txnId, id, factory, TokenSigningMetadata::optionalKycKey);
	}

	private <T> SigningOrderResult<T> tokenRefloating(
			TransactionID txnId,
			TokenID id,
			SigningOrderResultFactory<T> factory
	) {
		return tokenAdjusts(txnId, id, factory, TokenSigningMetadata::optionalSupplyKey);
	}

	private <T> SigningOrderResult<T> tokenWiping(
			TransactionID txnId,
			TokenID id,
			SigningOrderResultFactory<T> factory
	) {
		return tokenAdjusts(txnId, id, factory, TokenSigningMetadata::optionalWipeKey);
	}

	private <T> SigningOrderResult<T> tokenUpdates(
			TransactionID txnId,
			TokenUpdateTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		List<Function<TokenSigningMetadata, Optional<JKey>>> nonAdminReqs = Collections.emptyList();
		var basic = tokenMutates(txnId, op.getToken(), factory, nonAdminReqs);
		var required = basic.getOrderedKeys();
		if (!addAccount(
				op,
				TokenUpdateTransactionBody::hasAutoRenewAccount,
				TokenUpdateTransactionBody::getAutoRenewAccount,
				required)) {
			return accountFailure(op.getAutoRenewAccount(), txnId, MISSING_AUTORENEW_ACCOUNT, factory);
		}
		if (!addAccount(
				op,
				TokenUpdateTransactionBody::hasTreasury,
				TokenUpdateTransactionBody::getTreasury,
				required)) {
			return accountFailure(op.getTreasury(), txnId, MISSING_ACCOUNT, factory);
		}
		addToMutableReqIfPresent(
				op,
				TokenUpdateTransactionBody::hasAdminKey,
				TokenUpdateTransactionBody::getAdminKey,
				required);
		return basic;
	}

	private <T> boolean addAccount(T op, Predicate<T> isPresent, Function<T, AccountID> getter, List<JKey> reqs) {
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
			TokenID id,
			SigningOrderResultFactory<T> factory
	) {
		return tokenMutates(txnId, id, factory, Collections.emptyList());
	}

	private <T> SigningOrderResult<T> tokenMutates(
			TransactionID txnId,
			TokenID id,
			SigningOrderResultFactory<T> factory,
			List<Function<TokenSigningMetadata, Optional<JKey>>> optionalKeyLookups
	) {
		List<JKey> required = new ArrayList<>();

		var result = sigMetaLookup.tokenSigningMetaFor(id);
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
			return factory.forMissingToken(id, txnId);
		}
		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> tokenAdjusts(
			TransactionID txnId,
			TokenID id,
			SigningOrderResultFactory<T> factory,
			Function<TokenSigningMetadata, Optional<JKey>> optionalKeyLookup
	) {
		List<JKey> required = EMPTY_LIST;

		var result = sigMetaLookup.tokenSigningMetaFor(id);
		if (result.succeeded()) {
			var optionalKey = optionalKeyLookup.apply(result.metadata());
			if (optionalKey.isPresent()) {
				required = mutable(required);
				required.add(optionalKey.get());
			} else {
				return SigningOrderResult.noKnownKeys();
			}
		} else {
			return factory.forMissingToken(id, txnId);
		}
		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> tokenAssociate(
			TransactionID txnId,
			TokenAssociateTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		return forSingleAccount(txnId, op.getAccount(), factory);
	}

	private <T> SigningOrderResult<T> tokenDissociate(
			TransactionID txnId,
			TokenDissociateTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		return forSingleAccount(txnId, op.getAccount(), factory);
	}

	private <T> SigningOrderResult<T> scheduleCreate(
			TransactionID txnId,
			ScheduleCreateTransactionBody op,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = new ArrayList<>();

		addToMutableReqIfPresent(
				op,
				ScheduleCreateTransactionBody::hasAdminKey,
				ScheduleCreateTransactionBody::getAdminKey,
				required);
		try {
			var scheduled = TransactionBody.parseFrom(op.getTransactionBody());
			if (scheduled.hasScheduleCreate()) {
				return factory.forNestedScheduleCreate(txnId);
			}
			var scheduledOrderResult = keysForOtherParties(scheduled, factory);
			if (scheduledOrderResult.hasErrorReport()) {
				return factory.forUnresolvableRequiredSigners(
						scheduled,
						txnId,
						scheduledOrderResult.getErrorReport());
			} else {
				var scheduledKeys = scheduledOrderResult.getOrderedKeys();
				for (JKey key : scheduledKeys) {
					var dup = key.duplicate();
					dup.setForScheduledTxn(true);
					required.add(dup);
				}
			}
		} catch (InvalidProtocolBufferException e) {
			return factory.forUnparseableScheduledTxn(txnId);
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> scheduleSign(
			TransactionID txnId,
			ScheduleID id,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = new ArrayList<>();

		var result = sigMetaLookup.scheduleSigningMetaFor(id);
		if (!result.succeeded()) {
			return factory.forMissingSchedule(id, txnId);
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> scheduleDelete(
			TransactionID txnId,
			ScheduleID id,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = new ArrayList<>();

		var result = sigMetaLookup.scheduleSigningMetaFor(id);
		if (result.succeeded()) {
			var meta = result.metadata();
			if (meta.adminKey().isPresent()) {
				required.add(meta.adminKey().get());
			}
		} else {
			return factory.forMissingSchedule(id, txnId);
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> forSingleAccount(
			TransactionID txnId,
			AccountID target,
			SigningOrderResultFactory<T> factory
	) {
		List<JKey> required = EMPTY_LIST;

		var result = sigMetaLookup.accountSigningMetaFor(target);
		if (result.succeeded()) {
			var meta = result.metadata();
			required = mutable(required);
			required.add(meta.getKey());
		} else {
			return factory.forMissingAccount(target, txnId);
		}

		return factory.forValidOrder(required);
	}

	private KeyOrderingFailure includeIfPresentAndNecessary(AccountAmount adjust, List<JKey> required) {
		var account = adjust.getAccountID();
		var result = sigMetaLookup.accountSigningMetaFor(account);
		if (result.succeeded()) {
			var meta = result.metadata();
			if (adjust.getAmount() < 0 || meta.isReceiverSigRequired()) {
				required.add(meta.getKey());
			}
		}
		return result.failureIfAny();
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
}
