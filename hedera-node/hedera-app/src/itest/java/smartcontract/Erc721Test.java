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

package smartcontract;

import actualintegrationtestsforoncethankgod.DaggerScaffoldingComponent;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.contract.impl.state.ContractSchema;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.swirlds.common.metrics.Metrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Erc721Test {
    private static final FileID ERC721_INITCODE_FILE_ID = new FileID(0, 0, 1001);

    @Mock
    private Metrics metrics;

    private ScaffoldingComponent scaffoldingComponent;

    @BeforeEach
    void setUp() {
        scaffoldingComponent = DaggerScaffoldingComponent.factory().create(metrics);
    }

    @Test
    void erc721OperationsAsExpected() {
        // given:
        addErc721InitcodeToState();
        final var context = scaffoldingComponent.handleContextCreator().apply(synthCreateTxn());
        final var subject = scaffoldingComponent.contractCreateHandler();

        // when:
        subject.handle(context);
        commitStateChanges();

        // then:
        assertContractExists();
    }

    private TransactionBody synthCreateTxn() {
        return TransactionBody.newBuilder()
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .fileID(ERC721_INITCODE_FILE_ID)
                        .build())
                .build();
    }

    private void assertContractExists() {
        // TODO - assert that the contract exists in the state (and has the correct bytecode)
    }

    private void addErc721InitcodeToState() {
        final WritableKVState<FileID, File> files = scaffoldingComponent
                .hederaState()
                .createWritableStates(FileServiceImpl.NAME)
                .get(FileServiceImpl.BLOBS_KEY);
        // TODO - read the ERC-721 initcode from a resource
        files.put(ERC721_INITCODE_FILE_ID, File.DEFAULT);
        commitKvStateChanges(files);
    }

    private void commitStateChanges() {
        final var tokenState = scaffoldingComponent.hederaState().createWritableStates(TokenServiceImpl.NAME);
        commitKvStateChanges(tokenState.get(TokenServiceImpl.ACCOUNTS_KEY));
        commitKvStateChanges(tokenState.get(TokenServiceImpl.ALIASES_KEY));
        commitKvStateChanges(tokenState.get(TokenServiceImpl.TOKEN_RELS_KEY));
        commitKvStateChanges(tokenState.get(TokenServiceImpl.TOKENS_KEY));
        commitKvStateChanges(tokenState.get(TokenServiceImpl.NFTS_KEY));

        final var contractState = scaffoldingComponent.hederaState().createWritableStates(ContractServiceImpl.NAME);
        commitKvStateChanges(contractState.get(ContractSchema.BYTECODE_KEY));
        commitKvStateChanges(contractState.get(ContractSchema.STORAGE_KEY));
    }

    private void commitKvStateChanges(final WritableKVState<?, ?> state) {
        ((WritableKVStateBase<?, ?>) state).commit();
    }
}
