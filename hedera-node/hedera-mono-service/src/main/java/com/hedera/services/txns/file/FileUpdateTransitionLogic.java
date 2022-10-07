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

import static com.hedera.services.files.TieredHederaFs.firstUnsuccessful;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PREPARED_UPDATE_FILE_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static java.lang.Boolean.TRUE;
import static java.lang.Math.max;

import com.hedera.services.config.EntityNumbers;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.HederaFs;
import com.hedera.services.files.TieredHederaFs;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class FileUpdateTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(FileUpdateTransitionLogic.class);

    private final HederaFs hfs;
    private final EntityNumbers entityNums;
    private final OptionValidator validator;
    private final SigImpactHistorian sigImpactHistorian;
    private final TransactionContext txnCtx;
    private final Supplier<MerkleNetworkContext> networkCtx;

    @Inject
    public FileUpdateTransitionLogic(
            final HederaFs hfs,
            final EntityNumbers entityNums,
            final OptionValidator validator,
            final SigImpactHistorian sigImpactHistorian,
            final TransactionContext txnCtx,
            final Supplier<MerkleNetworkContext> networkCtx) {
        this.hfs = hfs;
        this.entityNums = entityNums;
        this.txnCtx = txnCtx;
        this.validator = validator;
        this.networkCtx = networkCtx;
        this.sigImpactHistorian = sigImpactHistorian;
    }

    @Override
    public void doStateTransition() {
        final var op = txnCtx.accessor().getTxn().getFileUpdate();

        try {
            var validity = assessedValidity(op);
            if (validity != OK) {
                txnCtx.setStatus(validity);
                return;
            }

            final var target = op.getFileID();
            final var attr = hfs.getattr(target);
            if (attr.isDeleted()) {
                txnCtx.setStatus(FILE_DELETED);
                return;
            }

            if (!isAuthorizedToProcessFile(op, attr, target)) {
                return;
            }

            Optional<HederaFs.UpdateResult> replaceResult = Optional.empty();
            if (!op.getContents().isEmpty()) {
                replaceResult = Optional.of(hfs.overwrite(target, op.getContents().toByteArray()));
            }
            attr.setExpiry(max(op.getExpirationTime().getSeconds(), attr.getExpiry()));

            Optional<HederaFs.UpdateResult> changeResult = Optional.empty();
            if (replaceResult.map(HederaFs.UpdateResult::fileReplaced).orElse(TRUE)) {
                updateAttrBased(attr, op);
                changeResult = Optional.of(hfs.setattr(target, attr));
            }

            final var status =
                    firstUnsuccessful(
                            replaceResult.map(HederaFs.UpdateResult::outcome).orElse(SUCCESS),
                            changeResult.map(HederaFs.UpdateResult::outcome).orElse(SUCCESS));
            txnCtx.setStatus(status);
            if (status == SUCCESS) {
                sigImpactHistorian.markEntityChanged(target.getFileNum());
            }
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

    private void updateAttrBased(final HFileMeta attr, final FileUpdateTransactionBody op) {
        if (op.hasKeys()) {
            attr.setWacl(asFcKeyUnchecked(wrapped(op.getKeys())));
        }
        if (op.hasMemo()) {
            attr.setMemo(op.getMemo().getValue());
        }
    }

    private boolean isAuthorizedToProcessFile(
            final FileUpdateTransactionBody op, final HFileMeta attr, final FileID target) {
        if (attr.getWacl().isEmpty() && (op.hasKeys() || !op.getContents().isEmpty())) {
            /* The transaction is trying to update an immutable file; in general, not a legal operation,
            but the semantics change for a superuser (i.e., sysadmin or treasury) updating a system file. */
            final var isSysFile = entityNums.isSystemFile(target);
            final var isSysAdmin =
                    entityNums.accounts().isSuperuser(txnCtx.activePayer().getAccountNum());
            if (!(isSysAdmin && isSysFile)) {
                txnCtx.setStatus(UNAUTHORIZED);
                return false;
            }
        }
        return true;
    }

    static void mapToStatus(final IllegalArgumentException iae, final TransactionContext txnCtx) {
        if (iae.getCause() instanceof DecoderException) {
            txnCtx.setStatus(BAD_ENCODING);
            return;
        }
        try {
            var type = TieredHederaFs.IllegalArgumentType.valueOf(iae.getMessage());
            txnCtx.setStatus(type.suggestedStatus());
        } catch (IllegalArgumentException untyped) {
            log.warn(
                    "Unrecognized detail message '{}' handling {}!",
                    iae.getMessage(),
                    txnCtx.accessor().getSignedTxnWrapper(),
                    untyped);
            txnCtx.setStatus(FAIL_INVALID);
        }
    }

    private ResponseCodeEnum assessedValidity(final FileUpdateTransactionBody op) {
        if (!op.hasFileID() || !hfs.exists(op.getFileID())) {
            return INVALID_FILE_ID;
        }

        if (networkCtx.get().getPreparedUpdateFileNum() == op.getFileID().getFileNum()) {
            return PREPARED_UPDATE_FILE_IS_IMMUTABLE;
        }

        if (op.hasKeys() && !validator.hasGoodEncoding(wrapped(op.getKeys()))) {
            return BAD_ENCODING;
        }

        return OK;
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasFileUpdate;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    private ResponseCodeEnum validate(final TransactionBody fileUpdateTxn) {
        var op = fileUpdateTxn.getFileUpdate();

        final var memoValidity = !op.hasMemo() ? OK : validator.memoCheck(op.getMemo().getValue());
        if (memoValidity != OK) {
            return memoValidity;
        }

        if (op.hasExpirationTime()) {
            final var effectiveDuration =
                    Duration.newBuilder()
                            .setSeconds(
                                    op.getExpirationTime().getSeconds()
                                            - fileUpdateTxn
                                                    .getTransactionID()
                                                    .getTransactionValidStart()
                                                    .getSeconds())
                            .build();
            if (!validator.isValidAutoRenewPeriod(effectiveDuration)) {
                return AUTORENEW_DURATION_NOT_IN_RANGE;
            }
        }

        return OK;
    }

    static Key wrapped(final KeyList wacl) {
        return Key.newBuilder().setKeyList(wacl).build();
    }
}
