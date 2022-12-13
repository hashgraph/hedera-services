package com.hedera.node.app.workflows.dispatcher;

import com.hedera.node.app.service.admin.impl.handlers.FreezeHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusDeleteTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusUpdateTopicHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractCreateHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractDeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractSystemDeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractSystemUndeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractUpdateHandler;
import com.hedera.node.app.service.contract.impl.handlers.EtherumTransactionHandler;
import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.service.file.impl.handlers.FileCreateHandler;
import com.hedera.node.app.service.file.impl.handlers.FileDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileUpdateHandler;
import com.hedera.node.app.service.network.impl.handlers.UncheckedSubmitHandler;
import com.hedera.node.app.service.scheduled.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.service.scheduled.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.service.scheduled.impl.handlers.ScheduleSignHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoAddLiveHashHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoApproveAllowanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoCreateHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteAllowanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteLiveHashHandler;
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
import com.hedera.node.app.service.token.impl.handlers.TokenGrantKycToAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenMintHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenPauseHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenRevokeKycFromAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUnfreezeAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUnpauseHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUpdateHandler;
import com.hedera.node.app.service.util.impl.handlers.UtilPrngHandler;
import edu.umd.cs.findbugs.annotations.NonNull;

public record Handlers(
        @NonNull ConsensusCreateTopicHandler consensusCreateTopicHandler,
        @NonNull ConsensusUpdateTopicHandler consensusUpdateTopicHandler,
        @NonNull ConsensusDeleteTopicHandler consensusDeleteTopicHandler,
        @NonNull ConsensusSubmitMessageHandler consensusSubmitMessageHandler,

        @NonNull ContractCreateHandler contractCreateHandler,
        @NonNull ContractUpdateHandler contractUpdateHandler,
        @NonNull ContractCallHandler contractCallHandler,
        @NonNull ContractDeleteHandler contractDeleteHandler,
        @NonNull ContractSystemDeleteHandler contractSystemDeleteHandler,
        @NonNull ContractSystemUndeleteHandler contractSystemUndeleteHandler,
        @NonNull EtherumTransactionHandler etherumTransactionHandler,

        @NonNull CryptoCreateHandler cryptoCreateHandler,
        @NonNull CryptoUpdateHandler cryptoUpdateHandler,
        @NonNull CryptoTransferHandler cryptoTransferHandler,
        @NonNull CryptoDeleteHandler cryptoDeleteHandler,
        @NonNull CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler,
        @NonNull CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler,
        @NonNull CryptoAddLiveHashHandler cryptoAddLiveHashHandler,
        @NonNull CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler,

        @NonNull FileCreateHandler fileCreateHandler,
        @NonNull FileUpdateHandler fileUpdateHandler,
        @NonNull FileDeleteHandler fileDeleteHandler,
        @NonNull FileAppendHandler fileAppendHandler,
        @NonNull FileSystemDeleteHandler fileSystemDeleteHandler,
        @NonNull FileSystemUndeleteHandler fileSystemUndeleteHandler,

        @NonNull FreezeHandler freezeHandler,

        @NonNull UncheckedSubmitHandler uncheckedSubmitHandler,

        @NonNull ScheduleCreateHandler scheduleCreateHandler,
        @NonNull ScheduleSignHandler scheduleSignHandler,
        @NonNull ScheduleDeleteHandler scheduleDeleteHandler,

        @NonNull TokenCreateHandler tokenCreateHandler,
        @NonNull TokenUpdateHandler tokenUpdateHandler,
        @NonNull TokenMintHandler tokenMintHandler,
        @NonNull TokenBurnHandler tokenBurnHandler,
        @NonNull TokenDeleteHandler tokenDeleteHandler,
        @NonNull TokenAccountWipeHandler tokenAccountWipeHandler,
        @NonNull TokenFreezeAccountHandler tokenFreezeAccountHandler,
        @NonNull TokenUnfreezeAccountHandler tokenUnfreezeAccountHandler,
        @NonNull TokenGrantKycToAccountHandler tokenGrantKycToAccountHandler,
        @NonNull TokenRevokeKycFromAccountHandler tokenRevokeKycFromAccountHandler,
        @NonNull TokenAssociateToAccountHandler tokenAssociateToAccountHandler,
        @NonNull TokenDissociateFromAccountHandler tokenDissociateFromAccountHandler,
        @NonNull TokenFeeScheduleUpdateHandler tokenFeeScheduleUpdateHandler,
        @NonNull TokenPauseHandler tokenPauseHandler,
        @NonNull TokenUnpauseHandler tokenUnpauseHandler,

        @NonNull UtilPrngHandler utilPrngHandler) {}
