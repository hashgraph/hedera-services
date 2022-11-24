/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.sigs.metadata;

import static com.hedera.node.app.service.mono.context.primitives.StateView.EMPTY_WACL;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isAlias;
import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.node.app.service.mono.utils.EntityNum.fromAccountId;
import static com.hedera.node.app.service.mono.utils.EntityNum.fromTokenId;
import static com.hedera.node.app.service.mono.utils.EntityNum.fromTopicId;

import com.hedera.node.app.service.mono.config.FileNumbers;
import com.hedera.node.app.service.mono.context.StateChildren;
import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.service.mono.files.MetadataMapFactory;
import com.hedera.node.app.service.mono.files.store.FcBlobsBytesStore;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.legacy.core.jproto.JContractIDKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure;
import com.hedera.node.app.service.mono.sigs.order.LinkedRefs;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;

public final class StateChildrenSigMetadataLookup implements SigMetadataLookup {
    private final FileNumbers fileNumbers;
    private final AliasManager aliasManager;
    private final StateChildren stateChildren;
    private final Map<FileID, HFileMeta> metaMap;
    private final Function<MerkleToken, TokenSigningMetadata> tokenMetaTransform;

    public StateChildrenSigMetadataLookup(
            final FileNumbers fileNumbers,
            final StateChildren stateChildren,
            final Function<MerkleToken, TokenSigningMetadata> tokenMetaTransform) {
        this.fileNumbers = fileNumbers;
        this.stateChildren = stateChildren;
        this.tokenMetaTransform = tokenMetaTransform;
        this.aliasManager = new AliasManager(stateChildren::aliases);

        final var blobStore = new FcBlobsBytesStore(stateChildren::storage);
        this.metaMap = MetadataMapFactory.metaMapFrom(blobStore);
    }

    @Override
    public Instant sourceSignedAt() {
        return stateChildren.signedAt();
    }

    @Override
    public SafeLookupResult<FileSigningMetadata> fileSigningMetaFor(
            final FileID id, final @Nullable LinkedRefs linkedRefs) {
        if (fileNumbers.isSoftwareUpdateFile(id.getFileNum())) {
            return SPECIAL_FILE_RESULT;
        }
        if (linkedRefs != null) {
            linkedRefs.link(id.getFileNum());
        }
        final var meta = metaMap.get(id);
        return (meta == null)
                ? SafeLookupResult.failure(KeyOrderingFailure.MISSING_FILE)
                : new SafeLookupResult<>(new FileSigningMetadata(meta.getWacl()));
    }

    @Override
    public SafeLookupResult<TopicSigningMetadata> topicSigningMetaFor(
            final TopicID id, final @Nullable LinkedRefs linkedRefs) {
        if (linkedRefs != null) {
            linkedRefs.link(id.getTopicNum());
        }
        final var topic = stateChildren.topics().get(fromTopicId(id));
        if (topic == null || topic.isDeleted()) {
            return SafeLookupResult.failure(KeyOrderingFailure.INVALID_TOPIC);
        } else {
            final var effAdminKey = topic.hasAdminKey() ? topic.getAdminKey() : null;
            final var effSubmitKey = topic.hasSubmitKey() ? topic.getSubmitKey() : null;
            return new SafeLookupResult<>(new TopicSigningMetadata(effAdminKey, effSubmitKey));
        }
    }

    @Override
    public SafeLookupResult<TokenSigningMetadata> tokenSigningMetaFor(
            final TokenID id, final @Nullable LinkedRefs linkedRefs) {
        if (linkedRefs != null) {
            linkedRefs.link(id.getTokenNum());
        }
        final var token = stateChildren.tokens().get(fromTokenId(id));
        return (token == null)
                ? SafeLookupResult.failure(KeyOrderingFailure.MISSING_TOKEN)
                : new SafeLookupResult<>(tokenMetaTransform.apply(token));
    }

    @Override
    public SafeLookupResult<AccountSigningMetadata> accountSigningMetaFor(
            final AccountID id, final @Nullable LinkedRefs linkedRefs) {
        return lookupAccountByNumber(fromAccountId(id), linkedRefs);
    }

