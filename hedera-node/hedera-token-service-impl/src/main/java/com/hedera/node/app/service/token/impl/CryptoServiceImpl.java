/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import com.hedera.node.app.service.token.CryptoService;
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
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/** An implementation of the {@link CryptoService} interface. */
public final class CryptoServiceImpl implements CryptoService {

    private final CryptoAddLiveHashHandler cryptoAddLiveHashHandler;

    private final CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler;

    private final CryptoCreateHandler cryptoCreateHandler;

    private final CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler;

    private final CryptoDeleteHandler cryptoDeleteHandler;

    private final CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler;

    private final CryptoTransferHandler cryptoTransferHandler;

    private final CryptoUpdateHandler cryptoUpdateHandler;

    private final CryptoGetAccountBalanceHandler cryptoGetAccountBalanceHandler;

    private final CryptoGetAccountInfoHandler cryptoGetAccountInfoHandler;

    private final CryptoGetAccountRecordsHandler cryptoGetAccountRecordsHandler;

    private final CryptoGetLiveHashHandler cryptoGetLiveHashHandler;

    private final CryptoGetStakersHandler cryptoGetStakersHandler;

    /**
     * Constructs a {@link CryptoServiceImpl} instance.
     */
    public CryptoServiceImpl() {
        this.cryptoAddLiveHashHandler = new CryptoAddLiveHashHandler();
        this.cryptoApproveAllowanceHandler = new CryptoApproveAllowanceHandler();
        this.cryptoCreateHandler = new CryptoCreateHandler();
        this.cryptoDeleteAllowanceHandler = new CryptoDeleteAllowanceHandler();
        this.cryptoDeleteHandler = new CryptoDeleteHandler();
        this.cryptoDeleteLiveHashHandler = new CryptoDeleteLiveHashHandler();
        this.cryptoTransferHandler = new CryptoTransferHandler();
        this.cryptoUpdateHandler = new CryptoUpdateHandler();
        this.cryptoGetAccountBalanceHandler = new CryptoGetAccountBalanceHandler();
        this.cryptoGetAccountInfoHandler = new CryptoGetAccountInfoHandler();
        this.cryptoGetAccountRecordsHandler = new CryptoGetAccountRecordsHandler();
        this.cryptoGetLiveHashHandler = new CryptoGetLiveHashHandler();
        this.cryptoGetStakersHandler = new CryptoGetStakersHandler();
    }

    /**
     * Returns the {@link CryptoAddLiveHashHandler} instance.
     *
     * @return the {@link CryptoAddLiveHashHandler} instance.
     */
    @NonNull
    public CryptoAddLiveHashHandler getCryptoAddLiveHashHandler() {
        return cryptoAddLiveHashHandler;
    }

    /**
     * Returns the {@link CryptoApproveAllowanceHandler} instance.
     *
     * @return the {@link CryptoApproveAllowanceHandler} instance.
     */
    @NonNull
    public CryptoApproveAllowanceHandler getCryptoApproveAllowanceHandler() {
        return cryptoApproveAllowanceHandler;
    }

    /**
     * Returns the {@link CryptoCreateHandler} instance.
     *
     * @return the {@link CryptoCreateHandler} instance.
     */
    @NonNull
    public CryptoCreateHandler getCryptoCreateHandler() {
        return cryptoCreateHandler;
    }

    /**
     * Returns the {@link CryptoDeleteAllowanceHandler} instance.
     *
     * @return the {@link CryptoDeleteAllowanceHandler} instance.
     */
    @NonNull
    public CryptoDeleteAllowanceHandler getCryptoDeleteAllowanceHandler() {
        return cryptoDeleteAllowanceHandler;
    }

    /**
     * Returns the {@link CryptoDeleteHandler} instance.
     *
     * @return the {@link CryptoDeleteHandler} instance.
     */
    @NonNull
    public CryptoDeleteHandler getCryptoDeleteHandler() {
        return cryptoDeleteHandler;
    }

    /**
     * Returns the {@link CryptoDeleteLiveHashHandler} instance.
     *
     * @return the {@link CryptoDeleteLiveHashHandler} instance.
     */
    @NonNull
    public CryptoDeleteLiveHashHandler getCryptoDeleteLiveHashHandler() {
        return cryptoDeleteLiveHashHandler;
    }

    /**
     * Returns the {@link CryptoTransferHandler} instance.
     *
     * @return the {@link CryptoTransferHandler} instance.
     */
    @NonNull
    public CryptoTransferHandler getCryptoTransferHandler() {
        return cryptoTransferHandler;
    }

    /**
     * Returns the {@link CryptoUpdateHandler} instance.
     *
     * @return the {@link CryptoUpdateHandler} instance.
     */
    @NonNull
    public CryptoUpdateHandler getCryptoUpdateHandler() {
        return cryptoUpdateHandler;
    }

    /**
     * Returns the {@link CryptoGetAccountBalanceHandler} instance.
     *
     * @return the {@link CryptoGetAccountBalanceHandler} instance.
     */
    @NonNull
    public CryptoGetAccountBalanceHandler getCryptoGetAccountBalanceHandler() {
        return cryptoGetAccountBalanceHandler;
    }

    /**
     * Returns the {@link CryptoGetAccountInfoHandler} instance.
     *
     * @return the {@link CryptoGetAccountInfoHandler} instance.
     */
    @NonNull
    public CryptoGetAccountInfoHandler getCryptoGetAccountInfoHandler() {
        return cryptoGetAccountInfoHandler;
    }

    /**
     * Returns the {@link CryptoGetAccountRecordsHandler} instance.
     *
     * @return the {@link CryptoGetAccountRecordsHandler} instance.
     */
    @NonNull
    public CryptoGetAccountRecordsHandler getCryptoGetAccountRecordsHandler() {
        return cryptoGetAccountRecordsHandler;
    }

    /**
     * Returns the {@link CryptoGetLiveHashHandler} instance.
     *
     * @return the {@link CryptoGetLiveHashHandler} instance.
     */
    @NonNull
    public CryptoGetLiveHashHandler getCryptoGetLiveHashHandler() {
        return cryptoGetLiveHashHandler;
    }

    /**
     * Returns the {@link CryptoGetStakersHandler} instance.
     *
     * @return the {@link CryptoGetStakersHandler} instance.
     */
    @NonNull
    public CryptoGetStakersHandler getCryptoGetStakersHandler() {
        return cryptoGetStakersHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandler() {
        return Set.of(cryptoAddLiveHashHandler, cryptoApproveAllowanceHandler, cryptoCreateHandler,
                cryptoDeleteAllowanceHandler, cryptoDeleteHandler, cryptoDeleteLiveHashHandler,
                cryptoTransferHandler, cryptoUpdateHandler);
    }

    @NonNull
    @Override
    public Set<QueryHandler> getQueryHandler() {
        return Set.of(cryptoGetAccountBalanceHandler, cryptoGetAccountInfoHandler,
                cryptoGetAccountRecordsHandler,
                cryptoGetLiveHashHandler, cryptoGetStakersHandler);
    }
}
