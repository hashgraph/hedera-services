/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.utilops.inventory;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.suites.HapiSuite.APP_PROPERTIES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RecordSystemProperty<T> extends UtilOp {
    private static final Logger log = LogManager.getLogger(RecordSystemProperty.class);

    private final String property;
    private final Function<String, T> converter;
    private final Consumer<T> historian;

    public RecordSystemProperty(
            String property, Function<String, T> converter, Consumer<T> historian) {
        this.property = property;
        this.converter = converter;
        this.historian = historian;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        Map<String, String> nodeProps = new HashMap<>();
        var op = QueryVerbs.getFileContents(APP_PROPERTIES).addingConfigListTo(nodeProps);
        allRunFor(spec, op);

        T value;
        if (!nodeProps.containsKey(property)) {
            var defaultProps = loadDefaults();
            if (!defaultProps.containsKey(property)) {
                throw new IllegalStateException(
                        String.format(
                                "Nothing can be recorded for putative property '%s'!", property));
            }
            var defaultValue = defaultProps.get(property);
            log.info("Recorded default '{}' = '{}'", property, defaultValue);
            value = converter.apply(defaultValue);
        } else {
            log.info("Recorded '{}' override = '{}'", property, nodeProps.get(property));
            value = converter.apply(nodeProps.get(property));
        }
        historian.accept(value);
        return false;
    }

    Map<String, String> loadDefaults() throws IOException {
        var defaultProps = new Properties();
        defaultProps.load(
                RecordSystemProperty.class
                        .getClassLoader()
                        .getResourceAsStream("bootstrap.properties"));
        Map<String, String> defaults = new HashMap<>();
        defaultProps
                .stringPropertyNames()
                .forEach(p -> defaults.put(p, defaultProps.getProperty(p)));
        return defaults;
    }
}
