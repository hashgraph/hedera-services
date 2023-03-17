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

package com.swirlds.common.units;

import com.swirlds.common.formatting.UnitFormatter;

/**
 * An object capable of formatting a unit.
 *
 * @param <T>
 * 		the type of the unit to format
 */
public interface Unit<T extends Unit<T>> {

    /**
     * Represents a quantity in a simplified form.
     *
     * @param quantity
     * 		the quantity
     * @param unit
     * 		the unit of the quantity
     */
    record SimplifiedQuantity<T extends Unit<T>>(double quantity, Unit<T> unit) {}

    /**
     * Simplify a quantity to the most appropriate unit.
     *
     * @param quantity
     * 		the original quantity
     * @return an equivalent quantity and unit
     */
    SimplifiedQuantity<T> simplify(double quantity);

    /**
     * Convert a unit from one type to another.
     *
     * @param quantity
     * 		the original quantity
     * @param to
     * 		the new unit
     * @return the new quantity
     */
    double convertTo(double quantity, T to);

    /**
     * Convert a unit from one type to another.
     *
     * @param quantity
     * 		the original quantity
     * @param to
     * 		the new unit
     * @return the new quantity
     */
    double convertTo(long quantity, T to);

    /**
     * The position of this unit in the sequence of units of the same type.
     * The first unit in a unit family should have an ordinal of 0,
     * the next 1, and so on. Maps to enum ordinal for enum units.
     */
    int ordinal();

    /**
     * Get the number that the previous unit must be multiplied by to get this unit. The first unit
     * in the sequence should have a factor of 1.
     */
    int getConversionFactor();

    /**
     * Get the name of this unit.
     */
    String getName();

    /**
     * Get the name of this unit in plural form.
     */
    String getPluralName();

    /**
     * Get the abbreviation for this unit.
     */
    String getAbbreviation();

    /**
     * Build a formatter for this unit.
     */
    default UnitFormatter buildFormatter() {
        return new UnitFormatter(this);
    }

    /**
     * Build a formatter for this unit.
     *
     * @param quantity
     * 		the quantity of this unit
     */
    default UnitFormatter buildFormatter(final long quantity) {
        return new UnitFormatter(quantity, this);
    }

    /**
     * Build a formatter for this unit.
     *
     * @param quantity
     * 		the quantity of this unit
     */
    default UnitFormatter buildFormatter(final double quantity) {
        return new UnitFormatter(quantity, this);
    }
}
