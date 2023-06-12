/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.token.impl.validators;

import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.HederaConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

/**
 * Provides validation for fields like memo, which is common in many transactions.
 */
public class CommonValidator {
    private final ConfigProvider configProvider;
    @Inject
    public CommonValidator(@NonNull final ConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    public void validateMemo(@NonNull final String memo){
        final var memoBytes = memo.getBytes(StandardCharsets.UTF_8);
        final var hederaConfig = configProvider.getConfiguration().getConfigData(HederaConfig.class);
        validateTrue(memo.length() <= hederaConfig.transactionMaxMemoUtf8Bytes(), MEMO_TOO_LONG);
        validateFalse(contains(memoBytes, (byte) 0), INVALID_ZERO_BYTE_IN_STRING);
    }

    private static boolean contains(byte[] a, byte val)
    {
        for (int i = 0; i < a.length; ++i)
        {
            if (a[i] == val)
            {
                return true;
            }
        }
        return false;
    }
}
