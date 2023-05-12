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

package com.swirlds.cli.utility;

import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine;

/**
 * This class does preliminary parsing for arguments that need to be handled prior to the main CLI argument parsing.
 */
@CommandLine.Command()
public class PlatformCliPreParser implements Runnable {

    private List<String> cliPackagePrefixes;
    private Path log4jPath;
    private String[] unparsedArgs;
    private boolean colorDisabled;

    @CommandLine.Option(names = {"-C", "--cli"})
    private void setCliPackagePrefixes(final List<String> cliPackagePrefixes) {
        this.cliPackagePrefixes = cliPackagePrefixes;
    }

    /**
     * Set the log4j path.
     */
    @CommandLine.Option(names = {"-L", "--log4j"})
    private void setLog4jPath(final Path log4jPath) {
        this.log4jPath = log4jPath;
    }

    @CommandLine.Option(names = {"--no-color"})
    private void setColorDisabled(final boolean colorDisabled) {
        this.colorDisabled = colorDisabled;
    }

    /**
     * Get CLI package prefixes that should be scanned for additional CLI commands. Returns null if there are none.
     */
    public List<String> getCliPackagePrefixes() {
        return cliPackagePrefixes;
    }

    /**
     * The path where log4j configuration can be found. Returns null if there is none provided.
     */
    public Path getLog4jPath() {
        return log4jPath;
    }

    /**
     * Check if color is disabled.
     */
    public boolean isColorDisabled() {
        return colorDisabled;
    }

    /**
     * Get the arguments that were not parsed by this object.
     */
    public String[] getUnparsedArgs() {
        return unparsedArgs;
    }

    /**
     * Set the arguments that were not parsed by this object.
     */
    public void setUnparsedArgs(final String[] unparsedArgs) {
        this.unparsedArgs = unparsedArgs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        // no-op
    }
}
