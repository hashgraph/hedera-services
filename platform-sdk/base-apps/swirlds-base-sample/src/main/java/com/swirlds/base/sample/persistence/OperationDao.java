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

package com.swirlds.base.sample.persistence;

import static java.util.Objects.isNull;

import com.google.common.base.Predicates;
import com.swirlds.base.sample.domain.Operation;
import com.swirlds.base.sample.internal.DataTransferUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.apache.logging.log4j.util.Strings;

/**
 * in-memory simple data layer for Operations
 */
public class OperationDao {

    private static class InstanceHolder {
        private static final OperationDao INSTANCE = new OperationDao();
    }

    public static @NonNull OperationDao getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static @NonNull final Map<String, Operation> OPERATIONS = new ConcurrentHashMap<>();

    public @NonNull Operation save(final @NonNull Operation operation) {

        if (isNull(operation.uuid())) {
            String uuid = UUID.randomUUID().toString();
            final Operation value =
                    new Operation(uuid, operation.details(), System.currentTimeMillis(), operation.type());
            OPERATIONS.put(uuid, value);
            return value;
        } else if (OPERATIONS.containsKey(operation.uuid())) {
            throw new IllegalArgumentException("Non modifiable resource");
        }
        return operation;
    }

    /**
     * Search using a criteria
     */
    public @NonNull List<Operation> findByCriteria(final @NonNull Criteria criteria) {
        return OPERATIONS.values().stream().filter(criteria.toSearchPredicate()).toList();
    }

    /**
     * Searching criteria
     */
    public record Criteria(
            String uuid, String itemId, Date from, Date to, BigDecimal unitaryPriceFrom, BigDecimal unitaryPriceTo) {

        /**
         * Create a searching criteria based on a map containing values
         */
        public static @NonNull Criteria fromMap(final @NonNull Map<String, String> parameters) {
            final String uuid = parameters.get("uuid");
            final String itemId = parameters.get("itemId");
            final Date from =
                    parameters.get("from") != null ? DataTransferUtils.parseDate(parameters.get("from")) : null;
            final Date to = parameters.get("to") != null ? DataTransferUtils.parseDate(parameters.get("to")) : null;
            final BigDecimal unitaryPriceFrom = parameters.get("unitaryPriceFrom") != null
                    ? new BigDecimal(parameters.get("unitaryPriceFrom"))
                    : null;
            final BigDecimal unitaryPriceTo =
                    parameters.get("unitaryPriceTo") != null ? new BigDecimal(parameters.get("unitaryPriceTo")) : null;

            return new Criteria(uuid, itemId, from, to, unitaryPriceFrom, unitaryPriceTo);
        }

        /**
         * @return a predicate to perform the search
         */
        private Predicate<Operation> toSearchPredicate() {
            if (Strings.isNotBlank(uuid)) {
                return (t) -> uuid.equals(t.uuid());
            }

            Predicate<Operation> result = Predicates.alwaysTrue();
            if (Strings.isNotBlank(itemId)) {
                result = result.and(t -> t.details().stream().anyMatch(d -> itemId.equals(d.itemId())));
            }
            if (Objects.nonNull(from)) {
                result = result.and(t -> new Date(t.timestamp()).after(from));
            }
            if (Objects.nonNull(to)) {
                result = result.and(t -> new Date(t.timestamp()).before(to));
            }
            if (Objects.nonNull(unitaryPriceFrom)) {
                result = result.and(
                        t -> t.details().stream().allMatch(d -> unitaryPriceFrom.compareTo(d.unitaryPrice()) <= 0));
            }
            if (Objects.nonNull(unitaryPriceTo)) {
                result = result.and(
                        t -> t.details().stream().allMatch(d -> unitaryPriceFrom.compareTo(d.unitaryPrice()) >= 0));
            }

            return result;
        }
    }
}
