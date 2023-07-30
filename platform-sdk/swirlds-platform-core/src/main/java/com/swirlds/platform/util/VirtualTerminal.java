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

package com.swirlds.platform.util;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;

/**
 * A virtual terminal for running bash commands.
 */
public final class VirtualTerminal {

    private boolean printStdout;
    private boolean printStderr;
    private boolean printCommand;
    private boolean printExitCode;
    private boolean throwOnError;

    public VirtualTerminal() {}

    public VirtualTerminal setPrintStdout(boolean printStdout) {
        this.printStdout = printStdout;
        return this;
    }

    public VirtualTerminal setPrintStderr(boolean printStderr) {
        this.printStderr = printStderr;
        return this;
    }

    public VirtualTerminal setPrintCommand(boolean printCommand) {
        this.printCommand = printCommand;
        return this;
    }

    public VirtualTerminal setPrintExitCode(boolean printExitCode) {
        this.printExitCode = printExitCode;
        return this;
    }

    public VirtualTerminal setThrowOnError(boolean throwOnError) {
        this.throwOnError = throwOnError;
        return this;
    }

    /**
     * Run a shell command and return the result.
     *
     * @param command the command to run
     * @return the result of the command
     */
    @NonNull
    public CommandResult run(final String... command) {
        try {

            if (printCommand) {
                System.out.println("VirtualTerminal > " + String.join(" ", command));
            }

            final Runtime runtime = Runtime.getRuntime();
            final Process process = runtime.exec(command);

            final BufferedReader stdin = new BufferedReader(new InputStreamReader(process.getInputStream()));
            final StringBuilder in = new StringBuilder();

            String line;
            while ((line = stdin.readLine()) != null) {
                in.append(line).append("\n");
            }
            final String outString = in.toString();

            final BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            final StringBuilder err = new StringBuilder();

            while ((line = stderr.readLine()) != null) {
                err.append(line).append("\n");
            }
            final String errString = err.toString();

            while (process.isAlive()) {
                MILLISECONDS.sleep(1);
            }

            final int exitCode = process.exitValue();

            if (printStdout && !outString.isEmpty()) {
                System.out.println(outString);
            }
            if (printStderr && !errString.isEmpty()) {
                System.err.println(errString);
            }
            if (printExitCode) {
                System.out.println("Exit code: " + exitCode);
            }

            if (throwOnError && exitCode != 0) {
                throw new RuntimeException("Command failed: " + String.join(" ", command));
            }

            return new CommandResult(exitCode, outString, errString);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while running command", e);
        }
    }
}
