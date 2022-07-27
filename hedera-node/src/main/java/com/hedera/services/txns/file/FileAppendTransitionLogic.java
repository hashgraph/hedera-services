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

import static com.hedera.services.txns.file.FileUpdateTransitionLogic.mapToStatus;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PREPARED_UPDATE_FILE_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;

import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class FileAppendTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(FileAppendTransitionLogic.class);

    private static final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_RUBBER_STAMP =
            ignore -> OK;

    private final HederaFs hfs;
    private final FileNumbers fileNumbers;
    private final TransactionContext txnCtx;
    private final SigImpactHistorian sigImpactHistorian;
    private final Supplier<MerkleNetworkContext> networkCtx;

    @Inject
    public FileAppendTransitionLogic(
            final HederaFs hfs,
            final FileNumbers fileNumbers,
            final TransactionContext txnCtx,
            final SigImpactHistorian sigImpactHistorian,
            final Supplier<MerkleNetworkContext> networkCtx) {
        this.hfs = hfs;
        this.txnCtx = txnCtx;
        this.networkCtx = networkCtx;
        this.fileNumbers = fileNumbers;
        this.sigImpactHistorian = sigImpactHistorian;
    }

    @Override
    public void doStateTransition() {
        var op = txnCtx.accessor().getTxn().getFileAppend();

        try {
            var target = op.getFileID();
            var data = op.getContents().toByteArray();

            ResponseCodeEnum validity;
            if (fileNumbers.isSoftwareUpdateFile(target.getFileNum())) {
                validity = hfs.exists(target) ? OK : INVALID_FILE_ID;
            } else {
                Optional<HFileMeta> attr =
                        hfs.exists(target) ? Optional.of(hfs.getattr(target)) : Optional.empty();
                validity = classify(attr);
            }

            if (validity != OK) {
                txnCtx.setStatus(validity);
                return;
            }
            if (networkCtx.get().getPreparedUpdateFileNum() == target.getFileNum()) {
                txnCtx.setStatus(PREPARED_UPDATE_FILE_IS_IMMUTABLE);
                return;
            }

            sigImpactHistorian.markEntityChanged(target.getFileNum());
            final var result = hfs.append(target, data);
            final var status = result.outcome();
            txnCtx.setStatus(status);
        } catch (IllegalArgumentException iae) {
            mapToStatus(iae, txnCtx);
        } catch (Exception unknown) {
            log.warn(
                    "Unrecognized failure handling {}!",
                    txnCtx.accessor().getSignedTxnWrapper(),
                    unknown);
            txnCtx.setStatus(FAIL_INVALID);
        }
    }

    private ResponseCodeEnum classify(final Optional<HFileMeta> attr) {
        if (attr.isEmpty()) {
            return INVALID_FILE_ID;
        } else {
            var info = attr.get();
            if (info.isDeleted()) {
                return FILE_DELETED;
            } else if (info.getWacl().isEmpty()) {
                return UNAUTHORIZED;
            } else {
                return OK;
            }
        }
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasFileAppend;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return SEMANTIC_RUBBER_STAMP;
    }
}
