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

package com.hedera.node.app.workflows.handle.validation;

import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.BAD_ENCODING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ENTITIES_MAX_LIFETIME;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * An implementation of {@link AttributeValidator} that encapsulates the current policies for
 * validating attributes of entities, <i>without</i> any use of {@code mono-service} code.
 *
 * @deprecated Use {@link AttributeValidatorImpl} instead.
 */
@Deprecated(forRemoval = true)
@Singleton
public class StandardizedAttributeValidator implements AttributeValidator {
    private final long maxEntityLifetime;
    private final LongSupplier consensusSecondNow;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public StandardizedAttributeValidator(
            @NonNull final LongSupplier consensusSecondNow,
            @NonNull @CompositeProps final PropertySource properties,
            @NonNull final GlobalDynamicProperties dynamicProperties) {
        this.maxEntityLifetime = properties.getLongProperty(ENTITIES_MAX_LIFETIME);
        this.consensusSecondNow = consensusSecondNow;
        this.dynamicProperties = requireNonNull(dynamicProperties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateKey(@NonNull final Key key) {
        validateKeyAtLevel(key, 1);

        // If key is mappable in all levels, validate the key is valid
        if (!isValid(key)) {
            throw new HandleException(BAD_ENCODING);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateExpiry(long expiry) {
        final var now = consensusSecondNow.getAsLong();
        final var expiryGivenMaxLifetime = now + maxEntityLifetime;
        validateTrue(expiry > now && expiry <= expiryGivenMaxLifetime, INVALID_EXPIRATION_TIME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAutoRenewPeriod(long autoRenewPeriod) {
        validateTrue(
                autoRenewPeriod >= dynamicProperties.minAutoRenewDuration()
                        && autoRenewPeriod <= dynamicProperties.maxAutoRenewDuration(),
                AUTORENEW_DURATION_NOT_IN_RANGE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateMemo(@Nullable final String memo) {
        if (memo == null) {
            return;
        }
        final var raw = memo.getBytes(StandardCharsets.UTF_8);
        if (raw.length > dynamicProperties.maxMemoUtf8Bytes()) {
            throw new HandleException(MEMO_TOO_LONG);
        } else if (contains(raw, (byte) 0)) {
            throw new HandleException(INVALID_ZERO_BYTE_IN_STRING);
        }
    }

    private void validateKeyAtLevel(@NonNull final Key key, final int level) {
        if (level > MAX_NESTED_KEY_LEVELS) {
            throw new HandleException(BAD_ENCODING);
        }
        if (!key.hasThresholdKey() && !key.hasKeyList()) {
            validateSimple(key);
        } else if (key.hasThresholdKey()
                && key.thresholdKeyOrThrow().hasKeys()
                && key.thresholdKeyOrThrow().keysOrThrow().hasKeys()) {
            key.thresholdKeyOrThrow().keysOrThrow().keysOrThrow().forEach(k -> validateKeyAtLevel(k, level + 1));
        } else if (key.keyListOrThrow().hasKeys()) {
            key.keyListOrThrow().keysOrThrow().forEach(k -> validateKeyAtLevel(k, level + 1));
        }
    }

    /**
     * Current behavior is to only invalidate a simple key structure if it has no explicit type. Other validations,
     * like on the number of bytes in the public key; or on the size of the threshold key; are done elsewhere.
     *
     * @param key the key to validate
     */
    private void validateSimple(@NonNull final Key key) {
        if (key.key().kind() == Key.KeyOneOfType.UNSET) {
            throw new HandleException(BAD_ENCODING);
        }
    }

    private static boolean contains(byte[] a, byte val) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] == val) {
                return true;
            }
        }
        return false;
    }
}
