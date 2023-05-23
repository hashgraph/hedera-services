package com.swirlds.platform.cli;

import com.swirlds.cli.PlatformCli;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.recovery.emergencyfile.EmergencyRecoveryFile;
import com.swirlds.platform.recovery.emergencyfile.Location;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(
		name = "generate-uid",
		mixinStandardHelpOptions = true,
		description = "Validate whether an emergency recovery file is well formed and has the necessary information")
@SubcommandOf(PlatformCli.class)
public class ValidateManifestFileCommand extends AbstractCommand {

	/** The path to the emergency recovery file. */
	private Path file;

	@SuppressWarnings("unused")
	@CommandLine.Parameters(description = "the path to manifest file, usually named emergencyRecovery.yaml")
	private void setFile(final Path file) {
		this.fileMustExist(file);
		this.file = file;
	}

	@Override
	public Integer call() throws IOException {
		final EmergencyRecoveryFile erf = EmergencyRecoveryFile.read(file);
		if (erf == null) {
			// this will probably never happen, but just in case
			throw new IOException("Emergency recovery file could not be read");
		}
		validateFieldExists(erf.recovery(), "recovery");
		validateFieldExists(erf.recovery().state(), "recovery->state");
		validateFieldExists(erf.recovery().state().hash(), "recovery->state->hash");
		validateFieldExists(erf.recovery().state().timestamp(), "recovery->state->timestamp");
		validateFieldExists(erf.recovery().boostrap(), "recovery->boostrap");
		validateFieldExists(erf.recovery().boostrap().timestamp(), "recovery->boostrap->timestamp");
		validateFieldExists(erf.recovery().pkg(), "recovery->package");
		validateFieldExists(erf.recovery().pkg().locations(), "recovery->package->locations");
		if(erf.recovery().pkg().locations().size() == 0) {
			throw new IOException("The file should have at least one location in the recovery->package->locations field");
		}
		List<Location> locations = erf.recovery().pkg().locations();
		for (int i = 0; i < locations.size(); i++) {
			final Location location = locations.get(i);
			validateFieldExists(location.type(), String.format("recovery->package->locations[%d]->type", i));
			validateFieldExists(location.url(), String.format("recovery->package->locations[%d]->url", i));
			validateFieldExists(location.hash(), String.format("recovery->package->locations[%d]->hash", i));
		}
		validateFieldExists(erf.recovery().stream(), "recovery->stream");
		validateFieldExists(erf.recovery().stream().intervals(), "recovery->stream->intervals");

		return 0;
	}

	private static void validateFieldExists(final Object field, final String fieldName) throws IOException {
		if (field == null) {
			throw new IOException("The field " + fieldName + " is missing from the emergency recovery file.");
		}
	}
}
