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
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import edu.umd.cs.findbugs.annotations.NonNull;

import static java.util.Objects.requireNonNull;

public final class HandlersFactory {

    private HandlersFactory() {}

    public static Handlers createHandlers(
            @NonNull final HederaAccountNumbers hederaAccountNumbers,
            @NonNull final HederaFileNumbers hederaFileNumbers
    ) {
        requireNonNull(hederaAccountNumbers);
        requireNonNull(hederaFileNumbers);

        // TODO: Add required parameters to constructors
        return new Handlers(
                new ConsensusCreateTopicHandler(),
                new ConsensusUpdateTopicHandler(),
                new ConsensusDeleteTopicHandler(),
                new ConsensusSubmitMessageHandler(),

                new ContractCreateHandler(),
                new ContractUpdateHandler(),
                new ContractCallHandler(),
                new ContractDeleteHandler(),
                new ContractSystemDeleteHandler(),
                new ContractSystemUndeleteHandler(),
                new EtherumTransactionHandler(),

                new CryptoCreateHandler(),
                new CryptoUpdateHandler(),
                new CryptoTransferHandler(),
                new CryptoDeleteHandler(),
                new CryptoApproveAllowanceHandler(),
                new CryptoDeleteAllowanceHandler(),
                new CryptoAddLiveHashHandler(),
                new CryptoDeleteLiveHashHandler(),

                new FileCreateHandler(),
                new FileUpdateHandler(),
                new FileDeleteHandler(),
                new FileAppendHandler(),
                new FileSystemDeleteHandler(),
                new FileSystemUndeleteHandler(),

                new FreezeHandler(),

                new UncheckedSubmitHandler(),

                new ScheduleCreateHandler(),
                new ScheduleSignHandler(),
                new ScheduleDeleteHandler(),

                new TokenCreateHandler(),
                new TokenUpdateHandler(),
                new TokenMintHandler(),
                new TokenBurnHandler(),
                new TokenDeleteHandler(),
                new TokenAccountWipeHandler(),
                new TokenFreezeAccountHandler(),
                new TokenUnfreezeAccountHandler(),
                new TokenGrantKycToAccountHandler(),
                new TokenRevokeKycFromAccountHandler(),
                new TokenAssociateToAccountHandler(),
                new TokenDissociateFromAccountHandler(),
                new TokenFeeScheduleUpdateHandler(),
                new TokenPauseHandler(),
                new TokenUnpauseHandler(),

                new UtilPrngHandler()
        );
    }
}
