/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl;

import com.hedera.node.app.service.token.CryptoSignatureWaivers;
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
import com.hedera.node.app.service.token.impl.handlers.FinalizeChildRecordHandler;
import com.hedera.node.app.service.token.impl.handlers.FinalizeParentRecordHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenAccountWipeHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenAirdropHandler;
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
import com.hedera.node.app.service.token.impl.handlers.TokenHandlers;
import com.hedera.node.app.service.token.impl.handlers.TokenMintHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenPauseHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenRejectHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenRevokeKycFromAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUnfreezeAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUnpauseHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUpdateHandler;
import com.hedera.node.app.service.token.impl.handlers.staking.StakeRewardCalculator;
import com.hedera.node.app.service.token.impl.handlers.staking.StakeRewardCalculatorImpl;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHandler;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHandlerImpl;
import com.hedera.node.app.service.token.records.ChildRecordFinalizer;
import com.hedera.node.app.service.token.records.ParentRecordFinalizer;
import dagger.Binds;
import dagger.Module;

/**
 * Dagger module of the token service
 */
@Module
public interface TokenServiceInjectionModule {
    /**
     * Binds the {@link CryptoSignatureWaivers} to the {@link CryptoSignatureWaiversImpl}
     * @param impl the implementation of the {@link CryptoSignatureWaivers}
     * @return the bound implementation
     */
    @Binds
    CryptoSignatureWaivers cryptoSignatureWaivers(CryptoSignatureWaiversImpl impl);

