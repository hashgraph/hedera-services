/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.node.app.service.mono.sigs.order;

import static com.hedera.node.app.hapi.utils.CommonUtils.functionOf;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure.IMMUTABLE_ACCOUNT;
import static com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure.IMMUTABLE_CONTRACT;
import static com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure.INVALID_ACCOUNT;
import static com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure.INVALID_CONTRACT;
import static com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure.INVALID_TOPIC;
import static com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;
import static com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure.MISSING_TOKEN;
import static com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure.NONE;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isAlias;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asUsableFcKey;
import static java.util.Collections.EMPTY_LIST;

import com.hedera.node.app.hapi.utils.exception.UnknownHederaFunctionality;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JWildcardECDSAKey;
import com.hedera.node.app.service.mono.sigs.metadata.SigMetadataLookup;
import com.hedera.node.app.service.mono.sigs.metadata.TokenSigningMetadata;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Encapsulates all policies related to which Hedera keys must have active signatures for a given
 * gRPC transaction to be valid.
 *
 * <p>Two strategy predicates are injected into this class, one with logic to decide if the WACL for
 * a file targeted by the gRPC transaction must have an active signature; and one with logic to make
 * an equivalent decision for a crypto account.
 */
public class SigRequirements {
    private static final Set<KeyOrderingFailure> INVALID_ACCOUNT_CODES = EnumSet.of(MISSING_ACCOUNT, IMMUTABLE_ACCOUNT);

    private final SignatureWaivers signatureWaivers;
    private final SigMetadataLookup sigMetaLookup;

    public SigRequirements(final SigMetadataLookup sigMetaLookup, final SignatureWaivers signatureWaivers) {
        this.sigMetaLookup = sigMetaLookup;
        this.signatureWaivers = signatureWaivers;
    }

    /**
     * Uses the provided factory to summarize an attempt to compute the canonical signing order of
     * the Hedera key(s) that must be active for the payer of the given gRPC transaction.
     *
     * @param txn the gRPC transaction of interest.
     * @param factory the result factory to use to summarize the listing attempt.
     * @param <T> the type of error report created by the factory.
     * @return a {@link SigningOrderResult} summarizing the listing attempt.
     */
    public <T> SigningOrderResult<T> keysForPayer(
            final TransactionBody txn, final SigningOrderResultFactory<T> factory) {
        return keysForPayer(txn, factory, null);
    }

    public <T> SigningOrderResult<T> keysForPayer(
            final TransactionBody txn,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        return keysForPayer(txn, factory, linkedRefs, null);
    }

    /**
     * Uses the provided factory to summarize an attempt to compute the canonical signing order of
     * the Hedera key(s) that must be active for the payer of the given gRPC transaction.
     *
     * @param txn the gRPC transaction of interest.
     * @param factory the result factory to use to summarize the listing attempt.
     * @param linkedRefs if non-null, the accumulator to use to record all entities referenced
     *     during the attempt
     * @param payer the payer for the transaction, null to use the one in the transaction ID
     * @param <T> the type of error report created by the factory.
     * @return a {@link SigningOrderResult} summarizing the listing attempt.
     */
    public <T> SigningOrderResult<T> keysForPayer(
            final TransactionBody txn,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs,
            final @Nullable AccountID payer) {
        if (linkedRefs != null) {
            linkedRefs.setSourceSignedAt(sigMetaLookup.sourceSignedAt());
        }
        return orderForPayer(
                factory, linkedRefs, payer == null ? txn.getTransactionID().getAccountID() : payer);
    }

    public <T> SigningOrderResult<T> keysForOtherParties(
            final TransactionBody txn, final SigningOrderResultFactory<T> factory) {
        return keysForOtherParties(txn, factory, null);
    }

    public <T> SigningOrderResult<T> keysForOtherParties(
            final TransactionBody txn,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        return keysForOtherParties(txn, factory, linkedRefs, null);
    }

    /**
     * Uses the provided factory to summarize an attempt to compute the canonical signing order of
     * the Hedera key(s) that must be active for any Hedera entities involved in a non-payer role in
     * the given gRPC transaction. (Which could also include the payer crypto account.)
     *
     * @param txn the gRPC transaction of interest
     * @param factory the result factory to use to summarize the listing attempt
     * @param linkedRefs if non-null, the accumulator to use to record all entities referenced
     *     during the attempt
     * @param payer the payer for the transaction, null to use the one in the transaction ID
     * @param <T> the type of error report created by the factory
     * @return a {@link SigningOrderResult} summarizing the listing attempt
     */
    public <T> SigningOrderResult<T> keysForOtherParties(
            final TransactionBody txn,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs,
            final @Nullable AccountID payer) {
        final var realPayer = payer == null ? txn.getTransactionID().getAccountID() : payer;
        final var cryptoOrder = forCrypto(txn, factory, linkedRefs, realPayer);
        if (cryptoOrder != null) {
            return cryptoOrder;
        }
        final var consensusOrder = forConsensus(txn, factory, linkedRefs, realPayer);
        if (consensusOrder != null) {
            return consensusOrder;
        }
        final var tokenOrder = forToken(txn, factory, linkedRefs, realPayer);
        if (tokenOrder != null) {
            return tokenOrder;
        }
        final var scheduleOrder = forSchedule(txn, factory, linkedRefs, realPayer);
        if (scheduleOrder != null) {
            return scheduleOrder;
        }
        final var fileOrder = forFile(txn, factory, linkedRefs, realPayer);
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
            final SigningOrderResultFactory<T> factory, final @Nullable LinkedRefs linkedRefs, final AccountID payer) {
        final var result = sigMetaLookup.accountSigningMetaFor(payer, linkedRefs);
        if (result.succeeded()) {
            return factory.forValidOrder(List.of(result.metadata().key()));
        } else {
            if (INVALID_ACCOUNT_CODES.contains(result.failureIfAny())) {
                return factory.forInvalidAccount();
            } else {
                return factory.forGeneralPayerError();
            }
        }
    }

