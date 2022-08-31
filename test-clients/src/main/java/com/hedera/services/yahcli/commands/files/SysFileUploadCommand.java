/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.yahcli.commands.files;

import static com.hedera.services.bdd.spec.HapiApiSpec.SpecStatus.PASSED;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.BYTES_4K;
import static com.hedera.services.yahcli.commands.files.SysFilesCommand.resolvedDir;
import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.services.yahcli.suites.SysFileUploadSuite;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import picocli.CommandLine;

@CommandLine.Command(
        name = "upload",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Uploads a system file")
public class SysFileUploadCommand implements Callable<Integer> {
    private static final int DEFAULT_BYTES_PER_APPEND = BYTES_4K;
    private static final int DEFAULT_APPENDS_PER_BURST = 256;

    public static AtomicReference<String> activeSrcDir = new AtomicReference<>();

    @CommandLine.ParentCommand private SysFilesCommand sysFilesCommand;

    @CommandLine.Option(
            names = {"-s", "--source-dir"},
            paramLabel = "source directory",
            defaultValue = "{network}/sysfiles/")
    private String srcDir;

    @CommandLine.Option(
            names = {"--dry-run"},
            description =
                    "only write the serialized form of the system file to disk, do not send a"
                            + " FileUpdate")
    private boolean dryRun;

    @CommandLine.Option(
            names = {"--bytes-per-append"},
            description =
                    "number of bytes to add in each append to a special file (default "
                            + DEFAULT_BYTES_PER_APPEND
                            + ")")
    private Integer bytesPerAppend;

    @CommandLine.Option(
            names = {"--appends-per-burst"},
            description =
                    "number of appends to \"burst\" when uploading a special file (default "
                            + DEFAULT_APPENDS_PER_BURST
                            + ")")
    private Integer appendsPerBurst;

    @CommandLine.Option(
            names = {"--restart-from-failure"},
            description = "try to only append missing content")
    private Boolean restartFromFailure;

    @CommandLine.Parameters(
            arity = "1",
            paramLabel = "<sysfile>",
            description =
                    "one of "
                            + "{ address-book, node-details, fees, rates, props, "
                            + "permissions, throttles, software-zip, telemetry-zip } (or "
                            + "{ 101, 102, 111, 112, 121, 122, 123, 150, 159 })")
    private String sysFile;

    @Override
    public Integer call() throws Exception {
        var config = configFrom(sysFilesCommand.getYahcli());
        srcDir = resolvedDir(srcDir, config);
        activeSrcDir.set(srcDir);

        if (isSpecialFile()) {
            if (bytesPerAppend == null) {
                bytesPerAppend = BYTES_4K;
            }
            if (appendsPerBurst == null) {
                appendsPerBurst = DEFAULT_APPENDS_PER_BURST;
            }
            if (restartFromFailure == null) {
                restartFromFailure = Boolean.FALSE;
            }
        } else {
            if (bytesPerAppend != null) {
                throw new CommandLine.ParameterException(
                        sysFilesCommand.getYahcli().getSpec().commandLine(),
                        "Option 'bytesPerAppend' only makes sense for a special file");
            }
            if (appendsPerBurst != null) {
                throw new CommandLine.ParameterException(
                        sysFilesCommand.getYahcli().getSpec().commandLine(),
                        "Option 'appendsPerBurst' only makes sense for a special file");
            }
            if (restartFromFailure != null) {
                throw new CommandLine.ParameterException(
                        sysFilesCommand.getYahcli().getSpec().commandLine(),
                        "Option 'restartFromFailure' only makes sense for a special file");
            }
        }

        var delegate =
                isSpecialFile()
                        ? new SysFileUploadSuite(
                                bytesPerAppend,
                                appendsPerBurst,
                                restartFromFailure,
                                srcDir,
                                config.asSpecConfig(),
                                sysFile,
                                dryRun)
                        : new SysFileUploadSuite(srcDir, config.asSpecConfig(), sysFile, dryRun);

        delegate.runSuiteSync();

        final var finalSpecs = delegate.getFinalSpecs();
        if (!finalSpecs.isEmpty()) {
            if (finalSpecs.get(0).getStatus() == PASSED) {
                COMMON_MESSAGES.info("SUCCESS - Uploaded all requested system files");
            } else {
                COMMON_MESSAGES.warn("FAILED Uploading requested system files");
                return 1;
            }
        }

        return 0;
    }

    private boolean isSpecialFile() {
        return "software-zip".equals(sysFile)
                || "150".equals(sysFile)
                || "telemetry-zip".equals(sysFile)
                || "159".equals(sysFile);
    }
}