    @Override
    public SafeLookupResult<AccountSigningMetadata> aliasableAccountSigningMetaFor(
            final AccountID idOrAlias, final @Nullable LinkedRefs linkedRefs) {
        if (isAlias(idOrAlias)) {
            final var alias = idOrAlias.getAlias();
            if (alias.size() == EVM_ADDRESS_SIZE) {
                final var evmAddress = alias.toByteArray();
                if (aliasManager.isMirror(evmAddress)) {
                    return lookupAccountByNumber(EntityNum.fromMirror(evmAddress), linkedRefs);
                }
            }
            if (linkedRefs != null) {
                linkedRefs.link(alias);
            }
            final var explicitId = aliasManager.lookupIdBy(alias);
            return (explicitId == MISSING_NUM)
                    ? SafeLookupResult.failure(KeyOrderingFailure.MISSING_ACCOUNT)
                    : lookupAccountByNumber(explicitId, linkedRefs);
        } else {
            return lookupAccountByNumber(fromAccountId(idOrAlias), linkedRefs);
        }
    }

    @Override
    public SafeLookupResult<ScheduleSigningMetadata> scheduleSigningMetaFor(
            final ScheduleID id, final @Nullable LinkedRefs linkedRefs) {
        if (linkedRefs != null) {
            linkedRefs.link(id.getScheduleNum());
        }
        final var schedule =
                stateChildren
                        .schedules()
                        .byId()
                        .get(new EntityNumVirtualKey(EntityNum.fromScheduleId(id)));
        if (schedule == null) {
            return SafeLookupResult.failure(KeyOrderingFailure.MISSING_SCHEDULE);
        } else {
            final var scheduleMeta =
                    new ScheduleSigningMetadata(
                            schedule.adminKey(),
                            schedule.ordinaryViewOfScheduledTxn(),
                            schedule.hasExplicitPayer()
                                    ? Optional.of(schedule.payer().toGrpcAccountId())
                                    : Optional.empty());
            return new SafeLookupResult<>(scheduleMeta);
        }
    }

    @Override
    public SafeLookupResult<ContractSigningMetadata> aliasableContractSigningMetaFor(
            final ContractID idOrAlias, final @Nullable LinkedRefs linkedRefs) {
        final var id =
                (linkedRefs == null)
                        ? EntityIdUtils.unaliased(idOrAlias, aliasManager)
                        : EntityIdUtils.unaliased(idOrAlias, aliasManager, linkedRefs::link);
        return (id == MISSING_NUM)
                ? SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT)
                : lookupContractByNumber(id, linkedRefs);
    }

    private SafeLookupResult<ContractSigningMetadata> lookupContractByNumber(
            final EntityNum id, final @Nullable LinkedRefs linkedRefs) {
        if (linkedRefs != null) {
            linkedRefs.link(id.longValue());
        }
        final var contract = stateChildren.accounts().get(id);
        if (contract == null || contract.isDeleted() || !contract.isSmartContract()) {
            return SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT);
        } else {
            JKey key;
            if ((key = contract.getAccountKey()) == null || key instanceof JContractIDKey) {
                return SafeLookupResult.failure(KeyOrderingFailure.IMMUTABLE_CONTRACT);
            } else {
                return new SafeLookupResult<>(
                        new ContractSigningMetadata(key, contract.isReceiverSigRequired()));
            }
        }
    }

    private SafeLookupResult<AccountSigningMetadata> lookupAccountByNumber(
            final EntityNum id, final @Nullable LinkedRefs linkedRefs) {
        if (linkedRefs != null) {
            linkedRefs.link(id.longValue());
        }
        final var account = stateChildren.accounts().get(id);
        if (account == null) {
            return SafeLookupResult.failure(KeyOrderingFailure.MISSING_ACCOUNT);
        } else {
            final var key = account.getAccountKey();
            if (key == null || key.isEmpty()) {
                return SafeLookupResult.failure(KeyOrderingFailure.IMMUTABLE_ACCOUNT);
            }
            return new SafeLookupResult<>(
                    new AccountSigningMetadata(
                            account.getAccountKey(), account.isReceiverSigRequired()));
        }
    }

    private static final FileSigningMetadata SPECIAL_FILE_META =
            new FileSigningMetadata(EMPTY_WACL);
    private static final SafeLookupResult<FileSigningMetadata> SPECIAL_FILE_RESULT =
            new SafeLookupResult<>(SPECIAL_FILE_META);
}
