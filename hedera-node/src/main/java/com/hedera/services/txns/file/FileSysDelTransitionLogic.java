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
import static com.hedera.services.state.submerkle.EntityId.fromGrpcFileId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class FileSysDelTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(FileSysDelTransitionLogic.class);

    private static final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_RUBBER_STAMP =
            ignore -> OK;

    private final boolean supported;
    private final HederaFs hfs;
    private final SigImpactHistorian sigImpactHistorian;
    private final TransactionContext txnCtx;
    private final Map<EntityId, Long> expiries;

    @Inject
    public FileSysDelTransitionLogic(
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
        try {
            var op = txnCtx.accessor().getTxn().getSystemDelete();
            var tbd = op.getFileID();
            var attr = new AtomicReference<HFileMeta>();
            var validity = tryLookupAgainst(hfs, tbd, attr);

            if (validity != OK) {
                txnCtx.setStatus(validity);
                return;
            }

            var info = attr.get();
            var newExpiry =
                    op.hasExpirationTime() ? op.getExpirationTime().getSeconds() : info.getExpiry();
            if (newExpiry <= txnCtx.consensusTime().getEpochSecond()) {
                hfs.rm(tbd);
            } else {
                var oldExpiry = info.getExpiry();
                info.setDeleted(true);
                info.setExpiry(newExpiry);
                hfs.setattr(tbd, info);
                expiries.put(fromGrpcFileId(tbd), oldExpiry);
            }
            txnCtx.setStatus(SUCCESS);
            sigImpactHistorian.markEntityChanged(tbd.getFileNum());
        } catch (Exception unknown) {
            log.warn(
                    "Unrecognized failure handling {}!",
                    txnCtx.accessor().getSignedTxnWrapper(),
                    unknown);
            txnCtx.setStatus(FAIL_INVALID);
        }
    }

    static ResponseCodeEnum tryLookupAgainst(
            HederaFs hfs, FileID tbd, AtomicReference<HFileMeta> attr) {
        if (hfs.exists(tbd)) {
            var info = hfs.getattr(tbd);
            if (info.isDeleted()) {
                return FILE_DELETED;
            } else {
                attr.set(info);
                return OK;
            }
        } else {
            return INVALID_FILE_ID;
        }
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return txn -> txn.hasSystemDelete() && txn.getSystemDelete().hasFileID();
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return SEMANTIC_RUBBER_STAMP;
    }
}
