/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.api.implementation.workflows.prehandle.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.file.FilePreTransactionHandler;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.TokenPreTransactionHandler;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.services.api.implementation.ServicesAccessor;
import com.hedera.services.api.implementation.state.HederaState;
import com.hedera.services.api.implementation.workflows.prehandle.PreHandleDispatcher;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.NotImplementedException;

/** Default implementation of {@link PreHandleDispatcher} */
public final class PreHandleDispatcherImpl implements PreHandleDispatcher {

    private final CryptoPreTransactionHandler cryptoHandler;
    private final FilePreTransactionHandler fileHandler;
    private final TokenPreTransactionHandler tokenHandler;

    /**
     * Constructor of {@code PreHandleDispatcherImpl}
     *
     * @param hederaState the {@link HederaState} this dispatcher is bound to
     * @param services the {@link ServicesAccessor} with all available services
     * @throws NullPointerException if one of the parameters is {@code null}
     */
    public PreHandleDispatcherImpl(final HederaState hederaState, final ServicesAccessor services) {
        final var cryptoStates = hederaState.getServiceStates(CryptoService.class);
        cryptoHandler = services.cryptoService().createPreTransactionHandler(cryptoStates);

        final var fileStates = hederaState.getServiceStates(FileService.class);
        fileHandler = services.fileService().createPreTransactionHandler(fileStates);

        final var tokenStates = hederaState.getServiceStates(TokenService.class);
        tokenHandler = services.tokenService().createPreTransactionHandler(tokenStates);
    }

    @Override
    public TransactionMetadata dispatch(final TransactionBody transactionBody) {
        requireNonNull(transactionBody);
        return switch (transactionBody.getDataCase()) {
            case CRYPTOCREATEACCOUNT -> cryptoHandler.preHandleCryptoCreate(transactionBody);
            case CRYPTOUPDATEACCOUNT -> cryptoHandler.preHandleUpdateAccount(transactionBody);
            case CRYPTOTRANSFER -> cryptoHandler.preHandleCryptoTransfer(transactionBody);
            case CRYPTODELETE -> cryptoHandler.preHandleCryptoDelete(transactionBody);
            case CRYPTOAPPROVEALLOWANCE -> cryptoHandler.preHandleApproveAllowances(
                    transactionBody);
            case CRYPTODELETEALLOWANCE -> cryptoHandler.preHandleDeleteAllowances(transactionBody);
            case CRYPTOADDLIVEHASH -> cryptoHandler.preHandleAddLiveHash(transactionBody);
            case CRYPTODELETELIVEHASH -> cryptoHandler.preHandleDeleteLiveHash(transactionBody);

            case FILECREATE -> fileHandler.preHandleCreateFile(transactionBody);
            case FILEUPDATE -> fileHandler.preHandleUpdateFile(transactionBody);
            case FILEDELETE -> fileHandler.preHandleDeleteFile(transactionBody);
            case FILEAPPEND -> fileHandler.preHandleAppendContent(transactionBody);
            case SYSTEMDELETE -> fileHandler.preHandleSystemDelete(transactionBody);
            case SYSTEMUNDELETE -> fileHandler.preHandleSystemUndelete(transactionBody);

            case TOKENCREATION -> tokenHandler.preHandleCreateToken(transactionBody);
            case TOKENUPDATE -> tokenHandler.preHandleUpdateToken(transactionBody);
            case TOKENMINT -> tokenHandler.preHandleMintToken(transactionBody);
            case TOKENBURN -> tokenHandler.preHandleBurnToken(transactionBody);
            case TOKENDELETION -> tokenHandler.preHandleDeleteToken(transactionBody);
            case TOKENWIPE -> tokenHandler.preHandleWipeTokenAccount(transactionBody);
            case TOKENFREEZE -> tokenHandler.preHandleFreezeTokenAccount(transactionBody);
            case TOKENUNFREEZE -> tokenHandler.preHandleUnfreezeTokenAccount(transactionBody);
            case TOKENGRANTKYC -> tokenHandler.preHandleGrantKycToTokenAccount(transactionBody);
            case TOKENREVOKEKYC -> tokenHandler.preHandleRevokeKycFromTokenAccount(transactionBody);
            case TOKENASSOCIATE -> tokenHandler.preHandleAssociateTokens(transactionBody);
            case TOKENDISSOCIATE -> tokenHandler.preHandleDissociateTokens(transactionBody);
            case TOKEN_FEE_SCHEDULE_UPDATE -> tokenHandler.preHandleUpdateTokenFeeSchedule(
                    transactionBody);
            case TOKEN_PAUSE -> tokenHandler.preHandlePauseToken(transactionBody);
            case TOKEN_UNPAUSE -> tokenHandler.preHandleUnpauseToken(transactionBody);

            default -> throw new NotImplementedException(
                    transactionBody.getDataCase() + " has not been implemented yet");
        };
    }
}
