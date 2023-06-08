/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.grpc.marshalling;

import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static com.hedera.node.app.service.mono.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isAlias;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isOfEvmAddressSize;
import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.node.app.service.mono.utils.MiscUtils.isSerializedProtoKey;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class AliasResolver {
    private int perceivedMissing = 0;
    private int perceivedCreations = 0;
    private int perceivedLazyCreations = 0;
    private int perceivedInvalidCreations = 0;
    private final Map<ByteString, EntityNum> resolutions = new HashMap<>();

    /* ---- temporary token transfer resolutions map containing the token transfers to alias, is needed to check if
    an alias is repeated. It is allowed to be repeated in multiple token transfer lists, but not in a single
    token transfer list ---- */
    private final Map<ByteString, EntityNum> tokenTransferResolutions = new HashMap<>();

    private enum Result {
        KNOWN_ALIAS,
        UNKNOWN_ALIAS,
        REPEATED_UNKNOWN_ALIAS
    }

    public CryptoTransferTransactionBody resolve(
            final CryptoTransferTransactionBody op, final AliasManager aliasManager) {
        final var resolvedOp = CryptoTransferTransactionBody.newBuilder();

        final var resolvedAdjusts = resolveHbarAdjusts(op.getTransfers(), aliasManager);
        resolvedOp.setTransfers(resolvedAdjusts);

        final var resolvedTokenAdjusts = resolveTokenAdjusts(op.getTokenTransfersList(), aliasManager);
        resolvedOp.addAllTokenTransfers(resolvedTokenAdjusts);

        return resolvedOp.build();
    }

    public void perceiveEthTxn(final EthTxData ethTxDataMeta, final AliasManager aliasManager) {
        if (aliasManager
                        .lookupIdBy(ByteStringUtils.wrapUnsafely(ethTxDataMeta.to()))
                        .equals(MISSING_NUM)
                && ethTxDataMeta.value().compareTo(BigInteger.ZERO) > 0) {
            perceivedLazyCreations++;
        }
    }

    public Map<ByteString, EntityNum> resolutions() {
        return resolutions;
    }

    public int perceivedMissingAliases() {
        return perceivedMissing;
    }

    public int perceivedAutoCreations() {
        return perceivedCreations;
    }

    public int perceivedLazyCreations() {
        return perceivedLazyCreations;
    }

    public int perceivedInvalidCreations() {
        return perceivedInvalidCreations;
    }

    public static boolean usesAliases(final CryptoTransferTransactionBody op) {
        for (var adjust : op.getTransfers().getAccountAmountsList()) {
            if (isAlias(adjust.getAccountID())) {
                return true;
            }
        }
        for (var tokenAdjusts : op.getTokenTransfersList()) {
            for (var ownershipChange : tokenAdjusts.getNftTransfersList()) {
                if (isAlias(ownershipChange.getSenderAccountID()) || isAlias(ownershipChange.getReceiverAccountID())) {
                    return true;
                }
            }
            for (var tokenAdjust : tokenAdjusts.getTransfersList()) {
                if (isAlias(tokenAdjust.getAccountID())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<TokenTransferList> resolveTokenAdjusts(
            final List<TokenTransferList> opTokenAdjusts, final AliasManager aliasManager) {
        final List<TokenTransferList> resolvedTokenAdjusts = new ArrayList<>();
        for (var tokenAdjust : opTokenAdjusts) {
            final var resolvedTokenAdjust = TokenTransferList.newBuilder();
            tokenTransferResolutions.clear();

            resolvedTokenAdjust.setToken(tokenAdjust.getToken());
            for (final var adjust : tokenAdjust.getTransfersList()) {
                final var result =
                        resolveInternalFungible(aliasManager, adjust, resolvedTokenAdjust::addTransfers, true);

                // Since the receiver can be an unknown alias in a CryptoTransfer perceive the
                // result
                perceiveNonNftResult(result, adjust);
            }

            for (final var change : tokenAdjust.getNftTransfersList()) {
                final var resolvedChange = change.toBuilder().setSerialNumber(change.getSerialNumber());

                final var senderResult =
                        resolveInternal(aliasManager, change.getSenderAccountID(), resolvedChange::setSenderAccountID);
                if (senderResult != Result.KNOWN_ALIAS) {
                    perceivedMissing++;
                }
                final var receiverResult = resolveInternal(
                        aliasManager, change.getReceiverAccountID(), resolvedChange::setReceiverAccountID);

                // Since the receiver can be an unknown alias in a CryptoTransfer perceive the
                // result
                perceiveNftReceiverResult(receiverResult, change);

                resolvedTokenAdjust.addNftTransfers(resolvedChange.build());
            }

            resolvedTokenAdjusts.add(resolvedTokenAdjust.build());
        }
        return resolvedTokenAdjusts;
    }

    private TransferList resolveHbarAdjusts(final TransferList opAdjusts, final AliasManager aliasManager) {
        final var resolvedAdjusts = TransferList.newBuilder();
        for (var adjust : opAdjusts.getAccountAmountsList()) {
            final var result = resolveInternalFungible(aliasManager, adjust, resolvedAdjusts::addAccountAmounts, false);
            perceiveNonNftResult(result, adjust);
        }
        return resolvedAdjusts.build();
    }

    private Result resolveInternal(
            final AliasManager aliasManager, final AccountID idOrAlias, final Consumer<AccountID> resolvingAction) {
        AccountID resolvedId = idOrAlias;
        var result = Result.KNOWN_ALIAS;
        if (isAlias(idOrAlias)) {
            final var alias = idOrAlias.getAlias();
            if (isOfEvmAddressSize(alias)) {
                final var evmAddress = alias.toByteArray();
                if (isMirror(evmAddress)) {
                    offerMirrorId(evmAddress, resolvingAction);
                    return Result.KNOWN_ALIAS;
                }
            }
            final var resolution = aliasManager.lookupIdBy(alias);
            if (resolution != MISSING_NUM) {
                resolvedId = resolution.toGrpcAccountId();
            } else {
                result = netOf(alias, true);
            }
            resolutions.put(alias, resolution);
        }
        resolvingAction.accept(resolvedId);
        return result;
    }

    private Result resolveInternalFungible(
            final AliasManager aliasManager,
            final AccountAmount adjust,
            final Consumer<AccountAmount> resolvingAction,
            final boolean isForToken) {
        AccountAmount resolvedAdjust = adjust;
        var result = Result.KNOWN_ALIAS;
        if (isAlias(adjust.getAccountID())) {
            final var alias = adjust.getAccountID().getAlias();
            if (isOfEvmAddressSize(alias)) {
                final var evmAddress = alias.toByteArray();
                if (isMirror(evmAddress)) {
                    offerMirrorId(
                            evmAddress,
                            id -> resolvingAction.accept(
                                    adjust.toBuilder().setAccountID(id).build()));
                    return Result.KNOWN_ALIAS;
                }
            }
            final var resolution = aliasManager.lookupIdBy(alias);
            if (resolution == MISSING_NUM) {
                result = netOf(alias, !isForToken);
            } else {
                resolvedAdjust = adjust.toBuilder()
                        .setAccountID(resolution.toGrpcAccountId())
                        .build();
            }
            resolutions.put(alias, resolution);
            tokenTransferResolutions.put(alias, resolution);
        }
        resolvingAction.accept(resolvedAdjust);
        return result;
    }

    private void perceiveNftReceiverResult(final Result result, final NftTransfer change) {
        perceiveResult(
                result, change.getSerialNumber(), change.getReceiverAccountID().getAlias(), false);
    }

    private void perceiveNonNftResult(final Result result, final AccountAmount adjust) {
        perceiveResult(result, adjust.getAmount(), adjust.getAccountID().getAlias(), true);
    }

    private void perceiveResult(
            final Result result, final long assetChange, final ByteString alias, final boolean repetitionsAreInvalid) {
        if (result == Result.UNKNOWN_ALIAS) {
            if (assetChange > 0) {
                if (isSerializedProtoKey(alias)) {
                    perceivedCreations++;
                } else if (isOfEvmAddressSize(alias)) {
                    perceivedLazyCreations++;
                } else {
                    perceivedInvalidCreations++;
                }
            } else {
                perceivedMissing++;
            }
        } else if (repetitionsAreInvalid && result == Result.REPEATED_UNKNOWN_ALIAS) {
            perceivedInvalidCreations++;
        }
    }

    private Result netOf(final ByteString alias, final boolean isForNftOrHbar) {
        if (isForNftOrHbar) {
            // Note a REPEATED_UNKNOWN_ALIAS is still valid for the NFT receiver case
            return resolutions.containsKey(alias) ? Result.REPEATED_UNKNOWN_ALIAS : Result.UNKNOWN_ALIAS;
        } else {
            // If the token resolutions map already contains this unknown alias, we can assume
            // it was successfully auto-created by a prior mention in this CryptoTransfer.
            // (If it appeared in a sender location, this transfer will fail anyway.)
            if (tokenTransferResolutions.containsKey(alias)) {
                return Result.REPEATED_UNKNOWN_ALIAS;
            }
            return resolutions.containsKey(alias) ? Result.KNOWN_ALIAS : Result.UNKNOWN_ALIAS;
        }
    }

    private void offerMirrorId(final byte[] evmAddress, final Consumer<AccountID> resolvingAction) {
        final var contractNum = Longs.fromBytes(
                evmAddress[12],
                evmAddress[13],
                evmAddress[14],
                evmAddress[15],
                evmAddress[16],
                evmAddress[17],
                evmAddress[18],
                evmAddress[19]);
        resolvingAction.accept(STATIC_PROPERTIES.scopedAccountWith(contractNum));
    }

    /* ---- Only used for tests */
    @VisibleForTesting
    public Map<ByteString, EntityNum> tokenResolutions() {
        return tokenTransferResolutions;
    }
}
