package com.hedera.services.txns.schedule;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.ScheduleChecks;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.context.SingletonContextsManager.CONTEXTS;
import static com.hedera.services.keys.KeysHelper.ed25519ToJKey;
import static com.hedera.services.txns.schedule.SignatoryUtils.witnessInScope;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ScheduleSignTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(ScheduleSignTransitionLogic.class);

    private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

    private final ScheduleStore store;
    private final TransactionContext txnCtx;
    private final InHandleActivationHelper activationHelper;

    public ScheduleSignTransitionLogic(
            ScheduleStore store,
            TransactionContext txnCtx,
            InHandleActivationHelper activationHelper
    ) {
        this.store = store;
        this.txnCtx = txnCtx;
        this.activationHelper = activationHelper;
    }

    @Override
    public void doStateTransition() {
        try {
            transitionFor(txnCtx.accessor().getTxn().getScheduleSign());
        } catch (Exception e) {
            e.printStackTrace();
            log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxn4Log(), e);
            txnCtx.setStatus(FAIL_INVALID);
        }
    }

    private void transitionFor(ScheduleSignTransactionBody op) {
        var sb = new StringBuilder();
        var isNowReady = witnessInScope(op.getScheduleID(), store, activationHelper, sb);
        /* Uncomment for temporary log-based testing locally */
		if (store == CONTEXTS.lookup(0L).scheduleStore()) {
			log.info("\n>>> START ScheduleSign >>>\n{}<<< END ScheduleSign END <<<", sb);
		}
		txnCtx.setStatus(SUCCESS);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasScheduleSign;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
        return SYNTAX_CHECK;
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        ScheduleSignTransactionBody op = txnBody.getScheduleSign();

        return (op.hasScheduleID() && store.exists(op.getScheduleID())) ? OK : INVALID_SCHEDULE_ID;
    }
}
