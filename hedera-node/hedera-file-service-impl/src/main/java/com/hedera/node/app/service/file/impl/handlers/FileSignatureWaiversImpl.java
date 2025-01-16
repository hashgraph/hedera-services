/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl.handlers;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.file.FileSignatureWaivers;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A Singleton implementation of signature waivers needed for transactions in
 * {@link com.hedera.node.app.service.file.FileService}.
 */
@Singleton
public class FileSignatureWaiversImpl implements FileSignatureWaivers {
    private final Authorizer authorizer;

    /**
     * Constructs a {@link FileSignatureWaiversImpl} with the given {@link Authorizer}.
     * @param authorizer account is authorized to perform a specific function
     */
    @Inject
    public FileSignatureWaiversImpl(@NonNull final Authorizer authorizer) {
        this.authorizer = requireNonNull(authorizer);
    }

    @Override
    public boolean areFileUpdateSignaturesWaived(final TransactionBody fileUpdateTxn, final AccountID payer) {
        return authorizer.hasPrivilegedAuthorization(payer, HederaFunctionality.FILE_UPDATE, fileUpdateTxn)
                == SystemPrivilege.AUTHORIZED;
    }

    @Override
    public boolean areFileAppendSignaturesWaived(final TransactionBody fileAppendTxn, final AccountID payer) {
        return authorizer.hasPrivilegedAuthorization(payer, HederaFunctionality.FILE_APPEND, fileAppendTxn)
                == SystemPrivilege.AUTHORIZED;
    }
}
