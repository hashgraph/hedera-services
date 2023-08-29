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

package com.swirlds.common.formatting;

import static com.swirlds.common.formatting.StringFormattingUtils.commaSeparatedNumber;

import com.swirlds.common.units.Unit;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * This class generates formatted strings for numbers with units.
 */
public class UnitFormatter {

    private boolean abbreviate = true;
    private int decimalPlaces = 1;
    private boolean showUnit = true;
    private boolean showSpaceInBetween = true;
    private UnitFormat unitFormat = UnitFormat.SINGLE_SIMPLIFIED;

    private Long longQuantity;
    private Double doubleQuantity;
    private final Unit<?> unit;

    /**
     * Create a new unit formatter.
     *
     * @param quantity the quantity
     * @param unit     the unit of the quantity
     */
    public UnitFormatter(final long quantity, final Unit<?> unit) {
        setQuantity(quantity);
        this.unit = unit;
    }

    /**
     * Create a new unit formatter.
     *
     * @param quantity the quantity
     * @param unit     the unit of the quantity
     */
    public UnitFormatter(final double quantity, final Unit<?> unit) {
        setQuantity(quantity);
        this.unit = unit;
    }

    /**
     * Create a new unit formatter.
     */
    public UnitFormatter(final Unit<?> unit) {
        setQuantity(0L);
        this.unit = unit;
    }

    /**
     * Set if the value should be simplified to the best unit.
     *
     * @param unitFormat the unit format
     * @return this object
     */
    @NonNull
    public UnitFormatter setUnitFormat(@NonNull final UnitFormat unitFormat) {
        this.unitFormat = unitFormat;
        return this;
    }

    /**
     * Set the quantity.
     *
     * @param quantity the quantity
     * @return this object
     */
    @NonNull
    public UnitFormatter setQuantity(final long quantity) {
        this.doubleQuantity = null;
        this.longQuantity = quantity;
        return this;
    }

    /**
     * Set the quantity.
     *
     * @param quantity the quantity
     * @return this object
     */
    @NonNull
    public UnitFormatter setQuantity(final double quantity) {
        this.longQuantity = null;
        this.doubleQuantity = quantity;
        return this;
    }

    /**
     * Set if the unit should be abbreviated. Default false;
     *
     * @param abbreviate if true then abbreviate the unit
     * @return this object
     */
    @NonNull
    public UnitFormatter setAbbreviate(final boolean abbreviate) {
        this.abbreviate = abbreviate;
        return this;
    }

    /**
     * Set the number of decimal places to show. Default 2.
     *
     * @param decimalPlaces the number of decimal places to show
     * @return this object
     */
    @NonNull
    public UnitFormatter setDecimalPlaces(final int decimalPlaces) {
        this.decimalPlaces = decimalPlaces;
        return this;
    }

    /**
     * Set if the unit should be displayed. Default true.
     *
     * @param showUnit true if the unit should be displayed
     * @return this object
     */
    @NonNull
    public UnitFormatter setShowUnit(final boolean showUnit) {
        this.showUnit = showUnit;
        return this;
    }

    /**
     * Set if there should be a space put in between the number and the unit. Default true.
     *
     * @param showSpaceInBetween true if there should be a space in between the number and the unit
     * @return this object
     */
    @NonNull
    public UnitFormatter setShowSpaceInBetween(final boolean showSpaceInBetween) {
        this.showSpaceInBetween = showSpaceInBetween;
        return this;
    }

    /**
     * Render the number as a string.
     *
     * @return the rendered string
     */
    @NonNull
    public String render() {
        final StringBuilder sb = new StringBuilder();
        render(sb);
        return sb.toString();
    }

    /**
     * Checks whether the given quantity string requires a plural unit
     * <p>
     * Any number other than 1.0 is considered to be plural.
     *
     * @param quantityString the quantity string
     * @return true if the quantity string requires a plural unit
     */
    private boolean isQuantityPlural(@NonNull final String quantityString) {
        boolean plural = false;

        if (quantityString.charAt(0) != '1') {
            plural = true;
        } else if (quantityString.length() > 1) {
            if (quantityString.charAt(1) != '.') {
                plural = true;
            } else {
                for (int i = 2; i < quantityString.length(); i++) {
                    final char c = quantityString.charAt(i);
                    if (c != '0') {
                        plural = true;
                        break;
                    }
                }
            }
        }

        return plural;
    }