    /**
     * Binds the {@link StakingRewardsHandler} to the {@link StakingRewardsHandlerImpl}
     * @param stakingRewardsHandler the implementation of the {@link StakingRewardsHandler}
     * @return the bound implementation
     */
    @Binds
    StakingRewardsHandler stakingRewardHandler(StakingRewardsHandlerImpl stakingRewardsHandler);
    /**
     * Binds the {@link StakeRewardCalculator} to the {@link StakeRewardCalculatorImpl}
     * @param rewardCalculator the implementation of the {@link StakeRewardCalculator}
     * @return the bound implementation
     */
    @Binds
    StakeRewardCalculator stakeRewardCalculator(StakeRewardCalculatorImpl rewardCalculator);
    /**
     * Binds the {@link ParentRecordFinalizer} to the {@link FinalizeParentRecordHandler}
     * @param parentRecordFinalizer the implementation of the {@link ParentRecordFinalizer}
     * @return the bound implementation
     */
    @Binds
    ParentRecordFinalizer parentRecordFinalizer(FinalizeParentRecordHandler parentRecordFinalizer);
    /**
     * Binds the {@link ChildRecordFinalizer} to the {@link FinalizeChildRecordHandler}
     * @param childRecordHandler the implementation of the {@link ChildRecordFinalizer}
     * @return the bound implementation
     */
    @Binds
    ChildRecordFinalizer childRecordFinalizer(FinalizeChildRecordHandler childRecordHandler);
    /**
     * Returns the {@link CryptoAddLiveHashHandler}
     * @return the handler
     */
    CryptoAddLiveHashHandler cryptoAddLiveHashHandler();
    /**
     * Returns the {@link CryptoApproveAllowanceHandler}
     * @return the handler
     */
    CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler();
    /**
     * Returns the {@link CryptoCreateHandler}
     * @return the handler
     */
    CryptoCreateHandler cryptoCreateHandler();
    /**
     * Returns the {@link CryptoDeleteAllowanceHandler}
     * @return the handler
     */
    CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler();
    /**
     * Returns the {@link CryptoDeleteHandler}
     * @return the handler
     */
    CryptoDeleteHandler cryptoDeleteHandler();
    /**
     * Returns the {@link CryptoDeleteLiveHashHandler}
     * @return the handler
     */
    CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler();
    /**
     * Returns the {@link CryptoGetAccountBalanceHandler}
     * @return the handler
     */
    CryptoGetAccountBalanceHandler cryptoGetAccountBalanceHandler();
    /**
     * Returns the {@link CryptoGetAccountInfoHandler}
     * @return the handler
     */
    CryptoGetAccountInfoHandler cryptoGetAccountInfoHandler();
    /**
     * Returns the {@link CryptoGetAccountRecordsHandler}
     * @return the handler
     */
    CryptoGetAccountRecordsHandler cryptoGetAccountRecordsHandler();
    /**
     * Returns the {@link CryptoGetLiveHashHandler}
     * @return the handler
     */
    CryptoGetLiveHashHandler cryptoGetLiveHashHandler();
    /**
     * Returns the {@link CryptoGetStakersHandler}
     * @return the handler
     */
    CryptoGetStakersHandler cryptoGetStakersHandler();
    /**
     * Returns the {@link CryptoTransferHandler}
     * @return the handler
     */
    CryptoTransferHandler cryptoTransferHandler();
    /**
     * Returns the {@link CryptoUpdateHandler}
     * @return the handler
     */
    CryptoUpdateHandler cryptoUpdateHandler();
    /**
     * Returns the {@link TokenAccountWipeHandler}
     * @return the handler
     */
    TokenAccountWipeHandler tokenAccountWipeHandler();
    /**
     * Returns the {@link TokenAssociateToAccountHandler}
     * @return the handler
     */
    TokenAssociateToAccountHandler tokenAssociateToAccountHandler();
    /**
     * Returns the {@link TokenBurnHandler}
     * @return the handler
     */
    TokenBurnHandler tokenBurnHandler();
    /**
     * Returns the {@link TokenCreateHandler}
     * @return the handler
     */
    TokenCreateHandler tokenCreateHandler();
    /**
     * Returns the {@link TokenDeleteHandler}
     * @return the handler
     */
    TokenDeleteHandler tokenDeleteHandler();
    /**
     * Returns the {@link TokenDissociateFromAccountHandler}
     * @return the handler
     */
    TokenDissociateFromAccountHandler tokenDissociateFromAccountHandler();
    /**
     * Returns the {@link TokenFeeScheduleUpdateHandler}
     * @return the handler
     */
    TokenFeeScheduleUpdateHandler tokenFeeScheduleUpdateHandler();
    /**
     * Returns the {@link TokenFreezeAccountHandler}
     * @return the handler
     */
    TokenFreezeAccountHandler tokenFreezeAccountHandler();
    /**
     * Returns the {@link TokenGetAccountNftInfosHandler}
     * @return the handler
     */
    TokenGetAccountNftInfosHandler tokenGetAccountNftInfosHandler();
    /**
     * Returns the {@link TokenGetInfoHandler}
     * @return the handler
     */
    TokenGetInfoHandler tokenGetInfoHandler();
    /**
     * Returns the {@link TokenGetNftInfoHandler}
     * @return the handler
     */
    TokenGetNftInfoHandler tokenGetNftInfoHandler();
    /**
     * Returns the {@link TokenGetNftInfosHandler}
     * @return the handler
     */
    TokenGetNftInfosHandler tokenGetNftInfosHandler();
    /**
     * Returns the {@link TokenGrantKycToAccountHandler}
     * @return the handler
     */
    TokenGrantKycToAccountHandler tokenGrantKycToAccountHandler();
    /**
     * Returns the {@link TokenMintHandler}
     * @return the handler
     */
    TokenMintHandler tokenMintHandler();
    /**
     * Returns the {@link TokenPauseHandler}
     * @return the handler
     */
    TokenPauseHandler tokenPauseHandler();
    /**
     * Returns the {@link TokenRevokeKycFromAccountHandler}
     * @return the handler
     */
    TokenRevokeKycFromAccountHandler tokenRevokeKycFromAccountHandler();
    /**
     * Returns the {@link TokenUnfreezeAccountHandler}
     * @return the handler
     */
    TokenUnfreezeAccountHandler tokenUnfreezeAccountHandler();
    /**
     * Returns the {@link TokenUnpauseHandler}
     * @return the handler
     */
    TokenUnpauseHandler tokenUnpauseHandler();
    /**
     * Returns the {@link TokenUpdateHandler}
     * @return the handler
     */
    TokenUpdateHandler tokenUpdateHandler();
    /**
     * Returns the {@link TokenRejectHandler}
     * @return the handler
     */
    TokenRejectHandler tokenRejectHandler();
    /**
     * Returns the {@link TokenAirdropHandler}
     */
    TokenAirdropHandler tokenAirdropHandler();
    /**
     * Returns the {@link TokenHandlers}
     * @return the handler
     */
    TokenHandlers tokenComponent();
}