    private <T> SigningOrderResult<T> forContract(
            final TransactionBody txn,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        if (txn.hasContractCreateInstance()) {
            return contractCreate(txn.getContractCreateInstance(), factory, linkedRefs);
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
            final @Nullable LinkedRefs linkedRefs,
            final AccountID payer) {
        if (txn.hasCryptoCreateAccount()) {
            return cryptoCreate(txn.getCryptoCreateAccount(), factory);
        } else if (txn.hasCryptoTransfer()) {
            return cryptoTransfer(payer, txn.getCryptoTransfer(), factory, linkedRefs);
        } else if (txn.hasCryptoUpdateAccount()) {
            return cryptoUpdate(payer, txn, factory, linkedRefs);
        } else if (txn.hasCryptoDelete()) {
            return cryptoDelete(payer, txn.getCryptoDelete(), factory, linkedRefs);
        } else if (txn.hasCryptoApproveAllowance()) {
            final var approveTxn = txn.getCryptoApproveAllowance();
            return cryptoApproveAllowance(
                    payer,
                    approveTxn.getCryptoAllowancesList(),
                    approveTxn.getTokenAllowancesList(),
                    approveTxn.getNftAllowancesList(),
                    factory,
                    linkedRefs);
        } else if (txn.hasCryptoDeleteAllowance()) {
            final var deleteAllowanceTxn = txn.getCryptoDeleteAllowance();
            return cryptoDeleteAllowance(payer, deleteAllowanceTxn.getNftAllowancesList(), factory, linkedRefs);
        } else {
            return null;
        }
    }

    private <T> SigningOrderResult<T> forSchedule(
            final TransactionBody txn,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs,
            final AccountID payer) {
        if (txn.hasScheduleCreate()) {
            return scheduleCreate(payer, txn, txn.getScheduleCreate(), factory, linkedRefs);
        } else if (txn.hasScheduleSign()) {
            return scheduleSign(txn.getScheduleSign(), factory, linkedRefs);
        } else if (txn.hasScheduleDelete()) {
            return scheduleDelete(txn.getScheduleDelete().getScheduleID(), factory, linkedRefs);
        } else {
            return null;
        }
    }

    private <T> SigningOrderResult<T> forToken(
            final TransactionBody txn,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs,
            final AccountID payer) {
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
            final @Nullable LinkedRefs linkedRefs,
            final AccountID payer) {
        if (txn.hasFileCreate()) {
            return fileCreate(txn.getFileCreate(), factory);
        } else if (txn.hasFileAppend()) {
            return fileAppend(txn, factory, linkedRefs, payer);
        } else if (txn.hasFileUpdate()) {
            return fileUpdate(txn, factory, linkedRefs, payer);
        } else if (txn.hasFileDelete()) {
            return fileDelete(txn.getFileDelete(), factory, linkedRefs);
        } else {
            return null;
        }
    }

    private <T> SigningOrderResult<T> forConsensus(
            final TransactionBody txn,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs,
            final AccountID payer) {
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
            final @Nullable LinkedRefs linkedRefs) {
        List<JKey> required = EMPTY_LIST;

        final var target = op.getContractID();
        final var targetResult = sigMetaLookup.aliasableContractSigningMetaFor(target, linkedRefs);
        if (!targetResult.succeeded()) {
            return contractFailure(targetResult.failureIfAny(), factory);
        }
        required = mutable(required);
        required.add(targetResult.metadata().key());

        if (op.hasTransferAccountID()) {
            final var beneficiary = op.getTransferAccountID();
            final var beneficiaryResult = sigMetaLookup.accountSigningMetaFor(beneficiary, linkedRefs);
            if (!beneficiaryResult.succeeded()) {
                return factory.forInvalidAccount();
            } else if (beneficiaryResult.metadata().receiverSigRequired()) {
                required.add(beneficiaryResult.metadata().key());
            }
        } else if (op.hasTransferContractID()) {
            final var beneficiary = op.getTransferContractID();
            final var beneficiaryResult = sigMetaLookup.aliasableContractSigningMetaFor(beneficiary, linkedRefs);
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
            final @Nullable LinkedRefs linkedRefs) {
        List<JKey> required = EMPTY_LIST;

        final var target = op.getContractID();
        final var result = sigMetaLookup.aliasableContractSigningMetaFor(target, linkedRefs);
        if (needsCurrentAdminSig(op)) {
            if (!result.succeeded()) {
                return contractFailure(result.failureIfAny(), factory);
            }
            required = mutable(required);
            required.add(result.metadata().key());
        }
        if (hasNonDeprecatedAdminKey(op)) {
            final var candidate = asUsableFcKey(op.getAdminKey());
            required = mutable(required);
            candidate.ifPresent(required::add);
        }
        if (hasAutoRenewId(op)) {
            final var autoRenewAccountResult =
                    sigMetaLookup.accountSigningMetaFor(op.getAutoRenewAccountId(), linkedRefs);
            if (!autoRenewAccountResult.succeeded()) {
                return accountFailure(INVALID_AUTORENEW_ACCOUNT, factory);
            }
            required = mutable(required);
            required.add(autoRenewAccountResult.metadata().key());
        }

        return factory.forValidOrder(required);
    }

    private boolean needsCurrentAdminSig(final ContractUpdateTransactionBody op) {
        return !op.hasExpirationTime()
                || hasNonDeprecatedAdminKey(op)
                || op.hasProxyAccountID()
                || op.hasAutoRenewPeriod()
                || op.hasFileID()
                || op.getMemo().length() > 0;
    }

    private boolean hasNonDeprecatedAdminKey(final ContractUpdateTransactionBody op) {
        return op.hasAdminKey() && !op.getAdminKey().hasContractID();
    }

    private <T> SigningOrderResult<T> contractCreate(
            final ContractCreateTransactionBody op,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        List<JKey> required = EMPTY_LIST;

        final var key = op.getAdminKey();
        if (!key.hasContractID()) {
            final var candidate = asUsableFcKey(key);
            if (candidate.isPresent()) {
                required = mutable(required);
                required.add(candidate.get());
            }
        }
        if (hasAutoRenewId(op)) {
            final var autoRenewAccountResult =
                    sigMetaLookup.accountSigningMetaFor(op.getAutoRenewAccountId(), linkedRefs);
            if (!autoRenewAccountResult.succeeded()) {
                return accountFailure(INVALID_AUTORENEW_ACCOUNT, factory);
            }
            required = mutable(required);
            required.add(autoRenewAccountResult.metadata().key());
        }
        return !required.isEmpty() ? factory.forValidOrder(required) : SigningOrderResult.noKnownKeys();
    }

    private boolean hasAutoRenewId(final ContractCreateTransactionBody op) {
        return op.hasAutoRenewAccountId()
                && !EntityId.fromGrpcAccountId(op.getAutoRenewAccountId()).equals(EntityId.MISSING_ENTITY_ID);
    }

    private boolean hasAutoRenewId(final ContractUpdateTransactionBody op) {
        return op.hasAutoRenewAccountId()
                && !EntityId.fromGrpcAccountId(op.getAutoRenewAccountId()).equals(EntityId.MISSING_ENTITY_ID);
    }

    private <T> SigningOrderResult<T> fileDelete(
            final FileDeleteTransactionBody op,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        final var target = op.getFileID();
        final var targetResult = sigMetaLookup.fileSigningMetaFor(target, linkedRefs);
        if (!targetResult.succeeded()) {
            return factory.forMissingFile();
        } else {
            final var wacl = targetResult.metadata().wacl();
            return wacl.isEmpty() ? SigningOrderResult.noKnownKeys() : factory.forValidOrder(List.of(wacl));
        }
    }

    private <T> SigningOrderResult<T> fileUpdate(
            final TransactionBody fileUpdateTxn,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs,
            final AccountID payer) {
        final var newWaclMustSign = !signatureWaivers.isNewFileWaclWaived(fileUpdateTxn, payer);
        final var targetWaclMustSign = !signatureWaivers.isTargetFileWaclWaived(fileUpdateTxn, payer);
        final var op = fileUpdateTxn.getFileUpdate();
        final var target = op.getFileID();
        final var targetResult = sigMetaLookup.fileSigningMetaFor(target, linkedRefs);
        if (!targetResult.succeeded()) {
            return factory.forMissingFile();
        } else {
            final List<JKey> required = new ArrayList<>();
            if (targetWaclMustSign) {
                final var wacl = targetResult.metadata().wacl();
                if (!wacl.isEmpty()) {
                    required.add(wacl);
                }
            }
            if (newWaclMustSign && op.hasKeys()) {
                final var candidate =
                        asUsableFcKey(Key.newBuilder().setKeyList(op.getKeys()).build());
                candidate.ifPresent(required::add);
            }
            return factory.forValidOrder(required);
        }
    }

    private <T> SigningOrderResult<T> fileAppend(
            final TransactionBody fileAppendTxn,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs,
            final AccountID payer) {
        final var targetWaclMustSign = !signatureWaivers.isAppendFileWaclWaived(fileAppendTxn, payer);
        final var op = fileAppendTxn.getFileAppend();
        final var target = op.getFileID();
        final var targetResult = sigMetaLookup.fileSigningMetaFor(target, linkedRefs);
        if (!targetResult.succeeded()) {
            return factory.forMissingFile();
        } else {
            if (targetWaclMustSign) {
                final var wacl = targetResult.metadata().wacl();
                return wacl.isEmpty() ? SigningOrderResult.noKnownKeys() : factory.forValidOrder(List.of(wacl));
            } else {
                return SigningOrderResult.noKnownKeys();
            }
        }
    }

    private <T> SigningOrderResult<T> fileCreate(
            final FileCreateTransactionBody op, final SigningOrderResultFactory<T> factory) {
        final var candidate =
                asUsableFcKey(Key.newBuilder().setKeyList(op.getKeys()).build());
        return candidate.isPresent()
                ? factory.forValidOrder(List.of(candidate.get()))
                : SigningOrderResult.noKnownKeys();
    }

    private <T> SigningOrderResult<T> cryptoApproveAllowance(
            final AccountID payer,
            final List<CryptoAllowance> cryptoAllowancesList,
            final List<TokenAllowance> tokenAllowancesList,
            final List<NftAllowance> nftAllowancesList,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        final List<JKey> requiredKeys = new ArrayList<>();

        for (final var allowance : cryptoAllowancesList) {
            final var owner = allowance.getOwner();
            if (includeOwnerIfNecessary(payer, owner, requiredKeys, linkedRefs) != NONE) {
                return factory.forInvalidAllowanceOwner();
            }
        }
        for (final var allowance : tokenAllowancesList) {
            final var owner = allowance.getOwner();
            if (includeOwnerIfNecessary(payer, owner, requiredKeys, linkedRefs) != NONE) {
                return factory.forInvalidAllowanceOwner();
            }
        }
        for (final var allowance : nftAllowancesList) {
            final var ownerId = allowance.getOwner();
            var operatorId = allowance.hasDelegatingSpender() ? allowance.getDelegatingSpender() : ownerId;
            // Only the owner can grant approveForAll
            if (allowance.getApprovedForAll().getValue()) {
                operatorId = ownerId;
            }
            if (includeOwnerIfNecessary(payer, operatorId, requiredKeys, linkedRefs) != NONE) {
                if (operatorId.equals(ownerId)) {
                    return factory.forInvalidAllowanceOwner();
                } else {
                    return factory.forInvalidDelegatingSpender();
                }
            }
        }

        return factory.forValidOrder(requiredKeys);
    }

    private <T> SigningOrderResult<T> cryptoDeleteAllowance(
            final AccountID payer,
            final List<NftRemoveAllowance> nftAllowancesList,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        final List<JKey> requiredKeys = new ArrayList<>();
        for (final var allowance : nftAllowancesList) {
            final var owner = allowance.getOwner();
            if (includeOwnerIfNecessary(payer, owner, requiredKeys, linkedRefs) != NONE) {
                return factory.forInvalidAllowanceOwner();
            }
        }

        return factory.forValidOrder(requiredKeys);
    }

    private <T> SigningOrderResult<T> cryptoDelete(
            final AccountID payer,
            final CryptoDeleteTransactionBody op,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        List<JKey> required = EMPTY_LIST;

        final var target = op.getDeleteAccountID();
        if (!payer.equals(target)) {
            final var targetResult = sigMetaLookup.accountSigningMetaFor(target, linkedRefs);
            if (!targetResult.succeeded()) {
                return accountFailure(targetResult.failureIfAny(), factory);
            }
            required = mutable(required);
            required.add(targetResult.metadata().key());
        }

        final var beneficiary = op.getTransferAccountID();
        if (!payer.equals(beneficiary)) {
            final var beneficiaryResult = sigMetaLookup.accountSigningMetaFor(beneficiary, linkedRefs);
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
            final @Nullable LinkedRefs linkedRefs) {
        List<JKey> required = EMPTY_LIST;
        final var newAccountKeyMustSign = !signatureWaivers.isNewAccountKeyWaived(cryptoUpdateTxn, payer);
        final var targetAccountKeyMustSign = !signatureWaivers.isTargetAccountKeyWaived(cryptoUpdateTxn, payer);
        final var op = cryptoUpdateTxn.getCryptoUpdateAccount();
        final var target = op.getAccountIDToUpdate();
        final var result = sigMetaLookup.accountSigningMetaFor(target, linkedRefs);
        if (!result.succeeded()) {
            return accountFailure(result.failureIfAny(), factory);
        } else {
            if (targetAccountKeyMustSign && !payer.equals(target)) {
                required = mutable(required);
                required.add(result.metadata().key());
            }
            if (newAccountKeyMustSign && op.hasKey()) {
                required = mutable(required);
                final var candidate = asUsableFcKey(op.getKey());
                candidate.ifPresent(required::add);
            }
        }

        return factory.forValidOrder(required);
    }

    private <T> SigningOrderResult<T> cryptoTransfer(
            final AccountID payer,
            final CryptoTransferTransactionBody op,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        final List<JKey> required = new ArrayList<>();

        KeyOrderingFailure failure;
        for (final TokenTransferList xfers : op.getTokenTransfersList()) {
            for (final AccountAmount adjust : xfers.getTransfersList()) {
                if ((failure = includeIfNecessary(payer, adjust, required, true, linkedRefs, false)) != NONE) {
                    return accountFailure(failure, factory);
                }
            }
            final var token = xfers.getToken();
            for (final NftTransfer adjust : xfers.getNftTransfersList()) {
                final var sender = adjust.getSenderAccountID();
                if ((failure = nftIncludeIfNecessary(
                                payer, sender, null, adjust.getIsApproval(), required, token, op, linkedRefs, false))
                        != NONE) {
                    return accountFailure(failure, factory);
                }
                final var receiver = adjust.getReceiverAccountID();
                if ((failure = nftIncludeIfNecessary(
                                payer, receiver, sender, false, required, token, op, linkedRefs, true))
                        != NONE) {
                    return (failure == MISSING_TOKEN) ? factory.forMissingToken() : accountFailure(failure, factory);
                }
            }
        }
        for (final AccountAmount adjust : op.getTransfers().getAccountAmountsList()) {
            if ((failure = includeIfNecessary(payer, adjust, required, true, linkedRefs, true)) != NONE) {
                return accountFailure(failure, factory);
            }
        }

        return factory.forValidOrder(required);
    }

    private <T> SigningOrderResult<T> contractFailure(
            final KeyOrderingFailure type, final SigningOrderResultFactory<T> factory) {
        if (type == INVALID_CONTRACT) {
            return factory.forInvalidContract();
        } else if (type == IMMUTABLE_CONTRACT) {
            return factory.forImmutableContract();
        } else {
            return factory.forGeneralError();
        }
    }

    private <T> SigningOrderResult<T> accountFailure(
            final KeyOrderingFailure type, final SigningOrderResultFactory<T> factory) {
        if (type == INVALID_ACCOUNT || type == IMMUTABLE_ACCOUNT) {
            return factory.forInvalidAccount();
        } else if (type == MISSING_ACCOUNT) {
            return factory.forMissingAccount();
        } else if (type == INVALID_AUTORENEW_ACCOUNT) {
            return factory.forInvalidAutoRenewAccount();
        } else {
            return factory.forGeneralError();
        }
    }

    private <T> SigningOrderResult<T> topicFailure(
            final KeyOrderingFailure type, final SigningOrderResultFactory<T> factory) {
        if (type == INVALID_TOPIC) {
            return factory.forMissingTopic();
        } else {
            return factory.forGeneralError();
        }
    }

    private List<JKey> mutable(final List<JKey> required) {
        return (required == EMPTY_LIST) ? new ArrayList<>() : required;
    }

    private <T> SigningOrderResult<T> cryptoCreate(
            final CryptoCreateTransactionBody op, final SigningOrderResultFactory<T> factory) {
        final var required = new ArrayList<JKey>();
        final var key = op.getKey();
        final var alias = op.getAlias();
        if (!alias.isEmpty()) {
            // semantic checks should have already verified alias is a valid evm address
            // add evm address key to req keys only if it is derived from a key, diff than the admin key
            final var isAliasDerivedFromDiffKey = !key.hasECDSASecp256K1()
                    || !Arrays.equals(
                            recoverAddressFromPubKey(key.getECDSASecp256K1().toByteArray()), alias.toByteArray());
            if (isAliasDerivedFromDiffKey) {
                required.add(new JWildcardECDSAKey(alias.toByteArray(), false));
            }
        }
        if (op.getReceiverSigRequired()) {
            final var candidate = asUsableFcKey(key);
            candidate.ifPresent(required::add);
        }
        return factory.forValidOrder(required);
    }

    private <T> SigningOrderResult<T> topicCreate(
            final AccountID payer,
            final ConsensusCreateTopicTransactionBody op,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        final List<JKey> required = new ArrayList<>();

        addToMutableReqIfPresent(
                op,
                ConsensusCreateTopicTransactionBody::hasAdminKey,
                ConsensusCreateTopicTransactionBody::getAdminKey,
                required);
        if (addAccount(
                        payer,
                        op,
                        ConsensusCreateTopicTransactionBody::hasAutoRenewAccount,
                        ConsensusCreateTopicTransactionBody::getAutoRenewAccount,
                        required,
                        linkedRefs)
                != NONE) {
            return accountFailure(INVALID_AUTORENEW_ACCOUNT, factory);
        }

        return factory.forValidOrder(required);
    }

    private <T> SigningOrderResult<T> tokenCreate(
            final AccountID payer,
            final TokenCreateTransactionBody op,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        final List<JKey> required = new ArrayList<>();

        final var failure = addAccount(
                payer,
                op,
                TokenCreateTransactionBody::hasTreasury,
                TokenCreateTransactionBody::getTreasury,
                required,
                linkedRefs);
        if (failure != NONE) {
            return accountFailure(failure, factory);
        }
        final var couldAddAutoRenew = addAccount(
                        payer,
                        op,
                        TokenCreateTransactionBody::hasAutoRenewAccount,
                        TokenCreateTransactionBody::getAutoRenewAccount,
                        required,
                        linkedRefs)
                == NONE;
        if (!couldAddAutoRenew) {
            return accountFailure(INVALID_AUTORENEW_ACCOUNT, factory);
        }
        addToMutableReqIfPresent(
                op, TokenCreateTransactionBody::hasAdminKey, TokenCreateTransactionBody::getAdminKey, required);
        for (final var customFee : op.getCustomFeesList()) {
            final var collector = customFee.getFeeCollectorAccountId();
            /* A fractional fee collector and a collector for a fixed fee denominated
            in the units of the newly created token both must always sign a TokenCreate,
            since these are automatically associated to the newly created token. */
            final boolean couldAddCollector;
            if (customFee.hasFixedFee()) {
                final var fixedFee = customFee.getFixedFee();
                final var alwaysAdd = fixedFee.hasDenominatingTokenId()
                        && fixedFee.getDenominatingTokenId().getTokenNum() == 0L;
                couldAddCollector = addAccount(payer, collector, required, alwaysAdd, linkedRefs) == NONE;
            } else if (customFee.hasFractionalFee()) {
                couldAddCollector = addAccount(payer, collector, required, true, linkedRefs) == NONE;
            } else {
                // TODO: Is this check needed here for a royalty fee?
                final var royaltyFee = customFee.getRoyaltyFee();
                var alwaysAdd = false;
                if (royaltyFee.hasFallbackFee()) {
                    final var fFee = royaltyFee.getFallbackFee();
                    alwaysAdd = fFee.hasDenominatingTokenId()
                            && fFee.getDenominatingTokenId().getTokenNum() == 0;
                }
                couldAddCollector = addAccount(payer, collector, required, alwaysAdd, linkedRefs) == NONE;
            }
            if (!couldAddCollector) {
                return factory.forInvalidFeeCollector();
            }
        }

        return factory.forValidOrder(required);
    }

    private <T> SigningOrderResult<T> tokenFreezing(
            final TokenID id, final SigningOrderResultFactory<T> factory, final @Nullable LinkedRefs linkedRefs) {
        return tokenAdjusts(id, factory, TokenSigningMetadata::freezeKey, linkedRefs);
    }

    private <T> SigningOrderResult<T> tokenKnowing(
            final TokenID id, final SigningOrderResultFactory<T> factory, final @Nullable LinkedRefs linkedRefs) {
        return tokenAdjusts(id, factory, TokenSigningMetadata::kycKey, linkedRefs);
    }

    private <T> SigningOrderResult<T> tokenRefloating(
            final TokenID id, final SigningOrderResultFactory<T> factory, final @Nullable LinkedRefs linkedRefs) {
        return tokenAdjusts(id, factory, TokenSigningMetadata::supplyKey, linkedRefs);
    }

    private <T> SigningOrderResult<T> tokenWiping(
            final TokenID id, final SigningOrderResultFactory<T> factory, final @Nullable LinkedRefs linkedRefs) {
        return tokenAdjusts(id, factory, TokenSigningMetadata::wipeKey, linkedRefs);
    }

    private <T> SigningOrderResult<T> tokenPausing(
            final TokenID id, final SigningOrderResultFactory<T> factory, final @Nullable LinkedRefs linkedRefs) {
        return tokenAdjusts(id, factory, TokenSigningMetadata::pauseKey, linkedRefs);
    }

    private <T> SigningOrderResult<T> tokenFeeScheduleUpdates(
            final AccountID payer,
            final TokenFeeScheduleUpdateTransactionBody op,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        final var id = op.getTokenId();
        final var result = sigMetaLookup.tokenSigningMetaFor(id, linkedRefs);
        if (result.succeeded()) {
            final var feeScheduleKey = result.metadata().feeScheduleKey();
            if (feeScheduleKey.isPresent()) {
                final List<JKey> required = new ArrayList<>();
                required.add(feeScheduleKey.get());
                for (final var customFee : op.getCustomFeesList()) {
                    final var collector = customFee.getFeeCollectorAccountId();
                    final var couldAddCollector =
                            addAccountIfReceiverSigRequired(payer, collector, required, linkedRefs) == NONE;
                    if (!couldAddCollector) {
                        return factory.forInvalidFeeCollector();
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
            final LinkedRefs linkedRefs) {
        final List<Function<TokenSigningMetadata, Optional<JKey>>> nonAdminReqs = Collections.emptyList();
        final var basic = tokenMutates(op.getToken(), factory, nonAdminReqs, linkedRefs);
        if (basic.hasErrorReport()) {
            return basic;
        }

        final var required = basic.getOrderedKeys();
        if (addAccount(
                        payer,
                        op,
                        TokenUpdateTransactionBody::hasAutoRenewAccount,
                        TokenUpdateTransactionBody::getAutoRenewAccount,
                        required,
                        linkedRefs)
                != NONE) {
            return accountFailure(INVALID_AUTORENEW_ACCOUNT, factory);
        }
        final KeyOrderingFailure failure;
        if ((failure = addAccount(
                        payer,
                        op,
                        TokenUpdateTransactionBody::hasTreasury,
                        TokenUpdateTransactionBody::getTreasury,
                        required,
                        linkedRefs))
                != NONE) {
            return accountFailure(failure, factory);
        }
        addToMutableReqIfPresent(
                op, TokenUpdateTransactionBody::hasAdminKey, TokenUpdateTransactionBody::getAdminKey, required);
        return basic;
    }

    private KeyOrderingFailure addAccountIfReceiverSigRequired(
            final AccountID payer, final AccountID id, final List<JKey> reqs, final @Nullable LinkedRefs linkedRefs) {
        return addAccount(payer, id, reqs, false, linkedRefs);
    }

    private <T> KeyOrderingFailure addAccount(
            final AccountID payer,
            final T op,
            final Predicate<T> isPresent,
            final Function<T, AccountID> getter,
            final List<JKey> reqs,
            final @Nullable LinkedRefs linkedRefs) {
        if (isPresent.test(op)) {
            return addAccount(payer, getter.apply(op), reqs, true, linkedRefs);
        }
        return NONE;
    }

    private KeyOrderingFailure addAccount(
            final AccountID payer,
            final AccountID id,
            final List<JKey> reqs,
            final boolean alwaysAdd,
            final @Nullable LinkedRefs linkedRefs) {
        if (!payer.equals(id)) {
            final var result = sigMetaLookup.accountSigningMetaFor(id, linkedRefs);
            if (result.succeeded()) {
                final var metadata = result.metadata();
                if (alwaysAdd || metadata.receiverSigRequired()) {
                    reqs.add(metadata.key());
                }
            } else {
                return result.failureIfAny();
            }
        }
        return NONE;
    }

    private <T> SigningOrderResult<T> tokenMutates(
            final TokenID id, final SigningOrderResultFactory<T> factory, final @Nullable LinkedRefs linkedRefs) {
        return tokenMutates(id, factory, Collections.emptyList(), linkedRefs);
    }

    private <T> SigningOrderResult<T> tokenMutates(
            final TokenID id,
            final SigningOrderResultFactory<T> factory,
            final List<Function<TokenSigningMetadata, Optional<JKey>>> optionalKeyLookups,
            final @Nullable LinkedRefs linkedRefs) {
        final List<JKey> required = new ArrayList<>();

        final var result = sigMetaLookup.tokenSigningMetaFor(id, linkedRefs);
        if (result.succeeded()) {
            final var meta = result.metadata();
            meta.adminKey().ifPresent(required::add);
            optionalKeyLookups.forEach(lookup -> {
                final var candidate = lookup.apply(meta);
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
            final LinkedRefs linkedRefs) {
        List<JKey> required = EMPTY_LIST;

        final var result = sigMetaLookup.tokenSigningMetaFor(id, linkedRefs);
        if (result.succeeded()) {
            final var optionalKey = optionalKeyLookup.apply(result.metadata());
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
            final @Nullable LinkedRefs linkedRefs) {
        return forSingleAccount(payer, op.getAccount(), factory, linkedRefs);
    }

    private <T> SigningOrderResult<T> tokenDissociate(
            final AccountID payer,
            final TokenDissociateTransactionBody op,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        return forSingleAccount(payer, op.getAccount(), factory, linkedRefs);
    }

    private <T> SigningOrderResult<T> scheduleCreate(
            final AccountID payer,
            final TransactionBody txn,
            final ScheduleCreateTransactionBody op,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        final List<JKey> required = new ArrayList<>();

        addToMutableReqIfPresent(
                op, ScheduleCreateTransactionBody::hasAdminKey, ScheduleCreateTransactionBody::getAdminKey, required);

        // We need to always add the custom payer to the sig requirements even if it equals the
        // payer.
        // It is still part of the "other" parties and we need to know to store it's key with the
        // schedule
        // in all cases. This fixes a case where the ScheduleCreate payer and the custom payer are
        // the same payer,
        // which would cause the custom payers signature to not get stored and then a ScheduleSign
        // would
        // not execute the transaction without and extra signature from the custom payer.
        if (op.hasPayerAccountID()) {
            final var payerResult = sigMetaLookup.accountSigningMetaFor(op.getPayerAccountID(), linkedRefs);
            if (!payerResult.succeeded()) {
                return accountFailure(payerResult.failureIfAny(), factory);
            } else {
                final var dupKey = payerResult.metadata().key().duplicate();
                dupKey.setForScheduledTxn(true);
                required.add(dupKey);
            }
        }

        // Payer must equal the transaction account ID for scheduled transactions. Scheduled
        // transactions do not
        // currently track any custom payer that was put on them at creation. There shouldn't
        // currently be any
        // code path to get to this anyway. If we ever allow scheduled transactions to contain
        // scheduled transactions
        // then this will need to change.
        if (!payer.equals(txn.getTransactionID().getAccountID())) {
            return factory.forInvalidAccount();
        }

        final var payerForNested = op.hasPayerAccountID()
                ? op.getPayerAccountID()
                : txn.getTransactionID().getAccountID();

        final var scheduledTxn = MiscUtils.asOrdinary(op.getScheduledTransactionBody(), txn.getTransactionID());
        final var mergeError = mergeScheduledKeys(required, scheduledTxn, factory, linkedRefs, payerForNested);
        return mergeError.orElseGet(() -> factory.forValidOrder(required));
    }

    private <T> SigningOrderResult<T> scheduleSign(
            final ScheduleSignTransactionBody op,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        final var id = op.getScheduleID();
        final List<JKey> required = new ArrayList<>();

        final var result = sigMetaLookup.scheduleSigningMetaFor(id, linkedRefs);
        if (!result.succeeded()) {
            return factory.forMissingSchedule();
        }

        final var optionalPayer = result.metadata().designatedPayer();
        if (optionalPayer.isPresent()) {
            final var payerResult = sigMetaLookup.accountSigningMetaFor(optionalPayer.get(), linkedRefs);
            if (!payerResult.succeeded()) {
                return accountFailure(payerResult.failureIfAny(), factory);
            } else {
                final var dupKey = payerResult.metadata().key().duplicate();
                dupKey.setForScheduledTxn(true);
                required.add(dupKey);
            }
        }

        final var scheduledTxn = result.metadata().scheduledTxn();
        final var payerForNested =
                optionalPayer.orElse(scheduledTxn.getTransactionID().getAccountID());

        final var mergeError = mergeScheduledKeys(required, scheduledTxn, factory, linkedRefs, payerForNested);
        return mergeError.orElseGet(() -> factory.forValidOrder(required));
    }

    private <T> Optional<SigningOrderResult<T>> mergeScheduledKeys(
            final List<JKey> required,
            final TransactionBody scheduledTxn,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs,
            final AccountID payerForNested) {
        try {
            final var scheduledFunction = functionOf(scheduledTxn);
            if (!MiscUtils.isSchedulable(scheduledFunction)) {
                return Optional.of(factory.forUnschedulableTxn());
            }
            final var scheduledOrderResult = keysForOtherParties(scheduledTxn, factory, linkedRefs, payerForNested);
            if (scheduledOrderResult.hasErrorReport()) {
                return Optional.of(factory.forUnresolvableRequiredSigners());
            } else {
                final var scheduledKeys = scheduledOrderResult.getOrderedKeys();
                for (final JKey key : scheduledKeys) {
                    final var dup = key.duplicate();
                    dup.setForScheduledTxn(true);
                    required.add(dup);
                }
            }
        } catch (final UnknownHederaFunctionality e) {
            return Optional.of(factory.forUnschedulableTxn());
        }
        return Optional.empty();
    }

    private <T> SigningOrderResult<T> scheduleDelete(
            final ScheduleID id, final SigningOrderResultFactory<T> factory, final @Nullable LinkedRefs linkedRefs) {
        final List<JKey> required = new ArrayList<>();

        final var result = sigMetaLookup.scheduleSigningMetaFor(id, linkedRefs);
        if (result.succeeded()) {
            final var meta = result.metadata();
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
            final @Nullable LinkedRefs linkedRefs) {
        List<JKey> required = EMPTY_LIST;

        if (!payer.equals(target)) {
            final var result = sigMetaLookup.accountSigningMetaFor(target, linkedRefs);
            if (result.succeeded()) {
                final var meta = result.metadata();
                required = mutable(required);
                required.add(meta.key());
            } else {
                return factory.forInvalidAccount();
            }
        }

        return factory.forValidOrder(required);
    }

    private KeyOrderingFailure includeOwnerIfNecessary(
            final AccountID payer, final AccountID owner, final List<JKey> required, final LinkedRefs linkedRefs) {
        if (!owner.equals(AccountID.getDefaultInstance()) && !payer.equals(owner)) {
            final var ownerResult = sigMetaLookup.accountSigningMetaFor(owner, linkedRefs);
            if (!ownerResult.succeeded()) {
                return INVALID_ACCOUNT;
            }
            required.add(ownerResult.metadata().key());
        }
        return NONE;
    }

    private KeyOrderingFailure includeIfNecessary(
            final AccountID payer,
            final AccountAmount adjust,
            final List<JKey> required,
            final boolean autoCreationAllowed,
            final @Nullable LinkedRefs linkedRefs,
            final boolean isForHbar) {
        final var account = adjust.getAccountID();
        if (!payer.equals(account)) {
            final var result = sigMetaLookup.aliasableAccountSigningMetaFor(account, linkedRefs);
            if (result.succeeded()) {
                final var meta = result.metadata();
                final var isUnapprovedDebit = adjust.getAmount() < 0 && !adjust.getIsApproval();

                if ((isUnapprovedDebit || meta.receiverSigRequired())) {
                    // we can skip adding the sender's key if the payer has allowance granted to use
                    // sender's hbar.
                    required.add(meta.key());
                }
            } else {
                final var reason = result.failureIfAny();
                final var isCredit = adjust.getAmount() > 0L;
                // Since the immutable accounts 0.0.800 and 0.0.801 allows crediting hbar,
                // it is not treated as a failure. But token transfer to the immutable accounts is
                // not valid.
                if (reason == IMMUTABLE_ACCOUNT && isCredit && isForHbar) {
                    return NONE;
                } else if (reason == MISSING_ACCOUNT && autoCreationAllowed && isCredit && isAlias(account)) {
                    return NONE;
                } else {
                    // These response codes can be refined in a future release
                    return INVALID_ACCOUNT_CODES.contains(reason) ? INVALID_ACCOUNT : reason;
                }
            }
        }
        return NONE;
    }

    private KeyOrderingFailure nftIncludeIfNecessary(
            final AccountID payer,
            final AccountID party,
            final AccountID counterparty,
            final boolean isApproval,
            final List<JKey> required,
            final TokenID token,
            final CryptoTransferTransactionBody op,
            final @Nullable LinkedRefs linkedRefs,
            final boolean autoCreationAllowed) {
        final var isSender = counterparty == null;
        if (!payer.equals(party)) {
            final var result = sigMetaLookup.aliasableAccountSigningMetaFor(party, linkedRefs);
            if (!result.succeeded()) {
                final var reason = result.failureIfAny();
                // Any token transfer to immutable accounts 0.0.800 and 0.0.801 is not valid.
                if (reason == MISSING_ACCOUNT && autoCreationAllowed && isAlias(party) && !isSender) {
                    return NONE;
                } else {
                    return reason;
                }
            }
            final var meta = result.metadata();
            final var isUnapprovedTransfer = isSender && !isApproval;
            final var isGatedReceipt = !isSender && meta.receiverSigRequired();

            if (isUnapprovedTransfer || isGatedReceipt) {
                required.add(meta.key());
            } else if (!isSender) {
                final var tokenResult = sigMetaLookup.tokenSigningMetaFor(token, linkedRefs);
                if (!tokenResult.succeeded()) {
                    return tokenResult.failureIfAny();
                } else {
                    final var tokenMeta = tokenResult.metadata();
                    if (tokenMeta.hasRoyaltyWithFallback() && !receivesFungibleValue(counterparty, op, linkedRefs)) {
                        // Fallback situation; but we still need to check if the treasury is
                        // the sender or receiver, since in neither case will the fallback fee
                        // actually be charged
                        final var treasury = tokenMeta.treasury().toGrpcAccountId();
                        if (!treasury.equals(party) && !treasury.equals(counterparty)) {
                            required.add(meta.key());
                        }
                    }
                }
            }
            return result.failureIfAny();
        }

        return NONE;
    }

    private boolean receivesFungibleValue(
            final AccountID target, final CryptoTransferTransactionBody op, LinkedRefs linkedRefs) {
        for (final var adjust : op.getTransfers().getAccountAmountsList()) {
            if (isReceivingFungibleValue(adjust, target, linkedRefs)) {
                return true;
            }
        }
        for (final var transfers : op.getTokenTransfersList()) {
            for (final var adjust : transfers.getTransfersList()) {
                if (isReceivingFungibleValue(adjust, target, linkedRefs)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isReceivingFungibleValue(
            final AccountAmount adjust, final AccountID target, final LinkedRefs linkedRefs) {
        final var unaliasedAccountNum = sigMetaLookup.unaliasedAccount(adjust.getAccountID(), linkedRefs);
        final var unaliasedTargetNum = sigMetaLookup.unaliasedAccount(target, linkedRefs);
        return adjust.getAmount() > 0 && unaliasedAccountNum.equals(unaliasedTargetNum);
    }

    private <T> void addToMutableReqIfPresent(
            final T op, final Predicate<T> checker, final Function<T, Key> getter, final List<JKey> required) {
        if (checker.test(op)) {
            final var candidate = asUsableFcKey(getter.apply(op));
            candidate.ifPresent(required::add);
        }
    }

    private <T> SigningOrderResult<T> messageSubmit(
            final ConsensusSubmitMessageTransactionBody op,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        List<JKey> required = EMPTY_LIST;
        final var target = op.getTopicID();
        final var result = sigMetaLookup.topicSigningMetaFor(target, linkedRefs);
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
            final @Nullable LinkedRefs linkedRefs) {
        List<JKey> required = EMPTY_LIST;
        if (onlyExtendsExpiry(op)) {
            return factory.forValidOrder(required);
        }

        final var target = op.getTopicID();
        final var targetResult = sigMetaLookup.topicSigningMetaFor(target, linkedRefs);
        if (!targetResult.succeeded()) {
            return topicFailure(targetResult.failureIfAny(), factory);
        }
        final var meta = targetResult.metadata();
        if (meta.hasAdminKey()) {
            required = mutable(required);
            required.add(meta.adminKey());
        }

        if (op.hasAdminKey()) {
            required = mutable(required);
            final var candidate = asUsableFcKey(op.getAdminKey());
            candidate.ifPresent(required::add);
        }
        if (op.hasAutoRenewAccount() && !isEliding(op.getAutoRenewAccount())) {
            final var account = op.getAutoRenewAccount();
            if (!payer.equals(account)) {
                final var autoRenewResult = sigMetaLookup.accountSigningMetaFor(account, linkedRefs);
                if (autoRenewResult.succeeded()) {
                    required = mutable(required);
                    required.add(autoRenewResult.metadata().key());
                } else {
                    return accountFailure(INVALID_AUTORENEW_ACCOUNT, factory);
                }
            }
        }

        return factory.forValidOrder(required);
    }

    private boolean isEliding(final AccountID id) {
        return id.getShardNum() == 0 && id.getRealmNum() == 0 && id.getAccountNum() == 0;
    }

    private boolean onlyExtendsExpiry(final ConsensusUpdateTopicTransactionBody op) {
        return op.hasExpirationTime()
                && !op.hasMemo()
                && !op.hasAdminKey()
                && !op.hasSubmitKey()
                && !op.hasAutoRenewPeriod()
                && !op.hasAutoRenewAccount();
    }

    private <T> SigningOrderResult<T> topicDelete(
            final ConsensusDeleteTopicTransactionBody op,
            final SigningOrderResultFactory<T> factory,
            final @Nullable LinkedRefs linkedRefs) {
        List<JKey> required = EMPTY_LIST;

        final var target = op.getTopicID();
        final var targetResult = sigMetaLookup.topicSigningMetaFor(target, linkedRefs);
        if (!targetResult.succeeded()) {
            return topicFailure(targetResult.failureIfAny(), factory);
        } else if (targetResult.metadata().hasAdminKey()) {
            required = mutable(required);
            required.add(targetResult.metadata().adminKey());
        }
        return factory.forValidOrder(required);
    }
}
