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

/**
 * This class generates formatted strings for numbers with units.
 */
public class UnitFormatter {

    private boolean abbreviate = true;
    private int decimalPlaces = 1;
    private boolean showUnit = true;
    private boolean showSpaceInBetween = true;
    private boolean simplify = true;

    private Long longQuantity;
    private Double doubleQuantity;
    private final Unit<?> unit;

    /**
     * Create a new unit formatter.
     *
     * @param quantity
     * 		the quantity
     * @param unit
     * 		the unit of the quantity
     */
    public UnitFormatter(final long quantity, final Unit<?> unit) {
        setQuantity(quantity);
        this.unit = unit;
    }

    /**
     * Create a new unit formatter.
     *
     * @param quantity
     * 		the quantity
     * @param unit
     * 		the unit of the quantity
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
     * @param simplify
     * 		true if the unit should be simplified
     * @return this object
     */
    public UnitFormatter setSimplify(final boolean simplify) {
        this.simplify = simplify;
        return this;
    }

    /**
     * Set the quantity.
     *
     * @param quantity
     * 		the quantity
     * @return this object
     */
    public UnitFormatter setQuantity(final long quantity) {
        this.doubleQuantity = null;
        this.longQuantity = quantity;
        return this;
    }

    /**
     * Set the quantity.
     *
     * @param quantity
     * 		the quantity
     * @return this object
     */
    public UnitFormatter setQuantity(final double quantity) {
        this.longQuantity = null;
        this.doubleQuantity = quantity;
        return this;
    }

    /**
     * Set if the unit should be abbreviated. Default false;
     *
     * @param abbreviate
     * 		if true then abbreviate the unit
     * @return this object
     */
    public UnitFormatter setAbbreviate(final boolean abbreviate) {
        this.abbreviate = abbreviate;
        return this;
    }

    /**
     * Set the number of decimal places to show. Default 2.
     *
     * @param decimalPlaces
     * 		the number of decimal places to show
     * @return this object
     */
    public UnitFormatter setDecimalPlaces(final int decimalPlaces) {
        this.decimalPlaces = decimalPlaces;
        return this;
    }

    /**
     * Set if the unit should be displayed. Default true.
     *
     * @param showUnit
     * 		true if the unit should be displayed
     * @return this object
     */
    public UnitFormatter setShowUnit(final boolean showUnit) {
        this.showUnit = showUnit;
        return this;
    }

    /**
     * Set if there should be a space put in between the number and the unit. Default true.
     *
     * @param showSpaceInBetween
     * 		true if there should be a space in between the number and the unit
     * @return this object
     */
    public UnitFormatter setShowSpaceInBetween(final boolean showSpaceInBetween) {
        this.showSpaceInBetween = showSpaceInBetween;
        return this;
    }

    /**
     * Render the number as a string.
     *
     * @return the rendered string
     */
    public String render() {
        final StringBuilder sb = new StringBuilder();
        render(sb);
        return sb.toString();
    }

    /**
     * Render the number and write it to a string builder
     *
     * @param sb
     * 		the string builder to write the rendered string to
     */
    public void render(final StringBuilder sb) {

        if (longQuantity == null && doubleQuantity == null) {
            throw new IllegalStateException("Quantity must be set");
        }

        if (longQuantity != null && doubleQuantity != null) {
            throw new IllegalStateException("Quantity must be set to either long or double, not both");
        }

        final Unit<?> finalUnit;
        final double quantity;

        if (simplify) {
            final Unit.SimplifiedQuantity<?> simplifiedQuantity;
            if (longQuantity == null) {
                simplifiedQuantity = unit.simplify(doubleQuantity);
            } else {
                simplifiedQuantity = unit.simplify(longQuantity);
            }
            finalUnit = simplifiedQuantity.unit();
            quantity = simplifiedQuantity.quantity();
        } else {
            finalUnit = unit;
            if (longQuantity == null) {
                quantity = doubleQuantity;
            } else {
                quantity = longQuantity;
            }
        }

        final String quantityString = commaSeparatedNumber(quantity, decimalPlaces);
        sb.append(quantityString);

        if (showUnit) {
            if (showSpaceInBetween) {
                sb.append(" ");
            }

            if (abbreviate) {
                sb.append(finalUnit.getAbbreviation());
            } else {

                // We need to figure out if the number is equal to 1.0 after rounding.
                // Any number other than 1.0 is considered to be plural.
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

                if (plural) {
                    sb.append(finalUnit.getPluralName());
                } else {
                    sb.append(finalUnit.getName());
                }
            }
        }
    }
}
