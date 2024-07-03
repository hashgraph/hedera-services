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

package com.hedera.node.app.service.token.impl.util;

import static com.hedera.node.app.hapi.fees.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.node.app.hapi.fees.usage.crypto.CryptoOpsUsage.CREATE_SLOT_MULTIPLIER;
import static com.hedera.node.app.hapi.fees.usage.crypto.entities.CryptoEntitySizes.CRYPTO_ENTITY_SIZES;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BOOL_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.INT_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.LONG_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.getAccountKeyStorageSize;
import static com.hedera.node.app.spi.key.KeyUtils.IMMUTABILITY_SENTINEL_KEY;

import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;

public class CryptoCreateFeeCalculator {

    private CryptoCreateFeeCalculator() {
        // Util class
    }

    public static Fees calculate(TransactionBody body, FeeContext feeContext) {
        CryptoCreateTransactionBody op = body.cryptoCreateAccountOrThrow();

        final var keySize =
                op.hasKey() ? getAccountKeyStorageSize(CommonPbjConverters.fromPbj(op.keyOrElse(Key.DEFAULT))) : 0L;
        final var unlimitedAutoAssociations =
                feeContext.configuration().getConfigData(EntitiesConfig.class).unlimitedAutoAssociationsEnabled();
        final var maxAutoAssociationsSize =
                !unlimitedAutoAssociations && op.maxAutomaticTokenAssociations() > 0 ? INT_SIZE : 0L;
        final var baseSize = op.memo().length() + keySize + maxAutoAssociationsSize;
        final var lifeTime = op.autoRenewPeriodOrElse(Duration.DEFAULT).seconds();
        final var feeCalculator = feeContext.feeCalculatorFactory().feeCalculator(SubType.DEFAULT);
        final var fee = feeCalculator
                .addBytesPerTransaction(baseSize + (2 * LONG_SIZE) + BOOL_SIZE)
                .addRamByteSeconds((CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr() + baseSize) * lifeTime)
                .addNetworkRamByteSeconds(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());
        if (!unlimitedAutoAssociations && op.maxAutomaticTokenAssociations() > 0) {
            fee.addRamByteSeconds(op.maxAutomaticTokenAssociations() * lifeTime * CREATE_SLOT_MULTIPLIER);
        }
        if (IMMUTABILITY_SENTINEL_KEY.equals(op.key())) {
            final var updateTxnBody = TransactionBody.newBuilder()
                    .cryptoUpdateAccount(CryptoUpdateTransactionBody.newBuilder()
                            .key(Key.newBuilder().ecdsaSecp256k1(Bytes.EMPTY).build()))
                    .transactionID(body.transactionID())
                    .build();

            final var lazyCreationFee = feeContext.dispatchComputeFees(updateTxnBody, feeContext.payer());
            return fee.calculate().plus(lazyCreationFee);
        }
        return fee.calculate();
    }
}
