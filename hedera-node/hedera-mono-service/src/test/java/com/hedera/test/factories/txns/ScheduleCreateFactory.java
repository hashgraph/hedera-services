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
package com.hedera.test.factories.txns;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;

public class ScheduleCreateFactory extends SignedTxnFactory<ScheduleCreateFactory> {
    private boolean omitAdmin = false;
    private boolean scheduleNonsense = false;
    private Optional<AccountID> payer = Optional.empty();
    private Transaction scheduled = Transaction.getDefaultInstance();

    private ScheduleCreateFactory() {}

    public static ScheduleCreateFactory newSignedScheduleCreate() {
        return new ScheduleCreateFactory();
    }

    public ScheduleCreateFactory schedulingNonsense() {
        scheduleNonsense = true;
        return this;
    }

    public ScheduleCreateFactory missingAdmin() {
        omitAdmin = true;
        return this;
    }

    public ScheduleCreateFactory designatingPayer(AccountID id) {
        payer = Optional.of(id);
        return this;
    }

    public ScheduleCreateFactory creating(Transaction scheduled) {
        this.scheduled = scheduled;
        return this;
    }

    @Override
    protected ScheduleCreateFactory self() {
        return this;
    }

    @Override
    protected long feeFor(Transaction signedTxn, int numPayerKeys) {
        return 0;
    }

    @Override
    protected void customizeTxn(TransactionBody.Builder txn) {
        var op = ScheduleCreateTransactionBody.newBuilder();
        if (!omitAdmin) {
            op.setAdminKey(TxnHandlingScenario.SCHEDULE_ADMIN_KT.asKey());
        }
        payer.ifPresent(op::setPayerAccountID);
        if (scheduleNonsense) {
            op.setScheduledTransactionBody(SchedulableTransactionBody.getDefaultInstance());
        } else {
            try {
                var accessor = SignedTxnAccessor.from(scheduled.toByteArray());
                var scheduled = ScheduleUtils.fromOrdinary(accessor.getTxn());
                op.setScheduledTransactionBody(scheduled);
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalStateException(
                        "ScheduleCreate test used unparseable transaction!", e);
            }
        }
        txn.setScheduleCreate(op);
    }
}
