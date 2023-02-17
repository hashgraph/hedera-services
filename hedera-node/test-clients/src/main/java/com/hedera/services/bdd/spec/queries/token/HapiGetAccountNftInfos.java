/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.queries.token;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.TokenGetAccountNftInfosQuery;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.Assertions;

public class HapiGetAccountNftInfos extends HapiQueryOp<HapiGetAccountNftInfos> {
    private static final Logger log = LogManager.getLogger(HapiGetAccountNftInfos.class);

    private String account;
    private long start;
    private long end;

    public HapiGetAccountNftInfos(String account, long start, long end) {
        this.account = account;
        this.start = start;
        this.end = end;
    }

    private Optional<List<HapiTokenNftInfo>> expectedNfts = Optional.empty();

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenGetNftInfo;
    }

    @Override
    protected HapiGetAccountNftInfos self() {
        return this;
    }

    @Override
    protected void assertExpectationsGiven(HapiSpec spec) throws Throwable {
        var actualInfo = response.getTokenGetAccountNftInfos().getNftsList();
        var expectedInfo = new ArrayList<TokenNftInfo>();
        expectedNfts.ifPresent(nfts -> {
            for (HapiTokenNftInfo nftInfo : nfts) {
                var expectedNftElement = TokenNftInfo.newBuilder();
                var expectedNftId = NftID.newBuilder();

                nftInfo.getExpectedTokenID().ifPresent(e -> {
                    expectedNftId.setTokenID(TxnUtils.asTokenId(e, spec));
                    expectedNftElement.setCreationTime(spec.registry().getCreationTime(e));
                });
                nftInfo.getExpectedSerialNum().ifPresent(expectedNftId::setSerialNumber);

                expectedNftElement.setNftID(expectedNftId.build());
                nftInfo.getExpectedAccountID().ifPresent(e -> expectedNftElement.setAccountID(TxnUtils.asId(e, spec)));
                nftInfo.getExpectedMetadata().ifPresent(expectedNftElement::setMetadata);

                nftInfo.getExpectedLedgerID().ifPresent(id -> expectedNftElement.setLedgerId(rationalize(id)));

                var completedNft = expectedNftElement.build();
                expectedInfo.add(completedNft);
            }
            Assertions.assertEquals(actualInfo, expectedInfo);
        });
    }

    @Override
    protected void submitWith(HapiSpec spec, Transaction payment) {
        Query query = getAccountNftInfosQuery(spec, payment, false);
        response = spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls).getAccountNftInfos(query);
        if (verboseLoggingOn) {
            StringBuilder information = new StringBuilder("Nft information for '" + account + "': \n");
            List<TokenNftInfo> nfts = response.getTokenGetAccountNftInfos().getNftsList();
            information.append(Strings.join(nfts, '\n'));
            log.info(information.toString());
        }
    }

    @Override
    protected long lookupCostWith(HapiSpec spec, Transaction payment) throws Throwable {
        Query query = getAccountNftInfosQuery(spec, payment, true);
        Response response =
                spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls).getAccountNftInfos(query);
        return costFrom(response);
    }

    private Query getAccountNftInfosQuery(HapiSpec spec, Transaction payment, boolean costOnly) {
        var id = TxnUtils.asId(account, spec);
        TokenGetAccountNftInfosQuery getAccountNftInfosQuery = TokenGetAccountNftInfosQuery.newBuilder()
                .setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
                .setAccountID(id)
                .setStart(start)
                .setEnd(end)
                .build();
        return Query.newBuilder()
                .setTokenGetAccountNftInfos(getAccountNftInfosQuery)
                .build();
    }

    @Override
    protected boolean needsPayment() {
        return true;
    }

    public HapiGetAccountNftInfos hasNfts(HapiTokenNftInfo... nfts) {
        this.expectedNfts = Optional.of(Arrays.asList(nfts));
        return this;
    }
}
