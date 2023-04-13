package com.hedera.node.app.workflows.handle.validation;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.spi.exceptions.HandleException;
import com.hedera.node.app.spi.validation.AttributeValidator;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.function.LongSupplier;

import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.BAD_ENCODING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hedera.node.app.spi.config.PropertyNames.ENTITIES_MAX_LIFETIME;
import static com.hedera.node.app.spi.exceptions.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

@Singleton
public class StandardizedAttributeValidator implements AttributeValidator {
    public static final int MAX_NESTED_KEY_LEVELS = 15;

    private final long maxEntityLifetime;
    private final LongSupplier consensusSecondNow;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public StandardizedAttributeValidator(
            @NonNull final LongSupplier consensusSecondNow,
            @NonNull final @CompositeProps PropertySource properties,
            @NonNull final GlobalDynamicProperties dynamicProperties) {
        this.maxEntityLifetime = properties.getLongProperty(ENTITIES_MAX_LIFETIME);
        this.consensusSecondNow = consensusSecondNow;
        this.dynamicProperties = requireNonNull(dynamicProperties);
    }

    /**
     * {@inheritDoc
     */
    @Override
    public void validateKey(@NonNull final Key key) {
        validateKeyAtLevel(key, 1);
    }

    /**
     * {@inheritDoc
     */
    @Override
    public void validateExpiry(long expiry) {
        final var now = consensusSecondNow.getAsLong();
        final var expiryGivenMaxLifetime = now + maxEntityLifetime;
        validateTrue(expiry > now && expiry <= expiryGivenMaxLifetime, INVALID_EXPIRATION_TIME);
    }

    /**
     * {@inheritDoc
     */
    @Override
    public void validateAutoRenewPeriod(long autoRenewPeriod) {
        validateTrue(autoRenewPeriod >= dynamicProperties.minAutoRenewDuration()
                && autoRenewPeriod <= dynamicProperties.maxAutoRenewDuration(), AUTORENEW_DURATION_NOT_IN_RANGE);
    }

    /**
     * {@inheritDoc
     */
    @Override
    public void validateMemo(@NonNull final String memo) {
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
            key.thresholdKeyOrThrow().keysOrThrow().keysOrThrow().forEach(k ->
                    validateKeyAtLevel(k, level + 1));
        } else if (key.keyListOrThrow().hasKeys()) {
            key.keyListOrThrow().keysOrThrow().forEach(k ->
                    validateKeyAtLevel(k, level + 1));
        }
    }

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
