/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.file;

import static com.hedera.services.context.properties.EntityType.FILE;
import static com.hedera.services.context.properties.PropertyNames.ENTITIES_SYSTEM_DELETABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FileSysUndelTransitionLogic implements TransitionLogic {
    private static final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_RUBBER_STAMP =
            ignore -> OK;

    private final boolean supported;
    private final HederaFs hfs;
    private final SigImpactHistorian sigImpactHistorian;
    private final TransactionContext txnCtx;
    private final Map<EntityId, Long> expiries;

    @Inject
    public FileSysUndelTransitionLogic(
            final HederaFs hfs,
            final SigImpactHistorian sigImpactHistorian,
            final Map<EntityId, Long> expiries,
            final TransactionContext txnCtx,
            @CompositeProps final PropertySource properties) {
        this.hfs = hfs;
        this.expiries = expiries;
        this.txnCtx = txnCtx;
        this.sigImpactHistorian = sigImpactHistorian;
        this.supported = properties.getTypesProperty(ENTITIES_SYSTEM_DELETABLE).contains(FILE);
    }

    @Override
    public void doStateTransition() {
        if (!supported) {
            txnCtx.setStatus(NOT_SUPPORTED);
            return;
        }

        var op = txnCtx.accessor().getTxn().getSystemUndelete();
        var tbu = op.getFileID();
        var entity = EntityId.fromGrpcFileId(tbu);
        var attr = new AtomicReference<HFileMeta>();

        var validity = tryLookup(tbu, entity, attr);
        if (validity != OK) {
            txnCtx.setStatus(validity);
            return;
        }

        var info = attr.get();
        var oldExpiry = expiries.get(entity);
        if (oldExpiry <= txnCtx.consensusTime().getEpochSecond()) {
            hfs.rm(tbu);
        } else {
            info.setDeleted(false);
            info.setExpiry(oldExpiry);
            hfs.sudoSetattr(tbu, info);
        }
        expiries.remove(entity);
        txnCtx.setStatus(SUCCESS);
        sigImpactHistorian.markEntityChanged(tbu.getFileNum());
    }

    private ResponseCodeEnum tryLookup(
            FileID tbu, EntityId entity, AtomicReference<HFileMeta> attr) {
        if (!expiries.containsKey(entity) || !hfs.exists(tbu)) {
            return INVALID_FILE_ID;
        }

        var info = hfs.getattr(tbu);
        if (info.isDeleted()) {
            attr.set(info);
            return OK;
        } else {
            return INVALID_FILE_ID;
        }
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return txn -> txn.hasSystemUndelete() && txn.getSystemUndelete().hasFileID();
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return SEMANTIC_RUBBER_STAMP;
    }
}
