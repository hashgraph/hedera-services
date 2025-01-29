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

package com.hedera.node.app.service.file.impl.test.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.file.impl.handlers.FileSignatureWaiversImpl;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FileSignatureWaiversImplTest {

    @Mock
    private Authorizer authorizer;

    @Mock
    private TransactionBody fileUpdateTxn;

    @Mock
    private AccountID payer;

    private FileSignatureWaiversImpl fileSignatureWaivers;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        fileSignatureWaivers = new FileSignatureWaiversImpl(authorizer);
    }

    @Test
    @DisplayName("Signatures are waived when payer has privileged authorization for file update")
    public void signaturesAreWaivedWhenPayerHasPrivilegedAuthorizationForFileUpdate() {
        when(authorizer.hasPrivilegedAuthorization(payer, HederaFunctionality.FILE_UPDATE, fileUpdateTxn))
                .thenReturn(SystemPrivilege.AUTHORIZED);

        assertThat(fileSignatureWaivers.areFileUpdateSignaturesWaived(fileUpdateTxn, payer))
                .isTrue();
    }

    @Test
    @DisplayName("Signatures are not waived when payer does not have privileged authorization for file update")
    public void signaturesAreNotWaivedWhenPayerDoesNotHavePrivilegedAuthorizationForFileUpdate() {
        when(authorizer.hasPrivilegedAuthorization(payer, HederaFunctionality.FILE_UPDATE, fileUpdateTxn))
                .thenReturn(SystemPrivilege.UNAUTHORIZED);

        assertThat(fileSignatureWaivers.areFileUpdateSignaturesWaived(fileUpdateTxn, payer))
                .isFalse();
    }

    @Test
    @DisplayName("Signatures are waived when payer has privileged authorization")
    public void signaturesAreWaivedWhenPayerHasPrivilegedAuthorizationForFileAppend() {
        when(authorizer.hasPrivilegedAuthorization(payer, HederaFunctionality.FILE_APPEND, fileUpdateTxn))
                .thenReturn(SystemPrivilege.AUTHORIZED);

        assertThat(fileSignatureWaivers.areFileAppendSignaturesWaived(fileUpdateTxn, payer))
                .isTrue();
    }

    @Test
    @DisplayName("Signatures are not waived when payer does not have privileged authorization")
    public void signaturesAreNotWaivedWhenPayerDoesNotHavePrivilegedAuthorizationForFileAppend() {
        when(authorizer.hasPrivilegedAuthorization(payer, HederaFunctionality.FILE_APPEND, fileUpdateTxn))
                .thenReturn(SystemPrivilege.UNAUTHORIZED);

        assertThat(fileSignatureWaivers.areFileAppendSignaturesWaived(fileUpdateTxn, payer))
                .isFalse();
    }
}
