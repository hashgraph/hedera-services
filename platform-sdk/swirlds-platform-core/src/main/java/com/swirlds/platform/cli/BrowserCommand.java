package com.swirlds.platform.cli;

import com.swirlds.cli.PlatformCli;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.Browser;
import java.io.IOException;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "browse",
        mixinStandardHelpOptions = true,
        description = "Launch local instances of the platform using the Browser UI. "
                + "Note: this is sensitive to the current working directory, "
                + "and currently must be launched from the platform hedera-services/platform-sdk/sdk/ directory.")
@SubcommandOf(PlatformCli.class)
public class BrowserCommand extends AbstractCommand {

    // FUTURE WORK: this command currently ignores parameters for log4j and configuration paths.
    // FUTURE WORK: make this command insensitive to current working directory
    // FUTURE WORK: instead of passing string arguments for the browser to parse, do all of the parsing here

    private String[] browserArgs = new String[0];

    @CommandLine.Option(
            names = {"-a", "--browser-arg"},
            description = "Provide an argument that is passed to Browser.main().")
    private void setBrowserArgs(final String[] browserArgs) {
        this.browserArgs = browserArgs;
    }

    /**
     * This method is called after command line input is parsed.
     *
     * @return return code of the program
     */
    @Override
    public Integer call() throws IOException {
        try {
            Browser.main(browserArgs);
        } catch (final Exception e) {
            e.printStackTrace();
            return 1;
        }
        return 0;
    }
}