    /**
     * Append the unit to the string builder.
     *
     * @param stringBuilder  the string builder to append to
     * @param quantityString the quantity string
     * @param finalUnit      the unit to append
     */
    private void appendUnit(
            @NonNull final StringBuilder stringBuilder,
            @NonNull final String quantityString,
            @NonNull final Unit<?> finalUnit) {

        if (showSpaceInBetween) {
            stringBuilder.append(" ");
        }

        if (abbreviate) {
            stringBuilder.append(finalUnit.getAbbreviation());
        } else {
            if (isQuantityPlural(quantityString)) {
                stringBuilder.append(finalUnit.getPluralName());
            } else {
                stringBuilder.append(finalUnit.getName());
            }
        }
    }

    /**
     * Format the number with no unit simplification. The printed unit will be the original unit
     *
     * @param stringBuilder the string builder to write the rendered string to
     */
    private void formatWithUnsimplifiedUnit(@NonNull final StringBuilder stringBuilder) {
        final double quantity = longQuantity == null ? doubleQuantity : (double) longQuantity;

        final String quantityString = commaSeparatedNumber(quantity, decimalPlaces);
        stringBuilder.append(quantityString);

        if (showUnit) {
            appendUnit(stringBuilder, quantityString, unit);
        }
    }

    /**
     * Format the number with a single simplified unit
     *
     * @param stringBuilder the string builder to write the rendered string to
     */
    private void formatWithSingleSimplifiedUnit(@NonNull final StringBuilder stringBuilder) {
        final Unit.SimplifiedQuantity<?> simplifiedQuantity;
        if (longQuantity == null) {
            simplifiedQuantity = unit.simplify(doubleQuantity);
        } else {
            simplifiedQuantity = unit.simplify(longQuantity);
        }

        final String quantityString = commaSeparatedNumber(simplifiedQuantity.quantity(), decimalPlaces);
        stringBuilder.append(quantityString);

        if (showUnit) {
            appendUnit(stringBuilder, quantityString, simplifiedQuantity.unit());
        }
    }

    /**
     * Add a term to the string builder, and return the remainder
     *
     * @param stringBuilder    the string builder to write the rendered string to
     * @param startingQuantity the starting quantity
     * @return the remainder
     */
    @NonNull
    private Unit.SimplifiedQuantity<?> addTerm(
            @NonNull final StringBuilder stringBuilder, @NonNull final Unit.SimplifiedQuantity<?> startingQuantity) {

        final long roundedDownQuantity = (long) startingQuantity.quantity();

        final Unit.SimplifiedQuantity<?> remainder = new Unit.SimplifiedQuantity<>(
                startingQuantity.quantity() - roundedDownQuantity, startingQuantity.unit());
        final Unit.SimplifiedQuantity<?> simplifiedRemainder = remainder.unit().simplify(remainder.quantity());

        final String termString;
        if (simplifiedRemainder.unit().ordinal() < unit.ordinal()) {
            termString = commaSeparatedNumber(startingQuantity.quantity(), decimalPlaces);
        } else {
            termString = commaSeparatedNumber(roundedDownQuantity, 0);
        }

        stringBuilder.append(termString);

        // multi simplified format always displays the unit
        appendUnit(stringBuilder, termString, startingQuantity.unit());

        return simplifiedRemainder;
    }

    /**
     * Format the number with multiple units.
     *
     * @param stringBuilder the string builder to write the rendered string to
     */
    private void formatWithMultiSimplifiedUnits(@NonNull final StringBuilder stringBuilder) {
        double originalQuantity = longQuantity == null ? doubleQuantity : (double) longQuantity;

        Unit.SimplifiedQuantity<?> remainder = addTerm(
                stringBuilder,
                new Unit.SimplifiedQuantity<>(originalQuantity, unit).unit().simplify(originalQuantity));

        while (remainder.unit().ordinal() >= unit.ordinal()) {
            stringBuilder.append(" ");
            remainder = addTerm(stringBuilder, remainder);
        }
    }

    /**
     * Render the number and write it to a string builder
     *
     * @param stringBuilder the string builder to write the rendered string to
     */
    public void render(@NonNull final StringBuilder stringBuilder) {
        Objects.requireNonNull(stringBuilder);

        if (longQuantity == null && doubleQuantity == null) {
            throw new IllegalStateException("Quantity must be set");
        }

        if (longQuantity != null && doubleQuantity != null) {
            throw new IllegalStateException("Quantity must be set to either long or double, not both");
        }

        switch (unitFormat) {
            case UNSIMPLIFIED -> formatWithUnsimplifiedUnit(stringBuilder);
            case SINGLE_SIMPLIFIED -> formatWithSingleSimplifiedUnit(stringBuilder);
            case MULTI_SIMPLIFIED -> formatWithMultiSimplifiedUnits(stringBuilder);
        }
    }
}
