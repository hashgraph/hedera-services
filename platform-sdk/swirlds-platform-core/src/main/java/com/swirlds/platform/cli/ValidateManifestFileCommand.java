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

package com.swirlds.platform.cli;

import com.swirlds.cli.PlatformCli;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.recovery.emergencyfile.EmergencyRecoveryFile;
import com.swirlds.platform.recovery.emergencyfile.Location;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine;

@CommandLine.Command(
        name = "validate-manifest-file",
        mixinStandardHelpOptions = true,
        description = "Validate whether an emergency recovery file is well formed and has the necessary information")
@SubcommandOf(PlatformCli.class)
public class ValidateManifestFileCommand extends AbstractCommand {

    /** The path to the emergency recovery file. */
    private Path dir;

    @SuppressWarnings("unused")
    @CommandLine.Parameters(
            description = "the path to dir containing manifest file which should be named emergencyRecovery.yaml")
    private void setDir(final Path dir) {
        this.pathMustExist(dir);
        this.dir = dir;
    }

    @Override
    public Integer call() throws IOException {
        final EmergencyRecoveryFile file = EmergencyRecoveryFile.read(dir);
        if (file == null) {
            throw new IOException("Emergency recovery file could not be read from: " + dir.toAbsolutePath());
        }
        validateFieldExists(file.recovery(), "recovery");
        validateFieldExists(file.recovery().state(), "recovery->state");
        validateFieldExists(file.recovery().state().hash(), "recovery->state->hash");
        validateFieldExists(file.recovery().state().timestamp(), "recovery->state->timestamp");
        validateFieldExists(file.recovery().boostrap(), "recovery->boostrap");
        validateFieldExists(file.recovery().boostrap().timestamp(), "recovery->boostrap->timestamp");
        validateFieldExists(file.recovery().pkg(), "recovery->package");
        validateFieldExists(file.recovery().pkg().locations(), "recovery->package->locations");
        if (file.recovery().pkg().locations().size() == 0) {
            throw new IOException(
                    "The file should have at least one location in the recovery->package->locations field");
        }
        List<Location> locations = file.recovery().pkg().locations();
        for (int i = 0; i < locations.size(); i++) {
            final Location location = locations.get(i);
            validateFieldExists(location.type(), String.format("recovery->package->locations[%d]->type", i));
            validateFieldExists(location.url(), String.format("recovery->package->locations[%d]->url", i));
            validateFieldExists(location.hash(), String.format("recovery->package->locations[%d]->hash", i));
        }
        validateFieldExists(file.recovery().stream(), "recovery->stream");
        validateFieldExists(file.recovery().stream().intervals(), "recovery->stream->intervals");

        System.out.println("The emergency recovery file is well formed and has the necessary information.");
        return 0;
    }

    private static void validateFieldExists(final Object field, final String fieldName) throws IOException {
        if (field == null) {
            throw new IOException("The field " + fieldName + " is missing from the emergency recovery file.");
        }
    }
}
