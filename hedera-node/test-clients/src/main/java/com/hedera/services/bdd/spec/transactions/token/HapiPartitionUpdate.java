/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.transactions.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.common.base.MoreObjects;
import com.google.protobuf.StringValue;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiPartitionUpdate extends HapiTxnOp<HapiPartitionUpdate> {

    static final Logger log = LogManager.getLogger(HapiPartitionUpdate.class);

    private final String token;

    public HapiPartitionUpdate(final String token) {
        this.token = token;
    }

    protected HapiPartitionUpdate self() {
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenUpdatePartition;
    }

    private Optional<String> newName = Optional.empty();

    public HapiPartitionUpdate name(final String name) {
        this.newName = Optional.of(name);
        return this;
    }

    public HapiPartitionUpdate memo(final String memo) {
        this.memo = Optional.of(memo);
        return this;
    }

    /*  The constant fees will be modified later once new fee calculation model is implemented */
    @Override
    protected long feeFor(final HapiSpec spec, final Transaction txn, final int numPayerKeys) throws Throwable {
        return HapiSuite.ONE_HUNDRED_HBARS;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(final HapiSpec spec) throws Throwable {
        var id = TxnUtils.asTokenId(token, spec);
        final TokenUpdatePartitionTransactionBody opBody = spec.txns()
                .<TokenUpdatePartitionTransactionBody, TokenUpdatePartitionTransactionBody.Builder>body(
                        TokenUpdatePartitionTransactionBody.class, b -> {
                            b.setToken(id);
                            newName.ifPresent(b::setName);
                            memo.ifPresent(s -> b.setMemo(
                                    StringValue.newBuilder().setValue(s).build()));
                        });
        return b -> b.setTokenUpdatePartition(opBody);
    }

    protected List<Function<HapiSpec, Key>> defaultSigners(final Function<HapiSpec, String> effectivePayer) {
        final List<Function<HapiSpec, Key>> signers = new ArrayList<>();
        signers.add(spec -> spec.registry().getKey(effectivePayer.apply(spec)));
        signers.add(spec -> {
            try {
                return spec.registry().getPartitionKey(token);
            } catch (Exception ignore) {
                return Key.getDefaultInstance();
            }
        });
        return signers;
    }

    @Override
    protected void updateStateOf(final HapiSpec spec) {
        if (actualStatus != SUCCESS) {
            return;
        }
        final var registry = spec.registry();
        newName.ifPresent(s -> registry.saveName(token, s));
        memo.ifPresent(s -> registry.saveMemo(token, s));
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper = super.toStringHelper().add("Partition", token);
        return helper;
    }
}
