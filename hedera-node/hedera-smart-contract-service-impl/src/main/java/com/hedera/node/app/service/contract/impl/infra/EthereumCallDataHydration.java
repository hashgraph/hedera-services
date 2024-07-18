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

package com.hedera.node.app.service.contract.impl.infra;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_FILE_EMPTY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.populateEthTxData;
import static com.hedera.node.app.service.contract.impl.hevm.HydratedEthTxData.failureFrom;
import static com.hedera.node.app.service.contract.impl.hevm.HydratedEthTxData.successFrom;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.removeIfAnyLeading0x;

import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.service.contract.impl.hevm.HydratedEthTxData;
import com.hedera.node.app.service.file.ReadableFileStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.Hex;

/**
 * A utility that hydrates {@link EthTxData} from a {@link EthereumTransactionBody} and a {@link ReadableFileStore}.
 */
@Singleton
public class EthereumCallDataHydration {
    @Inject
    public EthereumCallDataHydration() {
        // Dagger2
    }

    /**
     * If successful, returns the hydrated {@link EthTxData} for the given {@link EthereumTransactionBody} and
     * {@link ReadableFileStore}. Otherwise, returns a {@link HydratedEthTxData} with a non-OK status and
     * null {@link EthTxData}.
     *
     * @param body the {@link EthereumTransactionBody} to hydrate
     * @param fileStore the {@link ReadableFileStore} to hydrate from (if needed)
     * @param firstUserEntityNum the first user entity number
     * @return the final {@link EthTxData}
     */
    public HydratedEthTxData tryToHydrate(
            @NonNull final EthereumTransactionBody body,
            @NonNull final ReadableFileStore fileStore,
            final long firstUserEntityNum) {
        final var ethTxData = populateEthTxData(body.ethereumData().toByteArray());
        if (ethTxData == null) {
            return failureFrom(INVALID_ETHEREUM_TRANSACTION);
        }
        if (requiresHydration(body, ethTxData)) {
            final var callDataFileId = body.callDataOrThrow();
            if (callDataFileId.fileNum() < firstUserEntityNum) {
                return failureFrom(INVALID_FILE_ID);
            }
            final var callDataFile = fileStore.getFileLeaf(callDataFileId);
            if (callDataFile == null) {
                return failureFrom(INVALID_FILE_ID);
            }
            if (callDataFile.deleted()) {
                return failureFrom(FILE_DELETED);
            }

            // Bytes.fromHex() doesn't appreciate a leading '0x' but we supported it in mono-service
            final byte[] callData;
            try {
                callData = Hex.decode(removeIfAnyLeading0x(callDataFile.contents()));
            } catch (final DecoderException ignore) {
                return failureFrom(INVALID_FILE_ID);
            }

            if (callData.length == 0) {
                return failureFrom(CONTRACT_FILE_EMPTY);
            }

            return successFrom(ethTxData.replaceCallData(callData));
        } else {
            return successFrom(ethTxData);
        }
    }

    private static boolean requiresHydration(
            @NonNull final EthereumTransactionBody body, @NonNull final EthTxData ethTxData) {
        return body.hasCallData() && !ethTxData.hasCallData();
    }
}
