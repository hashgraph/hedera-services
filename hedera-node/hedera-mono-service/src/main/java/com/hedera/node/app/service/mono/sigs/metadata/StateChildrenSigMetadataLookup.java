/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure.*;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.*;
import static com.hedera.node.app.service.mono.utils.EntityNum.*;

import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.node.app.service.mono.config.FileNumbers;
import com.hedera.node.app.service.mono.context.StateChildren;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.service.mono.files.MetadataMapFactory;
import com.hedera.node.app.service.mono.files.store.FcBlobsBytesStore;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.legacy.core.jproto.JContractIDKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JWildcardECDSAKey;
import com.hedera.node.app.service.mono.sigs.order.LinkedRefs;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.*;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class StateChildrenSigMetadataLookup implements SigMetadataLookup {
    private final FileNumbers fileNumbers;
    private final AliasManager aliasManager;
    private final StateChildren stateChildren;
    private final Map<FileID, HFileMeta> metaMap;
    private final Function<MerkleToken, TokenSigningMetadata> tokenMetaTransform;

    private final GlobalDynamicProperties properties;

    public StateChildrenSigMetadataLookup(
            final FileNumbers fileNumbers,
            final StateChildren stateChildren,
            final Function<MerkleToken, TokenSigningMetadata> tokenMetaTransform,
            final GlobalDynamicProperties properties) {
        this.fileNumbers = fileNumbers;
        this.stateChildren = stateChildren;
        this.tokenMetaTransform = tokenMetaTransform;
        this.aliasManager = new AliasManager(stateChildren::aliases);

        final var blobStore = new FcBlobsBytesStore(stateChildren::storage);
        this.metaMap = MetadataMapFactory.metaMapFrom(blobStore);
        this.properties = properties;
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
                ? SafeLookupResult.failure(MISSING_FILE)
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
            return SafeLookupResult.failure(INVALID_TOPIC);
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
                ? SafeLookupResult.failure(MISSING_TOKEN)
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
            if (isOfEvmAddressSize(alias)) {
                final var evmAddress = alias.toByteArray();
                if (HederaEvmContractAliases.isMirror(evmAddress)) {
                    return lookupAccountByNumber(EntityNum.fromMirror(evmAddress), linkedRefs);
                }
            }
            if (linkedRefs != null) {
                linkedRefs.link(alias);
            }
            final var explicitId = aliasManager.lookupIdBy(alias);
            return (explicitId == MISSING_NUM)
                    ? SafeLookupResult.failure(MISSING_ACCOUNT)
                    : lookupAccountByNumber(explicitId, linkedRefs);
        } else {
            return lookupAccountByNumber(fromAccountId(idOrAlias), linkedRefs);
        }
    }

    @Override
    public EntityNum unaliasedAccount(AccountID idOrAlias, final @Nullable LinkedRefs linkedRefs) {
        if (isAlias(idOrAlias)) {
            return (linkedRefs == null)
                    ? unaliased(idOrAlias, aliasManager)
                    : unaliased(idOrAlias, aliasManager, linkedRefs::link);
        }
        return fromAccountId(idOrAlias);
    }

    @Override
    public SafeLookupResult<ScheduleSigningMetadata> scheduleSigningMetaFor(
            final ScheduleID id, final @Nullable LinkedRefs linkedRefs) {
        if (linkedRefs != null) {
            linkedRefs.link(id.getScheduleNum());
        }
        final var schedule =
                stateChildren.schedules().byId().get(new EntityNumVirtualKey(EntityNum.fromScheduleId(id)));
        if (schedule == null) {
            return SafeLookupResult.failure(MISSING_SCHEDULE);
        } else {
            final var scheduleMeta = new ScheduleSigningMetadata(
                    schedule.adminKey(),
                    schedule.ordinaryViewOfScheduledTxn(),
                    schedule.hasExplicitPayer() ? Optional.of(schedule.payer().toGrpcAccountId()) : Optional.empty());
            return new SafeLookupResult<>(scheduleMeta);
        }
    }

    @Override
    public SafeLookupResult<ContractSigningMetadata> aliasableContractSigningMetaFor(
            final ContractID idOrAlias, final @Nullable LinkedRefs linkedRefs) {
        final var id = (linkedRefs == null)
                ? unaliased(idOrAlias, aliasManager)
                : unaliased(idOrAlias, aliasManager, linkedRefs::link);
        return (id == MISSING_NUM)
                ? SafeLookupResult.failure(INVALID_CONTRACT)
                : lookupContractByNumber(id, linkedRefs);
    }

    private SafeLookupResult<ContractSigningMetadata> lookupContractByNumber(
            final EntityNum id, final @Nullable LinkedRefs linkedRefs) {
        if (linkedRefs != null) {
            linkedRefs.link(id.longValue());
        }
        final var contract = stateChildren.accounts().get(id);
        if (contract == null || contract.isDeleted() || !contract.isSmartContract()) {
            return SafeLookupResult.failure(INVALID_CONTRACT);
        } else {
            final JKey key;
            if ((key = contract.getAccountKey()) == null || key instanceof JContractIDKey) {
                return SafeLookupResult.failure(IMMUTABLE_CONTRACT);
            } else {
                return new SafeLookupResult<>(new ContractSigningMetadata(key, contract.isReceiverSigRequired()));
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
            return SafeLookupResult.failure(MISSING_ACCOUNT);
        } else {
            final var key = account.getAccountKey();
            if (key.isEmpty()) {
                if (linkedRefs != null) {
                    linkedRefs.link(fileNumbers.applicationProperties());
                }

                if (!properties.isLazyCreationEnabled()) {
                    return SafeLookupResult.failure(IMMUTABLE_ACCOUNT);
                }

                final var accountAlias = account.getAlias();
                if (accountAlias.isEmpty()) {
                    return SafeLookupResult.failure(IMMUTABLE_ACCOUNT);
                } else {
                    return new SafeLookupResult<>(new AccountSigningMetadata(
                            new JWildcardECDSAKey(ByteStringUtils.unwrapUnsafelyIfPossible(accountAlias), true),
                            account.isReceiverSigRequired()));
                }
            }
            return new SafeLookupResult<>(
                    new AccountSigningMetadata(account.getAccountKey(), account.isReceiverSigRequired()));
        }
    }

    private static final FileSigningMetadata SPECIAL_FILE_META = new FileSigningMetadata(EMPTY_WACL);
    private static final SafeLookupResult<FileSigningMetadata> SPECIAL_FILE_RESULT =
            new SafeLookupResult<>(SPECIAL_FILE_META);
}
