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
package com.hedera.services.bdd.spec.utilops.checks;

import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.TokenGetNftInfosQuery;
import org.junit.jupiter.api.Assertions;

public class VerifyGetTokenNftInfosNotSupported extends UtilOp {
    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        TokenGetNftInfosQuery.Builder op =
                TokenGetNftInfosQuery.newBuilder().setTokenID(asToken("0.0.1001"));
        Query query = Query.newBuilder().setTokenGetNftInfos(op).build();
        Response response =
                spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls).getTokenNftInfos(query);
        Assertions.assertEquals(
                NOT_SUPPORTED,
                response.getTokenGetNftInfos().getHeader().getNodeTransactionPrecheckCode());
        return false;
    }
}
