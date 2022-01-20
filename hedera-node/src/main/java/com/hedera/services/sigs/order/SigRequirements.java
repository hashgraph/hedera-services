package com.hedera.services.sigs.order;

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

import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.sigs.metadata.TokenSigningMetadata;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.sigs.order.KeyOrderingFailure.IMMUTABLE_CONTRACT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_ACCOUNT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_CONTRACT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_TOPIC;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_AUTORENEW_ACCOUNT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_TOKEN;
import static com.hedera.services.sigs.order.KeyOrderingFailure.NONE;
import static com.hedera.services.utils.EntityIdUtils.isAlias;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
import static java.util.Collections.EMPTY_LIST;

/**
 * Encapsulates all policies related to which Hedera keys must have active signatures for a given
 * gRPC transaction to be valid.
 *
 * Two strategy predicates are injected into this class, one with logic to decide if the WACL for a
 * file targeted by the gRPC transaction must have an active signature; and one with logic to make an
 * equivalent decision for a crypto account.
 */
public class SigRequirements {
	/* The current architecture does not support a triggered transaction itself triggering a transaction. So
	 * no matter what is in the scheduling.whitelist property, we have to abort any attempt to schedule these. */
	private static final Set<HederaFunctionality> IMPOSSIBLE_TO_SCHEDULE = EnumSet.of(ScheduleCreate, ScheduleSign);

	private final SignatureWaivers signatureWaivers;
	private final SigMetadataLookup sigMetaLookup;

