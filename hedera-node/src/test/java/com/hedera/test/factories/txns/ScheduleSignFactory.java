package com.hedera.test.factories.txns;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.core.jproto.JThresholdKey;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.List;

public class ScheduleSignFactory extends SignedTxnFactory<ScheduleSignFactory> {
    private ScheduleSignFactory() {}

    private ScheduleID id;
    private boolean omitSignature;
    private boolean sameSignerAsPayer;

    public static ScheduleSignFactory newSignedScheduleSign() {
        return new ScheduleSignFactory();
    }

    public ScheduleSignFactory updating(ScheduleID id) {
        this.id = id;
        return this;
    }

    public ScheduleSignFactory missingSignature() {
        omitSignature = true;
        return this;
    }

    public ScheduleSignFactory sameSignerAsPayer() {
        sameSignerAsPayer = true;
        return this;
    }

    @Override
    protected ScheduleSignFactory self() {
        return this;
    }

    @Override
    protected long feeFor(Transaction signedTxn, int numPayerKeys) {
        return 0;
    }

    @Override
    protected void customizeTxn(TransactionBody.Builder txn) {
        var op = ScheduleSignTransactionBody.newBuilder()
                .setScheduleID(id);
        if (!omitSignature) {
            var sigPair = SignaturePair.newBuilder()
                    .setPubKeyPrefix(TxnHandlingScenario.SCHEDULE_SIG_PAIR_PUB_KEY)
                    .setEd25519(TxnHandlingScenario.SCHEDULE_SIG_PAIR_ED25519_SIG);
            if (sameSignerAsPayer) {
                JKey keyList = DEFAULT_PAYER_KT.asJKeyUnchecked();
                byte[] payerKey = keyList.getKeyList().getKeysList().get(0).getEd25519();
                sigPair.setPubKeyPrefix(ByteString.copyFrom(payerKey));
            }
            op.setSigMap(SignatureMap.newBuilder()
                    .addSigPair(sigPair.build())
                    .build());
        }
        txn.setScheduleSign(op);
    }
}
