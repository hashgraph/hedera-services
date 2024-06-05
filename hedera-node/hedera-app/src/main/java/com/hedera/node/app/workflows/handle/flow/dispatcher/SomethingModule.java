/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.dispatcher;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.workflows.handle.flow.DueDiligenceInfo;
import com.hedera.node.app.workflows.handle.flow.annotations.UserTxnScope;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import dagger.Module;
import dagger.Provides;
import java.util.Set;

@Module
public interface SomethingModule {
    @Provides
    @UserTxnScope
    static DueDiligenceInfo provideDueDiligenceInfo(PreHandleResult preHandleResult, NodeInfo creator) {
        return new DueDiligenceInfo(
                creator.accountId(),
                preHandleResult.status() != PreHandleResult.Status.SO_FAR_SO_GOOD
                        ? preHandleResult.responseCode()
                        : ResponseCodeEnum.OK);
    }

    @Provides
    @UserTxnScope
    static Set<Key> provideRequiredKeys(PreHandleResult preHandleResult) {
        return preHandleResult.requiredKeys();
    }

    @Provides
    @UserTxnScope
    static Set<Account> provideHollowAccounts(PreHandleResult preHandleResult) {
        return preHandleResult.hollowAccounts();
    }

    @Provides
    @UserTxnScope
    static ResponseCodeEnum provideUserError(PreHandleResult preHandleResult) {
        return preHandleResult.responseCode();
    }
}
