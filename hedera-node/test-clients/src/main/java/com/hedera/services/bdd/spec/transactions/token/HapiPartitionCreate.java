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
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenCreatePartitionDefinitionTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiPartitionCreate extends HapiTxnOp<HapiPartitionCreate> {

    static final Logger log = LogManager.getLogger(HapiPartitionCreate.class);

    private final String token;

    public HapiPartitionCreate(final String token) {
        this.token = token;
    }

    protected HapiPartitionCreate self() {
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenCreatePartition;
    }

    private Optional<String> parent_token_id = Optional.empty();

    private Optional<String> name = Optional.empty();

    public HapiPartitionCreate parent_token_id(final String parent_token_id) {
        this.parent_token_id = Optional.of(parent_token_id);
        return this;
    }

    public HapiPartitionCreate name(final String name) {
        this.name = Optional.of(name);
        return this;
    }

    public HapiPartitionCreate memo(final String memo) {
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
        final TokenCreatePartitionDefinitionTransactionBody opBody = spec.txns()
                .<TokenCreatePartitionDefinitionTransactionBody, TokenCreatePartitionDefinitionTransactionBody.Builder>
                        body(TokenCreatePartitionDefinitionTransactionBody.class, b -> {
                    b.setParentTokenId(id);
                    name.ifPresent(b::setName);
                    memo.ifPresent(s -> b.setMemo(s));
                });
        return b -> b.setTokenCreatePartition(opBody);
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
        var id = TxnUtils.asTokenId(token, spec);
        final var registry = spec.registry();
        parent_token_id.ifPresent(s -> registry.saveTokenId(token, id));
        name.ifPresent(s -> registry.saveName(token, s));
        memo.ifPresent(s -> registry.saveMemo(token, s));
        final TokenID partitionId = lastReceipt.getTokenID();
        registry.savePartitionId(token, partitionId);
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        final MoreObjects.ToStringHelper helper = super.toStringHelper().add("Partition", token);
        Optional.ofNullable(lastReceipt).ifPresent(receipt -> {
            if (receipt.getTokenID().getTokenNum() != 0) {
                helper.add("created", receipt.getTokenID().getTokenNum());
            }
        });
        return helper;
    }
}
