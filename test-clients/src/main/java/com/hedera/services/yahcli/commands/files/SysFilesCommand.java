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

import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.config.ConfigManager;
import com.hedera.services.yahcli.config.ConfigUtils;
import java.io.File;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "sysfiles",
        subcommands = {
            picocli.CommandLine.HelpCommand.class,
            SpecialFileHashCommand.class,
            SysFileDownloadCommand.class,
            SysFileUploadCommand.class
        },
        description = "Uploads/downloads system files")
public class SysFilesCommand implements Callable<Integer> {
    @ParentCommand Yahcli yahcli;

    static String resolvedDir(String literal, ConfigManager config) {
        if (literal.startsWith("{network}")) {
            literal = config.getTargetName() + File.separator + "sysfiles";
        }
        ConfigUtils.ensureDir(literal);
        if (literal.endsWith(File.separator)) {
            literal = literal.substring(0, literal.length() - 1);
        }
        return literal;
    }

    @Override
    public Integer call() throws Exception {
        throw new picocli.CommandLine.ParameterException(
                yahcli.getSpec().commandLine(), "Please specify an sysfile subcommand!");
    }

    public Yahcli getYahcli() {
        return yahcli;
    }
}
