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
import static com.hedera.services.txns.file.FileUpdateTransitionLogic.wrapped;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_WACL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.ExpiryMeta;
import com.hedera.services.txns.validation.ExpiryValidator;
import com.hedera.services.txns.validation.OptionValidator;
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
    private final ExpiryValidator expiryValidator;

    @Inject
    public FileCreateTransitionLogic(
            final HederaFs hfs,
            final UsageLimits usageLimits,
            final OptionValidator validator,
            final SigImpactHistorian sigImpactHistorian,
            final TransactionContext txnCtx,
            final ExpiryValidator expiryValidator) {
        this.hfs = hfs;
        this.validator = validator;
        this.txnCtx = txnCtx;
        this.usageLimits = usageLimits;
        this.expiryValidator = expiryValidator;
        this.sigImpactHistorian = sigImpactHistorian;
    }

    @Override
    public void doStateTransition() {
        final var accessor = txnCtx.accessor();
        var op = accessor.getTxn().getFileCreate();

        try {
            var validity = assessedValidity(op);
            if (validity != OK) {
                txnCtx.setStatus(validity);
                return;
            }

            final var txnId = accessor.getTxnId();
            final var expiryMeta = ExpiryMeta.fromFileCreateOp(op);
            final var summarizedMeta =
                    expiryValidator.summarizeCreationAttempt(
                            txnId.getTransactionValidStart().getSeconds(),
                            false,
                            expiryMeta);
            if (!summarizedMeta.isValid()) {
                txnCtx.setStatus(summarizedMeta.status());
                return;
            }

            var attr = asAttr(op, summarizedMeta.meta());
            var sponsor = txnCtx.activePayer();
            var created = hfs.create(op.getContents().toByteArray(), attr, sponsor);

            txnCtx.setCreated(created);
            txnCtx.setStatus(SUCCESS);
            sigImpactHistorian.markEntityChanged(created.getFileNum());
            usageLimits.refreshFiles();
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

    private HFileMeta asAttr(
            final FileCreateTransactionBody op,
            final ExpiryMeta validExpiryMeta) {
        final var wacl = op.hasKeys() ? asFcKeyUnchecked(wrapped(op.getKeys())) : StateView.EMPTY_WACL;
        return new HFileMeta(
                false,
                wacl,
                validExpiryMeta.expiry(),
                op.getMemo(),
                validExpiryMeta.autoRenewId(),
                validExpiryMeta.readableAutoRenewPeriod());
    }

    private ResponseCodeEnum validate(final TransactionBody fileCreateTxn) {
        var op = fileCreateTxn.getFileCreate();

        var memoValidity = validator.memoCheck(op.getMemo());
        if (memoValidity != OK) {
            return memoValidity;
        }

        final var configuresAutoRenew = op.hasAutoRenewAccount() && op.hasAutoRenewPeriod();
        if (!op.hasExpirationTime() && !configuresAutoRenew) {
            return INVALID_EXPIRATION_TIME;
        }

        return OK;
    }
}
