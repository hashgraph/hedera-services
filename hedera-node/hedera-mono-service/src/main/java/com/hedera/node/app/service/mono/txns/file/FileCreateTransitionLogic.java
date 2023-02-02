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
package com.hedera.node.app.service.mono.txns.file;

import static com.hedera.node.app.service.mono.context.primitives.StateView.EMPTY_WACL;
import static com.hedera.node.app.service.mono.txns.file.FileUpdateTransitionLogic.wrapped;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_WACL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.service.mono.files.HederaFs;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.txns.TransitionLogic;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class FileCreateTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(FileCreateTransitionLogic.class);

    private final HederaFs hfs;
    private final UsageLimits usageLimits;
    private final OptionValidator validator;
    private final SigImpactHistorian sigImpactHistorian;
    private final TransactionContext txnCtx;

    @Inject
    public FileCreateTransitionLogic(
            final HederaFs hfs,
            final UsageLimits usageLimits,
            final OptionValidator validator,
            final SigImpactHistorian sigImpactHistorian,
            final TransactionContext txnCtx) {
        this.hfs = hfs;
        this.validator = validator;
        this.txnCtx = txnCtx;
        this.usageLimits = usageLimits;
        this.sigImpactHistorian = sigImpactHistorian;
    }

    @Override
    public void doStateTransition() {
        final var op = txnCtx.accessor().getTxn().getFileCreate();

        try {
            final var validity = assessedValidity(op);
            if (validity != OK) {
                txnCtx.setStatus(validity);
                return;
            }

            final var attr = asAttr(op);
            final var sponsor = txnCtx.activePayer();
            final var created = hfs.create(op.getContents().toByteArray(), attr, sponsor);

            txnCtx.setCreated(created);
            txnCtx.setStatus(SUCCESS);
            sigImpactHistorian.markEntityChanged(created.getFileNum());
            usageLimits.refreshFiles();
        } catch (final IllegalArgumentException iae) {
            FileUpdateTransitionLogic.mapToStatus(iae, txnCtx);
        } catch (final Exception unknown) {
            log.warn(
                    "Unrecognized failure handling {}!",
                    txnCtx.accessor().getSignedTxnWrapper(),
                    unknown);
            txnCtx.setStatus(FAIL_INVALID);
        }
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasFileCreate;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    private ResponseCodeEnum assessedValidity(final FileCreateTransactionBody op) {
        if (!usageLimits.areCreatableFiles(1)) {
            return MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
        }
        if (op.hasKeys() && !validator.hasGoodEncoding(wrapped(op.getKeys()))) {
            return INVALID_FILE_WACL;
        }

        return OK;
    }

    private HFileMeta asAttr(final FileCreateTransactionBody op) {
        final JKey wacl = op.hasKeys() ? asFcKeyUnchecked(wrapped(op.getKeys())) : EMPTY_WACL;

        return new HFileMeta(false, wacl, op.getExpirationTime().getSeconds(), op.getMemo());
    }

    private ResponseCodeEnum validate(final TransactionBody fileCreateTxn) {
        final var op = fileCreateTxn.getFileCreate();

        final var memoValidity = validator.memoCheck(op.getMemo());
        if (memoValidity != OK) {
            return memoValidity;
        }

        if (!op.hasExpirationTime()) {
            return INVALID_EXPIRATION_TIME;
        }

        final var effectiveDuration =
                Duration.newBuilder()
                        .setSeconds(
                                op.getExpirationTime().getSeconds()
                                        - fileCreateTxn
                                                .getTransactionID()
                                                .getTransactionValidStart()
                                                .getSeconds())
                        .build();
        if (!validator.isValidAutoRenewPeriod(effectiveDuration)) {
            return AUTORENEW_DURATION_NOT_IN_RANGE;
        }

        return OK;
    }
}