	public SigRequirements(
			final SigMetadataLookup sigMetaLookup,
			final SignatureWaivers signatureWaivers
	) {
		this.sigMetaLookup = sigMetaLookup;
		this.signatureWaivers = signatureWaivers;
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
	public <T> SigningOrderResult<T> keysForPayer(
			final TransactionBody txn,
			final SigningOrderResultFactory<T> factory
	) {
		return keysForPayer(txn, factory, null);
	}

	/**
	 * Uses the provided factory to summarize an attempt to compute the canonical signing order
	 * of the Hedera key(s) that must be active for the payer of the given gRPC transaction.
	 *
	 * @param txn
	 * 		the gRPC transaction of interest.
	 * @param factory
	 * 		the result factory to use to summarize the listing attempt.
	 * @param linkedRefs
	 * 		if non-null, the accumulator to use to record all entities referenced during the attempt
	 * @param <T>
	 * 		the type of error report created by the factory.
	 * @return a {@link SigningOrderResult} summarizing the listing attempt.
	 */
	public <T> SigningOrderResult<T> keysForPayer(
			final TransactionBody txn,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		if (linkedRefs != null) {
			linkedRefs.setSourceSignedAt(sigMetaLookup.sourceSignedAt());
		}
		return orderForPayer(txn, factory, linkedRefs);
	}

	public <T> SigningOrderResult<T> keysForOtherParties(
			final TransactionBody txn,
			final SigningOrderResultFactory<T> factory
	) {
		return keysForOtherParties(txn, factory, null);
	}

	/**
	 * Uses the provided factory to summarize an attempt to compute the canonical signing order
	 * of the Hedera key(s) that must be active for any Hedera entities involved in a non-payer
	 * role in the given gRPC transaction. (Which could also include the payer crypto account.)
	 *
	 * @param txn
	 * 		the gRPC transaction of interest
	 * @param factory
	 * 		the result factory to use to summarize the listing attempt
	 * @param linkedRefs
	 * 		if non-null, the accumulator to use to record all entities referenced during the attempt
	 * @param <T>
	 * 		the type of error report created by the factory
	 * @return a {@link SigningOrderResult} summarizing the listing attempt
	 */
	public <T> SigningOrderResult<T> keysForOtherParties(
			final TransactionBody txn,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		final var cryptoOrder = forCrypto(txn, factory, linkedRefs);
		if (cryptoOrder != null) {
			return cryptoOrder;
		}
		final var consensusOrder = forConsensus(txn, factory, linkedRefs);
		if (consensusOrder != null) {
			return consensusOrder;
		}
		final var tokenOrder = forToken(txn, factory, linkedRefs);
		if (tokenOrder != null) {
			return tokenOrder;
		}
		final var scheduleOrder = forSchedule(txn, factory, linkedRefs);
		if (scheduleOrder != null) {
			return scheduleOrder;
		}
		var fileOrder = forFile(txn, factory, linkedRefs);
		if (fileOrder != null) {
			return fileOrder;
		}
		final var contractOrder = forContract(txn, factory, linkedRefs);
		if (contractOrder != null) {
			return contractOrder;
		}
		return SigningOrderResult.noKnownKeys();
	}

	private <T> SigningOrderResult<T> orderForPayer(
			final TransactionBody txn,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		final var payer = txn.getTransactionID().getAccountID();
		final var result = sigMetaLookup.accountSigningMetaFor(payer, linkedRefs);
		if (result.succeeded()) {
			return factory.forValidOrder(List.of(result.metadata().key()));
		} else {
			if (result.failureIfAny() == MISSING_ACCOUNT) {
				return factory.forInvalidAccount();
			} else {
				return factory.forGeneralPayerError();
			}
		}
	}

	private <T> SigningOrderResult<T> forContract(
			final TransactionBody txn,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		if (txn.hasContractCreateInstance()) {
			return contractCreate(txn.getContractCreateInstance(), factory);
		} else if (txn.hasContractUpdateInstance()) {
			return contractUpdate(txn.getContractUpdateInstance(), factory, linkedRefs);
		} else if (txn.hasContractDeleteInstance()) {
			return contractDelete(txn.getContractDeleteInstance(), factory, linkedRefs);
		} else {
			return null;
		}
	}

	private <T> SigningOrderResult<T> forCrypto(
			final TransactionBody txn,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		final var payer = txn.getTransactionID().getAccountID();
		if (txn.hasCryptoCreateAccount()) {
			return cryptoCreate(txn.getCryptoCreateAccount(), factory);
		} else if (txn.hasCryptoTransfer()) {
			return cryptoTransfer(payer, txn.getCryptoTransfer(), factory, linkedRefs);
		} else if (txn.hasCryptoUpdateAccount()) {
			return cryptoUpdate(payer, txn, factory, linkedRefs);
		} else if (txn.hasCryptoDelete()) {
			return cryptoDelete(payer, txn.getCryptoDelete(), factory, linkedRefs);
		} else {
			return null;
		}
	}

	private <T> SigningOrderResult<T> forSchedule(
			final TransactionBody txn,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		final var payer = txn.getTransactionID().getAccountID();
		if (txn.hasScheduleCreate()) {
			return scheduleCreate(payer, txn.getScheduleCreate(), factory, linkedRefs);
		} else if (txn.hasScheduleSign()) {
			return scheduleSign(txn.getScheduleSign().getScheduleID(), factory, linkedRefs);
		} else if (txn.hasScheduleDelete()) {
			return scheduleDelete(txn.getScheduleDelete().getScheduleID(), factory, linkedRefs);
		} else {
			return null;
		}
	}

	private <T> SigningOrderResult<T> forToken(
			final TransactionBody txn,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		final var payer = txn.getTransactionID().getAccountID();
		if (txn.hasTokenCreation()) {
			return tokenCreate(payer, txn.getTokenCreation(), factory, linkedRefs);
		} else if (txn.hasTokenAssociate()) {
			return tokenAssociate(payer, txn.getTokenAssociate(), factory, linkedRefs);
		} else if (txn.hasTokenDissociate()) {
			return tokenDissociate(payer, txn.getTokenDissociate(), factory, linkedRefs);
		} else if (txn.hasTokenFreeze()) {
			return tokenFreezing(txn.getTokenFreeze().getToken(), factory, linkedRefs);
		} else if (txn.hasTokenUnfreeze()) {
			return tokenFreezing(txn.getTokenUnfreeze().getToken(), factory, linkedRefs);
		} else if (txn.hasTokenGrantKyc()) {
			return tokenKnowing(txn.getTokenGrantKyc().getToken(), factory, linkedRefs);
		} else if (txn.hasTokenRevokeKyc()) {
			return tokenKnowing(txn.getTokenRevokeKyc().getToken(), factory, linkedRefs);
		} else if (txn.hasTokenMint()) {
			return tokenRefloating(txn.getTokenMint().getToken(), factory, linkedRefs);
		} else if (txn.hasTokenBurn()) {
			return tokenRefloating(txn.getTokenBurn().getToken(), factory, linkedRefs);
		} else if (txn.hasTokenWipe()) {
			return tokenWiping(txn.getTokenWipe().getToken(), factory, linkedRefs);
		} else if (txn.hasTokenDeletion()) {
			return tokenMutates(txn.getTokenDeletion().getToken(), factory, linkedRefs);
		} else if (txn.hasTokenUpdate()) {
			return tokenUpdates(payer, txn.getTokenUpdate(), factory, linkedRefs);
		} else if (txn.hasTokenFeeScheduleUpdate()) {
			return tokenFeeScheduleUpdates(payer, txn.getTokenFeeScheduleUpdate(), factory, linkedRefs);
		} else if (txn.hasTokenPause()) {
			return tokenPausing(txn.getTokenPause().getToken(), factory, linkedRefs);
		} else if (txn.hasTokenUnpause()) {
			return tokenPausing(txn.getTokenUnpause().getToken(), factory, linkedRefs);
		} else {
			return null;
		}
	}

	private <T> SigningOrderResult<T> forFile(
			final TransactionBody txn,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		if (txn.hasFileCreate()) {
			return fileCreate(txn.getFileCreate(), factory);
		} else if (txn.hasFileAppend()) {
			return fileAppend(txn, factory, linkedRefs);
		} else if (txn.hasFileUpdate()) {
			return fileUpdate(txn, factory, linkedRefs);
		} else if (txn.hasFileDelete()) {
			return fileDelete(txn.getFileDelete(), factory, linkedRefs);
		} else {
			return null;
		}
	}

	private <T> SigningOrderResult<T> forConsensus(
			final TransactionBody txn,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		final var payer = txn.getTransactionID().getAccountID();
		if (txn.hasConsensusCreateTopic()) {
			return topicCreate(payer, txn.getConsensusCreateTopic(), factory, linkedRefs);
		} else if (txn.hasConsensusSubmitMessage()) {
			return messageSubmit(txn.getConsensusSubmitMessage(), factory, linkedRefs);
		} else if (txn.hasConsensusUpdateTopic()) {
			return topicUpdate(payer, txn.getConsensusUpdateTopic(), factory, linkedRefs);
		} else if (txn.hasConsensusDeleteTopic()) {
			return topicDelete(txn.getConsensusDeleteTopic(), factory, linkedRefs);
		} else {
			return null;
		}
	}

	private <T> SigningOrderResult<T> contractDelete(
			final ContractDeleteTransactionBody op,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		List<JKey> required = EMPTY_LIST;

		var target = op.getContractID();
		var targetResult = sigMetaLookup.contractSigningMetaFor(target, linkedRefs);
		if (!targetResult.succeeded()) {
			return contractFailure(targetResult.failureIfAny(), factory);
		}
		required = mutable(required);
		required.add(targetResult.metadata().key());

		if (op.hasTransferAccountID()) {
			var beneficiary = op.getTransferAccountID();
			var beneficiaryResult = sigMetaLookup.accountSigningMetaFor(beneficiary, linkedRefs);
			if (!beneficiaryResult.succeeded()) {
				return factory.forInvalidAccount();
			} else if (beneficiaryResult.metadata().receiverSigRequired()) {
				required.add(beneficiaryResult.metadata().key());
			}
		} else if (op.hasTransferContractID()) {
			var beneficiary = op.getTransferContractID();
			var beneficiaryResult = sigMetaLookup.contractSigningMetaFor(beneficiary, linkedRefs);
			if (!beneficiaryResult.succeeded()) {
				return factory.forInvalidContract();
			} else if (beneficiaryResult.metadata().receiverSigRequired()) {
				required.add(beneficiaryResult.metadata().key());
			}
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> contractUpdate(
			final ContractUpdateTransactionBody op,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		List<JKey> required = EMPTY_LIST;

		var target = op.getContractID();
		var result = sigMetaLookup.contractSigningMetaFor(target, linkedRefs);
		if (needsCurrentAdminSig(op)) {
			if (!result.succeeded()) {
				return contractFailure(result.failureIfAny(), factory);
			}
			required = mutable(required);
			required.add(result.metadata().key());
		}
		if (hasNondeprecatedAdminKey(op)) {
			var candidate = asUsableFcKey(op.getAdminKey());
			required = mutable(required);
			candidate.ifPresent(required::add);
		}

		return factory.forValidOrder(required);
	}

	private boolean needsCurrentAdminSig(final ContractUpdateTransactionBody op) {
		return !op.hasExpirationTime()
				|| hasNondeprecatedAdminKey(op)
				|| op.hasProxyAccountID()
				|| op.hasAutoRenewPeriod()
				|| op.hasFileID()
				|| op.getMemo().length() > 0;
	}

	private boolean hasNondeprecatedAdminKey(final ContractUpdateTransactionBody op) {
		return op.hasAdminKey() && !op.getAdminKey().hasContractID();
	}

	private <T> SigningOrderResult<T> contractCreate(
			final ContractCreateTransactionBody op,
			final SigningOrderResultFactory<T> factory
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
			final FileDeleteTransactionBody op,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		var target = op.getFileID();
		var targetResult = sigMetaLookup.fileSigningMetaFor(target, linkedRefs);
		if (!targetResult.succeeded()) {
			return factory.forMissingFile();
		} else {
			var wacl = targetResult.metadata().wacl();
			return wacl.isEmpty() ? SigningOrderResult.noKnownKeys() : factory.forValidOrder(List.of(wacl));
		}
	}

	private <T> SigningOrderResult<T> fileUpdate(
			final TransactionBody fileUpdateTxn,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		final var newWaclMustSign = !signatureWaivers.isNewFileWaclWaived(fileUpdateTxn);
		final var targetWaclMustSign = !signatureWaivers.isTargetFileWaclWaived(fileUpdateTxn);
		final var op = fileUpdateTxn.getFileUpdate();
		final var target = op.getFileID();
		final var targetResult = sigMetaLookup.fileSigningMetaFor(target, linkedRefs);
		if (!targetResult.succeeded()) {
			return factory.forMissingFile();
		} else {
			List<JKey> required = new ArrayList<>();
			if (targetWaclMustSign) {
				var wacl = targetResult.metadata().wacl();
				if (!wacl.isEmpty()) {
					required.add(wacl);
				}
			}
			if (newWaclMustSign && op.hasKeys()) {
				var candidate = asUsableFcKey(Key.newBuilder().setKeyList(op.getKeys()).build());
				candidate.ifPresent(required::add);
			}
			return factory.forValidOrder(required);
		}
	}

	private <T> SigningOrderResult<T> fileAppend(
			final TransactionBody fileAppendTxn,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		final var targetWaclMustSign = !signatureWaivers.isAppendFileWaclWaived(fileAppendTxn);
		final var op = fileAppendTxn.getFileAppend();
		var target = op.getFileID();
		var targetResult = sigMetaLookup.fileSigningMetaFor(target, linkedRefs);
		if (!targetResult.succeeded()) {
			return factory.forMissingFile();
		} else {
			if (targetWaclMustSign) {
				var wacl = targetResult.metadata().wacl();
				return wacl.isEmpty() ? SigningOrderResult.noKnownKeys() : factory.forValidOrder(List.of(wacl));
			} else {
				return SigningOrderResult.noKnownKeys();
			}
		}
	}

	private <T> SigningOrderResult<T> fileCreate(
			final FileCreateTransactionBody op,
			final SigningOrderResultFactory<T> factory
	) {
		var candidate = asUsableFcKey(Key.newBuilder().setKeyList(op.getKeys()).build());
		return candidate.isPresent()
				? factory.forValidOrder(List.of(candidate.get()))
				: SigningOrderResult.noKnownKeys();
	}

	private <T> SigningOrderResult<T> cryptoDelete(
			final AccountID payer,
			final CryptoDeleteTransactionBody op,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		List<JKey> required = EMPTY_LIST;

		var target = op.getDeleteAccountID();
		if (!payer.equals(target)) {
			var targetResult = sigMetaLookup.accountSigningMetaFor(target, linkedRefs);
			if (!targetResult.succeeded()) {
				return accountFailure(targetResult.failureIfAny(), factory);
			}
			required = mutable(required);
			required.add(targetResult.metadata().key());
		}

		var beneficiary = op.getTransferAccountID();
		if (!payer.equals(beneficiary)) {
			var beneficiaryResult = sigMetaLookup.accountSigningMetaFor(beneficiary, linkedRefs);
			if (!beneficiaryResult.succeeded()) {
				return accountFailure(beneficiaryResult.failureIfAny(), factory);
			} else if (beneficiaryResult.metadata().receiverSigRequired()) {
				required = mutable(required);
				required.add(beneficiaryResult.metadata().key());
			}
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> cryptoUpdate(
			final AccountID payer,
			final TransactionBody cryptoUpdateTxn,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		List<JKey> required = EMPTY_LIST;
		final var newAccountKeyMustSign = !signatureWaivers.isNewAccountKeyWaived(cryptoUpdateTxn);
		final var targetAccountKeyMustSign = !signatureWaivers.isTargetAccountKeyWaived(cryptoUpdateTxn);
		final var op = cryptoUpdateTxn.getCryptoUpdateAccount();
		var target = op.getAccountIDToUpdate();
		var result = sigMetaLookup.accountSigningMetaFor(target, linkedRefs);
		if (!result.succeeded()) {
			return accountFailure(result.failureIfAny(), factory);
		} else {
			if (targetAccountKeyMustSign && !payer.equals(target)) {
				required = mutable(required);
				required.add(result.metadata().key());
			}
			if (newAccountKeyMustSign && op.hasKey()) {
				required = mutable(required);
				var candidate = asUsableFcKey(op.getKey());
				candidate.ifPresent(required::add);
			}
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> cryptoTransfer(
			final AccountID payer,
			final CryptoTransferTransactionBody op,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		List<JKey> required = new ArrayList<>();

		KeyOrderingFailure failure;
		for (TokenTransferList xfers : op.getTokenTransfersList()) {
			for (AccountAmount adjust : xfers.getTransfersList()) {
				if ((failure = includeIfNecessary(payer, adjust, required, false, linkedRefs)) != NONE) {
					return accountFailure(failure, factory);
				}
			}
			final var token = xfers.getToken();
			for (NftTransfer adjust : xfers.getNftTransfersList()) {
				final var sender = adjust.getSenderAccountID();
				if ((failure = nftIncludeIfNecessary(payer, sender, null, required, token, op, linkedRefs))
						!= NONE) {
					return accountFailure(failure, factory);
				}
				final var receiver = adjust.getReceiverAccountID();
				if ((failure = nftIncludeIfNecessary(payer, receiver, sender, required, token, op, linkedRefs))
						!= NONE) {
					return (failure == MISSING_TOKEN) ? factory.forMissingToken() : accountFailure(failure, factory);
				}
			}
		}
		for (AccountAmount adjust : op.getTransfers().getAccountAmountsList()) {
			if ((failure = includeIfNecessary(payer, adjust, required, true, linkedRefs)) != NONE) {
				return accountFailure(failure, factory);
			}
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> contractFailure(KeyOrderingFailure type, SigningOrderResultFactory<T> factory) {
		if (type == INVALID_CONTRACT) {
			return factory.forInvalidContract();
		} else if (type == IMMUTABLE_CONTRACT) {
			return factory.forImmutableContract();
		} else {
			return factory.forGeneralError();
		}
	}

	private <T> SigningOrderResult<T> accountFailure(KeyOrderingFailure type, SigningOrderResultFactory<T> factory) {
		if (type == INVALID_ACCOUNT) {
			return factory.forInvalidAccount();
		} else if (type == MISSING_ACCOUNT) {
			return factory.forMissingAccount();
		} else if (type == MISSING_AUTORENEW_ACCOUNT) {
			return factory.forMissingAutoRenewAccount();
		} else {
			return factory.forGeneralError();
		}
	}

	private <T> SigningOrderResult<T> topicFailure(KeyOrderingFailure type, SigningOrderResultFactory<T> factory) {
		if (type == INVALID_TOPIC) {
			return factory.forMissingTopic();
		} else {
			return factory.forGeneralError();
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
			final AccountID payer,
			final ConsensusCreateTopicTransactionBody op,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		final List<JKey> required = new ArrayList<>();

		addToMutableReqIfPresent(
				op,
				ConsensusCreateTopicTransactionBody::hasAdminKey,
				ConsensusCreateTopicTransactionBody::getAdminKey,
				required);
		if (!addAccount(
				payer,
				op,
				ConsensusCreateTopicTransactionBody::hasAutoRenewAccount,
				ConsensusCreateTopicTransactionBody::getAutoRenewAccount,
				required,
				linkedRefs)) {
			return accountFailure(MISSING_AUTORENEW_ACCOUNT, factory);
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> tokenCreate(
			final AccountID payer,
			final TokenCreateTransactionBody op,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		final List<JKey> required = new ArrayList<>();

		final var couldAddTreasury = addAccount(
				payer,
				op,
				TokenCreateTransactionBody::hasTreasury,
				TokenCreateTransactionBody::getTreasury,
				required,
				linkedRefs);
		if (!couldAddTreasury) {
			return accountFailure(MISSING_ACCOUNT, factory);
		}
		final var couldAddAutoRenew = addAccount(
				payer,
				op,
				TokenCreateTransactionBody::hasAutoRenewAccount,
				TokenCreateTransactionBody::getAutoRenewAccount,
				required,
				linkedRefs);
		if (!couldAddAutoRenew) {
			return accountFailure(MISSING_AUTORENEW_ACCOUNT, factory);
		}
		addToMutableReqIfPresent(
				op,
				TokenCreateTransactionBody::hasAdminKey,
				TokenCreateTransactionBody::getAdminKey,
				required);
		for (var customFee : op.getCustomFeesList()) {
			final var collector = customFee.getFeeCollectorAccountId();
			/* A fractional fee collector and a collector for a fixed fee denominated
			in the units of the newly created token both must always sign a TokenCreate,
			since these are automatically associated to the newly created token. */
			boolean couldAddCollector;
			if (customFee.hasFixedFee()) {
				final var fixedFee = customFee.getFixedFee();
				final var alwaysAdd = fixedFee.hasDenominatingTokenId()
						&& fixedFee.getDenominatingTokenId().getTokenNum() == 0L;
				couldAddCollector = addAccount(payer, collector, required, alwaysAdd, linkedRefs);
			} else if (customFee.hasFractionalFee()) {
				couldAddCollector = addAccount(payer, collector, required, true, linkedRefs);
			} else {
				final var royaltyFee = customFee.getRoyaltyFee();
				var alwaysAdd = false;
				if (royaltyFee.hasFallbackFee()) {
					final var fFee = royaltyFee.getFallbackFee();
					alwaysAdd = fFee.hasDenominatingTokenId() && fFee.getDenominatingTokenId().getTokenNum() == 0;
				}
				couldAddCollector = addAccount(payer, collector, required, alwaysAdd, linkedRefs);
			}
			if (!couldAddCollector) {
				return factory.forMissingFeeCollector();
			}
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> tokenFreezing(
			final TokenID id,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		return tokenAdjusts(id, factory, TokenSigningMetadata::freezeKey, linkedRefs);
	}

	private <T> SigningOrderResult<T> tokenKnowing(
			final TokenID id,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		return tokenAdjusts(id, factory, TokenSigningMetadata::kycKey, linkedRefs);
	}

	private <T> SigningOrderResult<T> tokenRefloating(
			final TokenID id,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		return tokenAdjusts(id, factory, TokenSigningMetadata::supplyKey, linkedRefs);
	}

	private <T> SigningOrderResult<T> tokenWiping(
			final TokenID id,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		return tokenAdjusts(id, factory, TokenSigningMetadata::wipeKey, linkedRefs);
	}

	private <T> SigningOrderResult<T> tokenPausing(
			final TokenID id,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		return tokenAdjusts(id, factory, TokenSigningMetadata::pauseKey, linkedRefs);
	}

	private <T> SigningOrderResult<T> tokenFeeScheduleUpdates(
			final AccountID payer,
			final TokenFeeScheduleUpdateTransactionBody op,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		final var id = op.getTokenId();
		var result = sigMetaLookup.tokenSigningMetaFor(id, linkedRefs);
		if (result.succeeded()) {
			final var feeScheduleKey = result.metadata().feeScheduleKey();
			if (feeScheduleKey.isPresent()) {
				final List<JKey> required = new ArrayList<>();
				required.add(feeScheduleKey.get());
				for (var customFee : op.getCustomFeesList()) {
					final var collector = customFee.getFeeCollectorAccountId();
					final var couldAddCollector = addAccountIfReceiverSigRequired(payer, collector, required,
							linkedRefs);
					if (!couldAddCollector) {
						return factory.forMissingFeeCollector();
					}
				}
				return factory.forValidOrder(required);
			} else {
				/* We choose to fail with TOKEN_HAS_NO_FEE_SCHEDULE_KEY downstream in transition logic */
				return SigningOrderResult.noKnownKeys();
			}
		} else {
			return factory.forMissingToken();
		}
	}

	private <T> SigningOrderResult<T> tokenUpdates(
			final AccountID payer,
			final TokenUpdateTransactionBody op,
			final SigningOrderResultFactory<T> factory,
			final LinkedRefs linkedRefs
	) {
		List<Function<TokenSigningMetadata, Optional<JKey>>> nonAdminReqs = Collections.emptyList();
		final var basic = tokenMutates(op.getToken(), factory, nonAdminReqs, linkedRefs);
		if (basic.hasErrorReport()) {
			return basic;
		}

		var required = basic.getOrderedKeys();
		if (!addAccount(
				payer,
				op,
				TokenUpdateTransactionBody::hasAutoRenewAccount,
				TokenUpdateTransactionBody::getAutoRenewAccount,
				required,
				linkedRefs)) {
			return accountFailure(MISSING_AUTORENEW_ACCOUNT, factory);
		}
		if (!addAccount(
				payer,
				op,
				TokenUpdateTransactionBody::hasTreasury,
				TokenUpdateTransactionBody::getTreasury,
				required,
				linkedRefs)) {
			return accountFailure(MISSING_ACCOUNT, factory);
		}
		addToMutableReqIfPresent(
				op,
				TokenUpdateTransactionBody::hasAdminKey,
				TokenUpdateTransactionBody::getAdminKey,
				required);
		return basic;
	}

	private boolean addAccountIfReceiverSigRequired(
			final AccountID payer,
			final AccountID id,
			final List<JKey> reqs,
			final @Nullable LinkedRefs linkedRefs
	) {
		return addAccount(payer, id, reqs, false, linkedRefs);
	}

	private <T> boolean addAccount(
			final AccountID payer,
			final T op,
			final Predicate<T> isPresent,
			final Function<T, AccountID> getter,
			final List<JKey> reqs,
			final @Nullable LinkedRefs linkedRefs
	) {
		if (isPresent.test(op)) {
			return addAccount(payer, getter.apply(op), reqs, true, linkedRefs);
		}
		return true;
	}

	private boolean addAccount(
			final AccountID payer,
			final AccountID id,
			final List<JKey> reqs,
			final boolean alwaysAdd,
			final @Nullable LinkedRefs linkedRefs
	) {
		if (!payer.equals(id)) {
			var result = sigMetaLookup.accountSigningMetaFor(id, linkedRefs);
			if (result.succeeded()) {
				final var metadata = result.metadata();
				if (alwaysAdd || metadata.receiverSigRequired()) {
					reqs.add(metadata.key());
				}
			} else {
				return false;
			}
		}
		return true;
	}

	private <T> SigningOrderResult<T> tokenMutates(
			final TokenID id,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		return tokenMutates(id, factory, Collections.emptyList(), linkedRefs);
	}

	private <T> SigningOrderResult<T> tokenMutates(
			final TokenID id,
			final SigningOrderResultFactory<T> factory,
			final List<Function<TokenSigningMetadata, Optional<JKey>>> optionalKeyLookups,
			final @Nullable LinkedRefs linkedRefs
	) {
		List<JKey> required = new ArrayList<>();

		var result = sigMetaLookup.tokenSigningMetaFor(id, linkedRefs);
		if (result.succeeded()) {
			var meta = result.metadata();
			meta.adminKey().ifPresent(required::add);
			optionalKeyLookups.forEach(lookup -> {
				var candidate = lookup.apply(meta);
				candidate.ifPresent(required::add);
			});
		} else {
			return factory.forMissingToken();
		}
		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> tokenAdjusts(
			final TokenID id,
			final SigningOrderResultFactory<T> factory,
			final Function<TokenSigningMetadata, Optional<JKey>> optionalKeyLookup,
			final LinkedRefs linkedRefs
	) {
		List<JKey> required = EMPTY_LIST;

		var result = sigMetaLookup.tokenSigningMetaFor(id, linkedRefs);
		if (result.succeeded()) {
			var optionalKey = optionalKeyLookup.apply(result.metadata());
			if (optionalKey.isPresent()) {
				required = mutable(required);
				required.add(optionalKey.get());
			} else {
				return SigningOrderResult.noKnownKeys();
			}
		} else {
			return factory.forMissingToken();
		}
		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> tokenAssociate(
			final AccountID payer,
			final TokenAssociateTransactionBody op,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		return forSingleAccount(payer, op.getAccount(), factory, linkedRefs);
	}

	private <T> SigningOrderResult<T> tokenDissociate(
			AccountID payer,
			TokenDissociateTransactionBody op,
			SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		return forSingleAccount(payer, op.getAccount(), factory, linkedRefs);
	}

	private <T> SigningOrderResult<T> scheduleCreate(
			final AccountID payer,
			final ScheduleCreateTransactionBody op,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		List<JKey> required = new ArrayList<>();

		addToMutableReqIfPresent(
				op,
				ScheduleCreateTransactionBody::hasAdminKey,
				ScheduleCreateTransactionBody::getAdminKey,
				required);

		int before = required.size();
		var couldAddPayer = addAccount(
				payer,
				op,
				ScheduleCreateTransactionBody::hasPayerAccountID,
				ScheduleCreateTransactionBody::getPayerAccountID,
				required,
				linkedRefs);
		if (!couldAddPayer) {
			return accountFailure(INVALID_ACCOUNT, factory);
		}
		int after = required.size();
		if (after > before) {
			var dupKey = required.get(after - 1).duplicate();
			dupKey.setForScheduledTxn(true);
			required.set(after - 1, dupKey);
		}

		var scheduledTxn = MiscUtils.asOrdinary(op.getScheduledTransactionBody());
		var mergeError = mergeScheduledKeys(required, scheduledTxn, factory, linkedRefs);
		return mergeError.orElseGet(() -> factory.forValidOrder(required));
	}

	private <T> SigningOrderResult<T> scheduleSign(
			final ScheduleID id,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		List<JKey> required = new ArrayList<>();

		var result = sigMetaLookup.scheduleSigningMetaFor(id, linkedRefs);
		if (!result.succeeded()) {
			return factory.forMissingSchedule();
		}
		var optionalPayer = result.metadata().designatedPayer();
		if (optionalPayer.isPresent()) {
			var payerResult = sigMetaLookup.accountSigningMetaFor(optionalPayer.get(), linkedRefs);
			if (!payerResult.succeeded()) {
				return accountFailure(INVALID_ACCOUNT, factory);
			} else {
				var dupKey = payerResult.metadata().key().duplicate();
				dupKey.setForScheduledTxn(true);
				required.add(dupKey);
			}
		}
		var scheduledTxn = result.metadata().scheduledTxn();
		var mergeError = mergeScheduledKeys(required, scheduledTxn, factory, linkedRefs);
		return mergeError.orElseGet(() -> factory.forValidOrder(required));
	}

	private <T> Optional<SigningOrderResult<T>> mergeScheduledKeys(
			final List<JKey> required,
			final TransactionBody scheduledTxn,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		try {
			final var scheduledFunction = MiscUtils.functionOf(scheduledTxn);
			if (IMPOSSIBLE_TO_SCHEDULE.contains(scheduledFunction)) {
				return Optional.of(factory.forUnschedulableTxn());
			}
			var scheduledOrderResult = keysForOtherParties(scheduledTxn, factory, linkedRefs);
			if (scheduledOrderResult.hasErrorReport()) {
				return Optional.of(factory.forUnresolvableRequiredSigners());
			} else {
				var scheduledKeys = scheduledOrderResult.getOrderedKeys();
				for (JKey key : scheduledKeys) {
					var dup = key.duplicate();
					dup.setForScheduledTxn(true);
					required.add(dup);
				}
			}
		} catch (UnknownHederaFunctionality e) {
			return Optional.of(factory.forUnschedulableTxn());
		}
		return Optional.empty();
	}

	private <T> SigningOrderResult<T> scheduleDelete(
			final ScheduleID id,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		final List<JKey> required = new ArrayList<>();

		var result = sigMetaLookup.scheduleSigningMetaFor(id, linkedRefs);
		if (result.succeeded()) {
			var meta = result.metadata();
			meta.adminKey().ifPresent(required::add);
		} else {
			return factory.forMissingSchedule();
		}

		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> forSingleAccount(
			final AccountID payer,
			final AccountID target,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		List<JKey> required = EMPTY_LIST;

		if (!payer.equals(target)) {
			var result = sigMetaLookup.accountSigningMetaFor(target, linkedRefs);
			if (result.succeeded()) {
				var meta = result.metadata();
				required = mutable(required);
				required.add(meta.key());
			} else {
				return factory.forMissingAccount();
			}
		}

		return factory.forValidOrder(required);
	}

	private KeyOrderingFailure includeIfNecessary(
			final AccountID payer,
			final AccountAmount adjust,
			final List<JKey> required,
			final boolean autoCreationAllowed,
			final @Nullable LinkedRefs linkedRefs
	) {
		var account = adjust.getAccountID();
		if (!payer.equals(account)) {
			var result = sigMetaLookup.aliasableAccountSigningMetaFor(account, linkedRefs);
			if (result.succeeded()) {
				var meta = result.metadata();
				if (adjust.getAmount() < 0 || meta.receiverSigRequired()) {
					required.add(meta.key());
				}
			} else {
				final var reason = result.failureIfAny();
				if (autoCreationAllowed && reason == MISSING_ACCOUNT && adjust.getAmount() > 0L && isAlias(account)) {
					return NONE;
				} else {
					/* MISSING_ACCOUNT is not a "terminal" signature status, because in several transaction types
					 * we want a downstream components to choose a more specific failure response. But missing
					 * accounts in a transfer list can be safely given the terminal INVALID_ACCOUNT_ID status. */
					return (reason == MISSING_ACCOUNT) ? INVALID_ACCOUNT : reason;
				}
			}
		}
		return NONE;
	}

	private KeyOrderingFailure nftIncludeIfNecessary(
			final AccountID payer,
			final AccountID party,
			final AccountID counterparty,
			final List<JKey> required,
			final TokenID token,
			final CryptoTransferTransactionBody op,
			final @Nullable LinkedRefs linkedRefs
	) {
		if (!payer.equals(party)) {
			var result = sigMetaLookup.aliasableAccountSigningMetaFor(party, linkedRefs);
			if (!result.succeeded()) {
				return result.failureIfAny();
			}
			var meta = result.metadata();
			final var isSender = counterparty == null;
			if (isSender || meta.receiverSigRequired()) {
				required.add(meta.key());
			} else {
				final var tokenResult = sigMetaLookup.tokenSigningMetaFor(token, linkedRefs);
				if (!tokenResult.succeeded()) {
					return tokenResult.failureIfAny();
				} else {
					final var tokenMeta = tokenResult.metadata();
					if (tokenMeta.hasRoyaltyWithFallback()) {
						final var fallbackApplies = !receivesFungibleValue(counterparty, op) &&
								counterparty.getAccountNum() != tokenMeta.treasury().num();
						if (fallbackApplies) {
							required.add(meta.key());
						}
					}
				}
			}
			return result.failureIfAny();
		}

		return NONE;
	}

	private boolean receivesFungibleValue(AccountID target, CryptoTransferTransactionBody op) {
		for (var adjust : op.getTransfers().getAccountAmountsList()) {
			if (adjust.getAmount() > 0 && adjust.getAccountID().equals(target)) {
				return true;
			}
		}
		for (var transfers : op.getTokenTransfersList()) {
			for (var adjust : transfers.getTransfersList()) {
				if (adjust.getAmount() > 0 && adjust.getAccountID().equals(target)) {
					return true;
				}
			}
		}
		return false;
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
			final ConsensusSubmitMessageTransactionBody op,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		List<JKey> required = EMPTY_LIST;
		var target = op.getTopicID();
		var result = sigMetaLookup.topicSigningMetaFor(target, linkedRefs);
		if (!result.succeeded()) {
			return topicFailure(result.failureIfAny(), factory);
		}
		if (result.metadata().hasSubmitKey()) {
			required = mutable(required);
			required.add(result.metadata().submitKey());
		}
		return factory.forValidOrder(required);
	}

	private <T> SigningOrderResult<T> topicUpdate(
			final AccountID payer,
			final ConsensusUpdateTopicTransactionBody op,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		List<JKey> required = EMPTY_LIST;
		if (onlyExtendsExpiry(op)) {
			return factory.forValidOrder(required);
		}

		var target = op.getTopicID();
		var targetResult = sigMetaLookup.topicSigningMetaFor(target, linkedRefs);
		if (!targetResult.succeeded()) {
			return topicFailure(targetResult.failureIfAny(), factory);
		}
		var meta = targetResult.metadata();
		if (meta.hasAdminKey()) {
			required = mutable(required);
			required.add(meta.adminKey());
		}

		if (op.hasAdminKey()) {
			required = mutable(required);
			var candidate = asUsableFcKey(op.getAdminKey());
			candidate.ifPresent(required::add);
		}
		if (op.hasAutoRenewAccount() && !isEliding(op.getAutoRenewAccount())) {
			var account = op.getAutoRenewAccount();
			if (!payer.equals(account)) {
				var autoRenewResult = sigMetaLookup.accountSigningMetaFor(account, linkedRefs);
				if (autoRenewResult.succeeded()) {
					required = mutable(required);
					required.add(autoRenewResult.metadata().key());
				} else {
					return accountFailure(MISSING_AUTORENEW_ACCOUNT, factory);
				}
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
			final ConsensusDeleteTopicTransactionBody op,
			final SigningOrderResultFactory<T> factory,
			final @Nullable LinkedRefs linkedRefs
	) {
		List<JKey> required = EMPTY_LIST;

		var target = op.getTopicID();
		var targetResult = sigMetaLookup.topicSigningMetaFor(target, linkedRefs);
		if (!targetResult.succeeded()) {
			return topicFailure(targetResult.failureIfAny(), factory);
		} else if (targetResult.metadata().hasAdminKey()) {
			required = mutable(required);
			required.add(targetResult.metadata().adminKey());
		}
		return factory.forValidOrder(required);
	}
}
