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

module org.hiero.wiring.framework {
    exports org.hiero.wiring.framework;
    exports org.hiero.wiring.framework.model.diagram;
    exports org.hiero.wiring.framework.component;
    exports org.hiero.wiring.framework.counters;
    exports org.hiero.wiring.framework.model;
    exports org.hiero.wiring.framework.schedulers;
    exports org.hiero.wiring.framework.schedulers.builders;
    exports org.hiero.wiring.framework.schedulers.internal;
    exports org.hiero.wiring.framework.tasks;
    exports org.hiero.wiring.framework.transformers;
    exports org.hiero.wiring.framework.wires;
    exports org.hiero.wiring.framework.wires.input;
    exports org.hiero.wiring.framework.wires.output;

    requires transitive com.swirlds.base;
    requires transitive com.swirlds.config.api;
    requires com.swirlds.common;
    requires com.swirlds.logging;
    requires com.swirlds.metrics.api;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
