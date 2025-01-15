/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

module com.swirlds.platform.component_framework {
    exports com.swirlds.platform.component.framework;
    exports com.swirlds.platform.component.framework.model.diagram;
    exports com.swirlds.platform.component.framework.component;
    exports com.swirlds.platform.component.framework.counters;
    exports com.swirlds.platform.component.framework.model;
    exports com.swirlds.platform.component.framework.schedulers;
    exports com.swirlds.platform.component.framework.schedulers.builders;
    exports com.swirlds.platform.component.framework.schedulers.internal;
    exports com.swirlds.platform.component.framework.tasks;
    exports com.swirlds.platform.component.framework.transformers;
    exports com.swirlds.platform.component.framework.wires;
    exports com.swirlds.platform.component.framework.wires.input;
    exports com.swirlds.platform.component.framework.wires.output;

    requires transitive com.swirlds.base;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires com.swirlds.logging;
    requires com.swirlds.metrics.api;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
