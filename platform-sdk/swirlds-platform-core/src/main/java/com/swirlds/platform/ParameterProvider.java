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

package com.swirlds.platform;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A utility class for obtaining the command line parameters when launching the platform using Browser.java.
 *
 * @deprecated this class will be removed at a future date, use the configuration engine for app config when launching
 * from the browser
 */
@Deprecated(forRemoval = true)
public final class ParameterProvider {

    private String[] parameters;
    private static final ParameterProvider INSTANCE = new ParameterProvider();

    private ParameterProvider() {}

    /**
     * Get the static instance of the ParameterProvider.
     *
     * @return the static instance of the ParameterProvider
     */
    public static ParameterProvider getInstance() {
        return INSTANCE;
    }

    /**
     * Set the command line parameters.
     *
     * @param parameters the command line parameters
     */
    public synchronized void setParameters(@Nullable final String[] parameters) {
        this.parameters = parameters;
    }

    /**
     * Get the command line parameters. Only populated of launching using the Browser.java.
     *
     * @return the command line parameters
     */
    public synchronized @Nullable String[] getParameters() {
        return parameters;
    }
}
