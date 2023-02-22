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
import com.hedera.node.app.service.token.handlers.CryptoApproveAllowanceHandlerI;
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
import com.hedera.node.app.spi.workflows.FreeQueryHandler;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;

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

    @NonNull
    @Override
    public TransactionHandler getCryptoAddLiveHashHandler() {
        return cryptoAddLiveHashHandler;
    }

    @NonNull
    @Override
    public CryptoApproveAllowanceHandlerI getCryptoApproveAllowanceHandler() {
        return cryptoApproveAllowanceHandler;
    }

    @NonNull
    @Override
    public TransactionHandler getCryptoCreateHandler() {
        return cryptoCreateHandler;
    }

    @NonNull
    @Override
    public TransactionHandler getCryptoDeleteAllowanceHandler() {
        return cryptoDeleteAllowanceHandler;
    }

    @NonNull
    @Override
    public TransactionHandler getCryptoDeleteHandler() {
        return cryptoDeleteHandler;
    }

    @NonNull
    @Override
    public TransactionHandler getCryptoDeleteLiveHashHandler() {
        return cryptoDeleteLiveHashHandler;
    }

    @NonNull
    @Override
    public TransactionHandler getCryptoTransferHandler() {
        return cryptoTransferHandler;
    }

    @NonNull
    @Override
    public TransactionHandler getCryptoUpdateHandler() {
        return cryptoUpdateHandler;
    }

    @NonNull
    @Override
    public FreeQueryHandler getCryptoGetAccountBalanceHandler() {
        return cryptoGetAccountBalanceHandler;
    }

    @NonNull
    @Override
    public PaidQueryHandler getCryptoGetAccountInfoHandler() {
        return cryptoGetAccountInfoHandler;
    }

    @NonNull
    @Override
    public PaidQueryHandler getCryptoGetAccountRecordsHandler() {
        return cryptoGetAccountRecordsHandler;
    }

    @NonNull
    @Override
    public FreeQueryHandler getCryptoGetLiveHashHandler() {
        return cryptoGetLiveHashHandler;
    }

    @NonNull
    @Override
    public FreeQueryHandler getCryptoGetStakersHandler() {
        return cryptoGetStakersHandler;
    }
}
