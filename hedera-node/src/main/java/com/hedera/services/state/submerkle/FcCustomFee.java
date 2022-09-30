/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.submerkle;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.submerkle.FcCustomFee.FeeType.FIXED_FEE;
import static com.hedera.services.state.submerkle.FcCustomFee.FeeType.FRACTIONAL_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Objects;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Represents a custom fee attached to an HTS token type. Custom fees are charged during a
 * CryptoTransfer that moves units of the token type. They are always paid by the same account that
 * pays the ordinary Hedera fees to funding/reward accounts 0.0.98/0.0.800 and the submitting node's
 * account.
 *
 * <p>A custom fee must give a fee collection account to receive the charged fees. The amount to be
 * charged is specified by either a fixed or fractional term.
 *
 * <p>A <i>fixed fee</i> may have units of either ℏ or an arbitrary HTS token.
 *
 * <p>A <i>fractional fee</i> always has the same units as the token type defining the custom fee.
 * It specifies the fraction of the units moved that should go to the fee collection account, along
 * with an optional minimum and maximum number of units to be charged. (Unless the
 * "net-of-transfers" flag is set, this fee is deducted from the receiving account(s); when the flag
 * is set, an <b>additional</b> fee is levied to the sending account---the receiving account(s) get
 * the amounts in the original transfer list.)
 *
 * <p>A <i>royalty fee</i> is used with a non-fungible unique token type and sets a fraction of the
 * fungible value received for a NFT that should be collected as royalty. (If no fungible value is
 * received, a fixed fee can be charged to the NFT's new owner, if desired.)
 */
public class FcCustomFee implements SelfSerializable {
    static final byte FIXED_CODE = (byte) (1 << 0);
    static final byte FRACTIONAL_CODE = (byte) (1 << 1);
    static final byte ROYALTY_CODE = (byte) (1 << 2);

    static final int RELEASE_016X_VERSION = 1;
    static final int RELEASE_017X_VERSION = 2;
    static final int RELEASE_0310_VERSION = 3;
    static final int CURRENT_VERSION = RELEASE_0310_VERSION;
    static final long RUNTIME_CONSTRUCTABLE_ID = 0xf65baa433940f137L;

    private FeeType feeType;
    private EntityId feeCollector;
    private boolean allCollectorsAreExempt;
    private FixedFeeSpec fixedFeeSpec;
    private FractionalFeeSpec fractionalFeeSpec;
    private RoyaltyFeeSpec royaltyFeeSpec;

    public enum FeeType {
        FRACTIONAL_FEE,
        FIXED_FEE,
        ROYALTY_FEE
    }

    private Account collector;

    public FcCustomFee() {
        /* For RuntimeConstructable */
    }

    FcCustomFee(
            FeeType feeType,
            EntityId feeCollector,
            FixedFeeSpec fixedFeeSpec,
            FractionalFeeSpec fractionalFeeSpec,
            RoyaltyFeeSpec royaltyFeeSpec,
            boolean allCollectorsAreExempt) {
        this.feeType = feeType;
        this.feeCollector = feeCollector;
        this.fixedFeeSpec = fixedFeeSpec;
        this.royaltyFeeSpec = royaltyFeeSpec;
        this.fractionalFeeSpec = fractionalFeeSpec;
        this.allCollectorsAreExempt = allCollectorsAreExempt;
    }

    public boolean requiresCollectorAutoAssociation() {
        switch (feeType) {
            case FRACTIONAL_FEE:
                return true;
            case FIXED_FEE:
                return fixedFeeSpec.usedDenomWildcard();
            case ROYALTY_FEE:
                if (royaltyFeeSpec.hasFallbackFee()) {
                    return royaltyFeeSpec.fallbackFee().usedDenomWildcard();
                }
        }
        return false;
    }

    public void validateAndFinalizeWith(
            final Token provisionalToken,
            final AccountStore accountStore,
            final TypedTokenStore tokenStore) {
        validate(provisionalToken, true, accountStore, tokenStore);
    }

    public void validateWith(
            final Token owningToken,
            final AccountStore accountStore,
            final TypedTokenStore tokenStore) {
        validate(owningToken, false, accountStore, tokenStore);
    }

    @VisibleForTesting
    public void setAllCollectorsAreExempt(boolean allCollectorsAreExempt) {
        this.allCollectorsAreExempt = allCollectorsAreExempt;
    }

    private void validate(
            final Token token,
            final boolean beingCreated,
            final AccountStore accountStore,
            final TypedTokenStore tokenStore) {
        collector =
                accountStore.loadAccountOrFailWith(
                        feeCollector.asId(), INVALID_CUSTOM_FEE_COLLECTOR);

        switch (feeType) {
            case FIXED_FEE:
                if (beingCreated) {
                    fixedFeeSpec.validateAndFinalizeWith(token, collector, tokenStore);
                } else {
                    fixedFeeSpec.validateWith(collector, tokenStore);
                }
                break;
            case ROYALTY_FEE:
                validateTrue(
                        token.isNonFungibleUnique(),
                        CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
                if (beingCreated) {
                    royaltyFeeSpec.validateAndFinalizeWith(token, collector, tokenStore);
                } else {
                    royaltyFeeSpec.validateWith(token, collector, tokenStore);
                }
                break;
            case FRACTIONAL_FEE:
                validateTrue(
                        token.isFungibleCommon(),
                        CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON);
                if (!beingCreated) {
                    validateTrue(
                            tokenStore.hasAssociation(token, collector),
                            TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
                }
                break;
        }
    }

    public static FcCustomFee royaltyFee(
            long numerator,
            long denominator,
            FixedFeeSpec fallbackFee,
            EntityId feeCollector,
            boolean allCollectorsAreExempt) {
        Objects.requireNonNull(feeCollector);
        final var spec = new RoyaltyFeeSpec(numerator, denominator, fallbackFee);
        return new FcCustomFee(
                FeeType.ROYALTY_FEE, feeCollector, null, null, spec, allCollectorsAreExempt);
    }

    public static FcCustomFee fractionalFee(
            long numerator,
            long denominator,
            long minimumUnitsToCollect,
            long maximumUnitsToCollect,
            boolean netOfTransfers,
            EntityId feeCollector,
            boolean allCollectorsAreExempt) {
        Objects.requireNonNull(feeCollector);
        final var spec =
                new FractionalFeeSpec(
                        numerator,
                        denominator,
                        minimumUnitsToCollect,
                        maximumUnitsToCollect,
                        netOfTransfers);
        return new FcCustomFee(
                FeeType.FRACTIONAL_FEE, feeCollector, null, spec, null, allCollectorsAreExempt);
    }

    public static FcCustomFee fixedFee(
            long unitsToCollect,
            EntityId tokenDenomination,
            EntityId feeCollector,
            boolean allCollectorsAreExempt) {
        Objects.requireNonNull(feeCollector);
        final var spec = new FixedFeeSpec(unitsToCollect, tokenDenomination);
        return new FcCustomFee(FIXED_FEE, feeCollector, spec, null, null, allCollectorsAreExempt);
    }

    public static FcCustomFee fromGrpc(CustomFee source) {
        final var isSpecified =
                source.hasFixedFee() || source.hasFractionalFee() || source.hasRoyaltyFee();
        validateTrue(isSpecified, CUSTOM_FEE_NOT_FULLY_SPECIFIED);

        final var feeCollector = EntityId.fromGrpcAccountId(source.getFeeCollectorAccountId());
        final var allCollectorsAreExempt = source.getAllCollectorsAreExempt();
        if (source.hasFixedFee()) {
            EntityId denom = null;
            final var fixedSource = source.getFixedFee();
            if (fixedSource.hasDenominatingTokenId()) {
                denom = EntityId.fromGrpcTokenId(fixedSource.getDenominatingTokenId());
            }
            return fixedFee(fixedSource.getAmount(), denom, feeCollector, allCollectorsAreExempt);
        } else if (source.hasFractionalFee()) {
            final var fractionalSource = source.getFractionalFee();
            final var fraction = fractionalSource.getFractionalAmount();
            final var nominalMax = fractionalSource.getMaximumAmount();
            final var effectiveMax = nominalMax == 0 ? Long.MAX_VALUE : nominalMax;
            return fractionalFee(
                    fraction.getNumerator(),
                    fraction.getDenominator(),
                    fractionalSource.getMinimumAmount(),
                    effectiveMax,
                    fractionalSource.getNetOfTransfers(),
                    feeCollector,
                    allCollectorsAreExempt);
        } else {
            final var royaltySource = source.getRoyaltyFee();
            final var fraction = royaltySource.getExchangeValueFraction();
            return royaltyFee(
                    fraction.getNumerator(),
                    fraction.getDenominator(),
                    royaltySource.hasFallbackFee()
                            ? FixedFeeSpec.fromGrpc(royaltySource.getFallbackFee())
                            : null,
                    feeCollector,
                    allCollectorsAreExempt);
        }
    }

    public CustomFee asGrpc() {
        final var builder =
                CustomFee.newBuilder()
                        .setFeeCollectorAccountId(feeCollector.toGrpcAccountId())
                        .setAllCollectorsAreExempt(allCollectorsAreExempt);
        if (feeType == FIXED_FEE) {
            final var spec = fixedFeeSpec;
            builder.setFixedFee(spec.asGrpc());
        } else if (feeType == FRACTIONAL_FEE) {
            final var spec = fractionalFeeSpec;
            final var fracBuilder =
                    FractionalFee.newBuilder()
                            .setFractionalAmount(
                                    Fraction.newBuilder()
                                            .setNumerator(spec.getNumerator())
                                            .setDenominator(spec.getDenominator()))
                            .setMinimumAmount(spec.getMinimumAmount());
            if (spec.getMaximumUnitsToCollect() != Long.MAX_VALUE) {
                fracBuilder.setMaximumAmount(spec.getMaximumUnitsToCollect());
            }
            fracBuilder.setNetOfTransfers(spec.isNetOfTransfers());
            builder.setFractionalFee(fracBuilder);
        } else {
            final var spec = royaltyFeeSpec;
            final var royaltyBuilder =
                    RoyaltyFee.newBuilder()
                            .setExchangeValueFraction(
                                    Fraction.newBuilder()
                                            .setNumerator(spec.numerator())
                                            .setDenominator(spec.denominator()));
            final var fallback = spec.fallbackFee();
            if (fallback != null) {
                royaltyBuilder.setFallbackFee(fallback.asGrpc());
            }
            builder.setRoyaltyFee(royaltyBuilder);
        }
        return builder.build();
    }

    public EntityId getFeeCollector() {
        return feeCollector;
    }

    public Id getFeeCollectorAsId() {
        return feeCollector.asId();
    }

    public FeeType getFeeType() {
        return feeType;
    }

    public FixedFeeSpec getFixedFeeSpec() {
        return fixedFeeSpec;
    }

    public void nullOutCollector() {
        collector = null;
    }

    public Account getValidatedCollector() {
        return collector;
    }

    public FractionalFeeSpec getFractionalFeeSpec() {
        return fractionalFeeSpec;
    }

    public RoyaltyFeeSpec getRoyaltyFeeSpec() {
        return royaltyFeeSpec;
    }

    public boolean getAllCollectorsAreExempt() {
        return allCollectorsAreExempt;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !obj.getClass().equals(FcCustomFee.class)) {
            return false;
        }

        final var that = (FcCustomFee) obj;
        return this.feeType == that.feeType
                && Objects.equals(this.feeCollector, that.feeCollector)
                && Objects.equals(this.fixedFeeSpec, that.fixedFeeSpec)
                && Objects.equals(this.fractionalFeeSpec, that.fractionalFeeSpec)
                && Objects.equals(this.royaltyFeeSpec, that.royaltyFeeSpec)
                && Objects.equals(this.allCollectorsAreExempt, that.allCollectorsAreExempt);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(FcCustomFee.class)
                .omitNullValues()
                .add("feeType", feeType)
                .add("fixedFee", fixedFeeSpec)
                .add("fractionalFee", fractionalFeeSpec)
                .add("royaltyFee", royaltyFeeSpec)
                .add("feeCollector", feeCollector)
                .add("allCollectorsAreExempt", allCollectorsAreExempt)
                .toString();
    }

    @Override
    public void deserialize(SerializableDataInputStream din, int version) throws IOException {
        var byteCode = din.readByte();
        if (byteCode == FIXED_CODE) {
            feeType = FIXED_FEE;
            var unitsToCollect = din.readLong();
            EntityId denom = din.readSerializable(true, EntityId::new);
            fixedFeeSpec = new FixedFeeSpec(unitsToCollect, denom);
        } else if (byteCode == FRACTIONAL_CODE) {
            feeType = FeeType.FRACTIONAL_FEE;
            var numerator = din.readLong();
            var denominator = din.readLong();
            var minimumUnitsToCollect = din.readLong();
            var maximumUnitsToCollect = din.readLong();
            var netOfTransfers = version >= RELEASE_017X_VERSION && din.readBoolean();
            fractionalFeeSpec =
                    new FractionalFeeSpec(
                            numerator,
                            denominator,
                            minimumUnitsToCollect,
                            maximumUnitsToCollect,
                            netOfTransfers);
        } else {
            feeType = FeeType.ROYALTY_FEE;
            var numerator = din.readLong();
            var denominator = din.readLong();
            var hasFallback = din.readBoolean();
            if (hasFallback) {
                var unitsToCollect = din.readLong();
                EntityId denom = din.readSerializable(true, EntityId::new);
                var fallbackFee = new FixedFeeSpec(unitsToCollect, denom);
                royaltyFeeSpec = new RoyaltyFeeSpec(numerator, denominator, fallbackFee);
            } else {
                royaltyFeeSpec = new RoyaltyFeeSpec(numerator, denominator, null);
            }
        }

        feeCollector = din.readSerializable(true, EntityId::new);
        allCollectorsAreExempt = version >= RELEASE_0310_VERSION && din.readBoolean();
    }

    @Override
    public void serialize(SerializableDataOutputStream dos) throws IOException {
        if (feeType == FIXED_FEE) {
            dos.writeByte(FIXED_CODE);
            serializeFixed(fixedFeeSpec, dos);
        } else if (feeType == FRACTIONAL_FEE) {
            dos.writeByte(FRACTIONAL_CODE);
            dos.writeLong(fractionalFeeSpec.getNumerator());
            dos.writeLong(fractionalFeeSpec.getDenominator());
            dos.writeLong(fractionalFeeSpec.getMinimumAmount());
            dos.writeLong(fractionalFeeSpec.getMaximumUnitsToCollect());
            dos.writeBoolean(fractionalFeeSpec.isNetOfTransfers());
        } else {
            dos.writeByte(ROYALTY_CODE);
            dos.writeLong(royaltyFeeSpec.numerator());
            dos.writeLong(royaltyFeeSpec.denominator());
            if (royaltyFeeSpec.fallbackFee() == null) {
                dos.writeBoolean(false);
            } else {
                dos.writeBoolean(true);
                serializeFixed(royaltyFeeSpec.fallbackFee(), dos);
            }
        }
        dos.writeSerializable(feeCollector, true);
        dos.writeBoolean(allCollectorsAreExempt);
    }

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    private void serializeFixed(FixedFeeSpec fee, SerializableDataOutputStream dos)
            throws IOException {
        dos.writeLong(fee.getUnitsToCollect());
        dos.writeSerializable(fee.getTokenDenomination(), true);
    }

    @Override
    public int getMinimumSupportedVersion() {
        return RELEASE_017X_VERSION;
    }
}
