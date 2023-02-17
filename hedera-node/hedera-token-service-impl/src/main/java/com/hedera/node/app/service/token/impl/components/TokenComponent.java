/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.token.impl.components;

import com.hedera.node.app.service.token.impl.handlers.CryptoAddLiveHashHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoApproveAllowanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoCreateHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteAllowanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteLiveHashHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetAccountBalanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetAccountInfoHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetAccountRecordsHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetLiveHashHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetStakersHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoUpdateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenAccountWipeHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenAssociateToAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenBurnHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenCreateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenDeleteHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenDissociateFromAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenFeeScheduleUpdateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenFreezeAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGetAccountNftInfosHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGetInfoHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGetNftInfoHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGetNftInfosHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGrantKycToAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenMintHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenPauseHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenRevokeKycFromAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUnfreezeAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUnpauseHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUpdateHandler;
import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component
public interface TokenComponent {
    @Component.Factory
    interface Factory {
        TokenComponent create();
    }

    CryptoAddLiveHashHandler cryptoAddLiveHashHandler();

    CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler();

    CryptoCreateHandler cryptoCreateHandler();

    CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler();

    CryptoDeleteHandler cryptoDeleteHandler();

    CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler();

    CryptoGetAccountBalanceHandler cryptoGetAccountBalanceHandler();

    CryptoGetAccountInfoHandler cryptoGetAccountInfoHandler();

    CryptoGetAccountRecordsHandler cryptoGetAccountRecordsHandler();

    CryptoGetLiveHashHandler cryptoGetLiveHashHandler();

    CryptoGetStakersHandler cryptoGetStakersHandler();

    CryptoTransferHandler cryptoTransferHandler();

    CryptoUpdateHandler cryptoUpdateHandler();

    TokenAccountWipeHandler tokenAccountWipeHandler();

    TokenAssociateToAccountHandler tokenAssociateToAccountHandler();

    TokenBurnHandler tokenBurnHandler();

    TokenCreateHandler tokenCreateHandler();

    TokenDeleteHandler tokenDeleteHandler();

    TokenDissociateFromAccountHandler tokenDissociateFromAccountHandler();

    TokenFeeScheduleUpdateHandler tokenFeeScheduleUpdateHandler();

    TokenFreezeAccountHandler tokenFreezeAccountHandler();

    TokenGetAccountNftInfosHandler tokenGetAccountNftInfosHandler();

    TokenGetInfoHandler tokenGetInfoHandler();

    TokenGetNftInfoHandler tokenGetNftInfoHandler();

    TokenGetNftInfosHandler tokenGetNftInfosHandler();

    TokenGrantKycToAccountHandler tokenGrantKycToAccountHandler();

    TokenMintHandler tokenMintHandler();

    TokenPauseHandler tokenPauseHandler();

    TokenRevokeKycFromAccountHandler tokenRevokeKycFromAccountHandler();

    TokenUnfreezeAccountHandler tokenUnfreezeAccountHandler();

    TokenUnpauseHandler tokenUnpauseHandler();

    TokenUpdateHandler tokenUpdateHandler();
}
