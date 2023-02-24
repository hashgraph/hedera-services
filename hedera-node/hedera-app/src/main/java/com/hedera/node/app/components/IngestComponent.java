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

package com.hedera.node.app.components;

import com.hedera.node.app.workflows.ingest.IngestModule;
import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import dagger.Subcomponent;

/**
 * A Dagger subcomponent that provides the Ingest workflow.
 */
@Subcomponent(modules = IngestModule.class)
public interface IngestComponent {
    IngestWorkflow ingestWorkflow();

    @Subcomponent.Factory
    interface Factory {
        IngestComponent create();
    }
}
