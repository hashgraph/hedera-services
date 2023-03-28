/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.hashgraph;
/*
 * This file is public domain.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.PlatformEvent;
import com.swirlds.platform.Browser;
import com.swirlds.platform.ExternalIpAddress;
import com.swirlds.platform.IpAddressStatus;
import com.swirlds.platform.Network;
import com.swirlds.platform.gui.SwirldsGui;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.TextField;
import java.awt.geom.Rectangle2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.BiFunction;
import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 * This app draws the hashgraph on the screen. Events are circles, with earlier ones lower. Events are color
 * coded: A non-witness is gray, and a witness has a color of green (famous), blue (not famous) or red
 * (undecided fame). When the event becomes part of the consensus, its color becomes darker.
 */
public class HashgraphDemoMain implements SwirldMain {
    /** delay after each screen update in milliseconds (250 means update 4 times per second) */
    private static final long screenUpdateDelay = 250;

    /** color for outline of labels */
    static final Color LABEL_OUTLINE = new Color(255, 255, 255);
    /** color for unknown-fame witness, non-consensus */
    static final Color LIGHT_RED = new Color(192, 0, 0);
    /** color for unknown-fame witness, consensus (which never happens) */
    static final Color DARK_RED = new Color(128, 0, 0);
    /** color for famous witness, non-consensus */
    static final Color LIGHT_GREEN = new Color(0, 192, 0);
    /** color for famous witness, consensus */
    static final Color DARK_GREEN = new Color(0, 128, 0);
    /** color for non-famous witness, non-consensus */
    static final Color LIGHT_BLUE = new Color(0, 0, 192);
    /** color for non-famous witness, consensus */
    static final Color DARK_BLUE = new Color(0, 0, 128);
    /** color for non-witness, non-consensus */
    static final Color LIGHT_GRAY = new Color(160, 160, 160);
    /** non-witness, consensus */
    static final Color DARK_GRAY = new Color(0, 0, 0);
    /** the app is run by this */
    public Platform platform;
    /** ID for this member */
    public long selfId;
    /** the entire window, including Swirlds menu, Picture, checkboxes */
    JFrame window;
    /** the JFrame with the hashgraph */
    Picture picture;
    /** a copy of the set of events at one moment, which paintComponent will draw */
    private PlatformEvent[] eventsCache;
    /** the number of members in the addressBook */
    private int numMembers = -1;
    /** number of columns (equals number of members) */
    private int numColumns;
    /** the nicknames of all the members */
    private String[] names;

    /** if checked, freeze the display (don't update it) */
    private Checkbox freezeCheckbox;
    /** if checked, color vertices only green (non-consensus) or blue (consensus) */
    private Checkbox simpleColorsCheckbox;

    // the following checkboxes control which labels to print on each vertex

    /** the round number for the event */
    private Checkbox labelRoundCheckbox;
    /** the consensus round received for the event */
    private Checkbox labelRoundRecCheckbox;
    /** the consensus order number for the event */
    private Checkbox labelConsOrderCheckbox;
    /** the consensus time stamp for the event */
    private Checkbox labelConsTimestampCheckbox;
    /** the generation number for the event */
    private Checkbox labelGenerationCheckbox;
    /** the ID number of the member who created the event */
    private Checkbox labelCreatorCheckbox;

    /** only draw this many events, at most */
    private TextField eventLimit;

    /** format the consensusTimestamp label */
    DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("H:m:s.n").withLocale(Locale.US).withZone(ZoneId.systemDefault());

    private static final BasicSoftwareVersion softwareVersion = new BasicSoftwareVersion(1);

    /**
     * Return the color for an event based on calculations in the consensus algorithm A non-witness is gray,
     * and a witness has a color of green (famous), blue (not famous) or red (undecided fame). When the
     * event becomes part of the consensus, its color becomes darker.
     *
     * @param event
     * 		the event to color
     * @return its color
     */
    private Color eventColor(final PlatformEvent event) {
        if (simpleColorsCheckbox.getState()) { // if checkbox checked
            return event.isConsensus() ? LIGHT_BLUE : LIGHT_GREEN;
        }
        if (!event.isWitness()) {
            return event.isConsensus() ? DARK_GRAY : LIGHT_GRAY;
        }
        if (!event.isFameDecided()) {
            return event.isConsensus() ? DARK_RED : LIGHT_RED;
        }
        if (event.isFamous()) {
            return event.isConsensus() ? DARK_GREEN : LIGHT_GREEN;
        }
        return event.isConsensus() ? DARK_BLUE : LIGHT_BLUE;
    }

    /**
     * This panel has the statistics and hashgraph picture, and appears in the window below all the
     * settings, right below "display last ___ events".
     */
    private class Picture extends JPanel {
        private static final long serialVersionUID = 1L;
        int ymin, ymax, width, n;
        double r;
        long minGen, maxGen;
        /** row to draw next in the window */
        int row;
        /** column to draw next in the window */
        int col;
        /** font height, in pixels */
        int textLineHeight;

        /**
         * find x position on the screen for the given event event
         *
         * @param event
         * 		the event (displayed as a circle on the screen)
         * @return the x coordinate for that event
         */
        private int xpos(final PlatformEvent event) {
            return ((int) event.getCreatorId() + 1) * width / (numColumns + 1);
        }

        /**
         * find y position on the screen for the given event event
         *
         * @param event
         * 		the event (displayed as a circle on the screen)
         * @return the y coordinate for that event
         */
        private int ypos(final PlatformEvent event) {
            return (event == null) ? -100 : (int) (ymax - r * (1 + 2 * (event.getGeneration() - minGen)));
        }

        /**
         * called by paintComponent to draw text at the top of the window
         *
         * @param g
         * 		the graphics context passed to paintComponent
         * @param text
         * 		a String.format formatting string
         * @param value
         * 		the value to pass to String.format to be formatted
         */
        private void print(final Graphics g, final String text, final double value) {
            g.drawString(String.format(text, value), col, row++ * textLineHeight - 3);
        }

        /** {@inheritDoc} */
        public void paintComponent(final Graphics g) {
            super.paintComponent(g);
            g.setFont(new Font(Font.MONOSPACED, 12, 12));
            final FontMetrics fm = g.getFontMetrics();
            final int fa = fm.getMaxAscent();
            final int fd = fm.getMaxDescent();
            textLineHeight = fa + fd;
            final int numMem = platform.getAddressBook().getSize();
            calcNames();
            width = getWidth();

            row = 1;
            col = 10;
            final Metrics metrics = platform.getContext().getMetrics();
            final double createCons = (double) metrics.getValue(PLATFORM_CATEGORY, "secC2C");
            final double recCons = (double) metrics.getValue(PLATFORM_CATEGORY, "secR2C");

            print(g, "%5.0f trans/sec", (double) metrics.getValue(PLATFORM_CATEGORY, "trans/sec"));
            print(g, "%5.0f events/sec", (double) metrics.getValue(PLATFORM_CATEGORY, "events/sec"));
            print(g, "%4.0f%% duplicate events", (double) metrics.getValue(PLATFORM_CATEGORY, "dupEv%"));

            print(g, "%5.3f sec, propagation time", createCons - recCons);
            print(g, "%5.3f sec, create to consensus", createCons);
            print(g, "%5.3f sec, receive to consensus", recCons);
            final Address address =
                    platform.getAddressBook().getAddress(platform.getSelfId().getId());
            print(g, "Internal: " + Network.getInternalIPAddress() + " : " + address.getPortInternalIpv4(), 0);

            final ExternalIpAddress ipAddress = Network.getExternalIpAddress();
            String externalIpAddress = ipAddress.toString();
            if (ipAddress.getStatus() == IpAddressStatus.IP_FOUND) {
                externalIpAddress += " : " + address.getPortExternalIpv4();
            }

            print(g, "External: " + externalIpAddress, 0);

            final int height1 = (row - 1) * textLineHeight; // text area at the top
            final int height2 = getHeight() - height1; // the main display, below the text
            g.setColor(Color.BLACK);
            ymin = (int) Math.round(height1 + 0.025 * height2);
            ymax = (int) Math.round(height1 + 0.975 * height2) - textLineHeight;
            for (int i = 0; i < numColumns; i++) {
                final int x = (i + 1) * width / (numColumns + 1);
                g.drawLine(x, ymin, x, ymax);
                final Rectangle2D rect = fm.getStringBounds(names[i], g);
                g.drawString(names[i], (int) (x - rect.getWidth() / 2), (int) (ymax + rect.getHeight()));
            }

            PlatformEvent[] events = eventsCache;
            if (events == null) { // in case a screen refresh happens before any events
                return;
            }
            int maxEvents;
            try {
                maxEvents = Math.max(0, Integer.parseInt(eventLimit.getText()));
            } catch (final NumberFormatException err) {
                maxEvents = 0;
            }

            if (maxEvents > 0) {
                events = Arrays.copyOfRange(events, Math.max(0, events.length - maxEvents), events.length);
            }

            minGen = Integer.MAX_VALUE;
            maxGen = Integer.MIN_VALUE;
            for (final PlatformEvent event : events) {
                minGen = Math.min(minGen, event.getGeneration());
                maxGen = Math.max(maxGen, event.getGeneration());
            }
            maxGen = Math.max(maxGen, minGen + 2);
            n = numMem + 1;
            final double gens = maxGen - minGen;
            final double dy = (ymax - ymin) * (gens - 1) / gens;
            r = Math.min(width / n / 4, dy / gens / 2);
            final int d = (int) (2 * r);

            // for each event, draw 2 downward lines to its parents
            for (final PlatformEvent event : events) {
                g.setColor(eventColor(event));
                final PlatformEvent e1 = event.getSelfParent();
                final PlatformEvent e2 = event.getOtherParent();
                if (e1 != null && e1.getGeneration() >= minGen) {
                    g.drawLine(xpos(event), ypos(event), xpos(event), ypos(e1));
                }
                if (e2 != null && e2.getGeneration() >= minGen) {
                    g.drawLine(xpos(event), ypos(event), xpos(e2), ypos(e2));
                }
            }

            // for each event, draw its circle
            for (final PlatformEvent event : events) {
                final PlatformEvent e1 = event.getSelfParent();
                final PlatformEvent e2 = event.getOtherParent();
                if (e1 == null || e2 == null) {
                    continue; // discarded events have no parents, so skip them
                }
                final Color color = eventColor(event);
                g.setColor(color);
                g.fillOval(xpos(event) - d / 2, ypos(event) - d / 2, d, d);
                g.setFont(g.getFont().deriveFont(Font.BOLD));

                String s = "";

                if (labelRoundCheckbox.getState()) {
                    s += " " + event.getRoundCreated();
                }
                if (labelRoundRecCheckbox.getState() && event.getRoundReceived() > 0) {
                    s += " " + event.getRoundReceived();
                }
                // if not consensus, then there's no order yet
                if (labelConsOrderCheckbox.getState() && event.isConsensus()) {
                    s += " " + event.getConsensusOrder();
                }
                if (labelConsTimestampCheckbox.getState()) {
                    final Instant t = event.getConsensusTimestamp();
                    if (t != null) {
                        s += " " + formatter.format(t);
                    }
                }
                if (labelGenerationCheckbox.getState()) {
                    s += " " + event.getGeneration();
                }
                if (labelCreatorCheckbox.getState()) {
                    s += " " + event.getCreatorId(); // ID number of member who created it
                }
                if (s != "") {
                    final Rectangle2D rect = fm.getStringBounds(s, g);
                    final int x = (int) (xpos(event) - rect.getWidth() / 2. - fa / 4.);
                    final int y = (int) (ypos(event) + rect.getHeight() / 2. - fd / 2);
                    g.setColor(LABEL_OUTLINE);
                    g.drawString(s, x - 1, y - 1);
                    g.drawString(s, x + 1, y - 1);
                    g.drawString(s, x - 1, y + 1);
                    g.drawString(s, x + 1, y + 1);
                    g.setColor(color);
                    g.drawString(s, x, y);
                }
            }
        }
    }

    /**
     * This is just for debugging: it allows the app to run in Eclipse. If the config.txt exists and lists a
     * particular SwirldMain class as the one to run, then it can run in Eclipse (with the green triangle
     * icon).
     *
     * @param args
     * 		these are not used
     */
    public static void main(final String[] args) {
        Browser.parseCommandLineArgsAndLaunch(args);
    }

    /** Fill in the names array, with the name of each member. Also set numColumns and numMembers. */
    private void calcNames() {
        final AddressBook addressBook = platform.getAddressBook();
        numColumns = addressBook.getSize();
        if (numColumns != numMembers) {
            numMembers = numColumns;
            names = new String[numColumns];
            for (int i = 0; i < numColumns; i++) {
                names[i] = addressBook.getAddress(i).getNickname();
            }
        }
    }

    // ////////////////////////////////////////////////////////////////////
    // the following are the methods required by the SwirldState interface
    // ////////////////////////////////////////////////////////////////////

    @Override
    public void init(final Platform platform, final NodeId id) {
        this.platform = platform;
        this.selfId = id.getId();
        final String[] parameters = ((PlatformWithDeprecatedMethods) platform).getParameters();

        SwirldsGui.setAbout(
                platform.getSelfId().getId(),
                "Hashgraph Demo v. 1.1\n" + "\n"
                        + "trans/sec = # transactions added to the hashgraph per second\n"
                        + "events/sec = # events added to the hashgraph per second\n"
                        + "duplicate events = percentage of events a member receives that they already know.\n"
                        + "bad events/sec = number of events per second received by a member that are invalid.\n"
                        + "propagation time = average seconds from creating a new event to a given member receiving it.\n"
                        + "create to consensus = average seconds from creating a new event to knowing its consensus order.\n"
                        + "receive to consensus = average seconds from receiving an event to knowing its consensus order.\n"
                        + "Witnesses are colored circles, non-witnesses are black/gray.\n"
                        + "Dark circles are part of the consensus, light are not.\n"
                        + "Fame is true for green, false for blue, unknown for red.\n");
        window = SwirldsGui.createWindow(platform, false); // Uses BorderLayout. Size is chosen by the Platform
        window.setLayout(new GridBagLayout()); // use a layout more powerful than BorderLayout
        int p = 0; // which parameter to use
        final BiFunction<Integer, String, Checkbox> cb = (n, s) -> new Checkbox(
                s, null, parameters.length > n && parameters[n].trim().equals("1"));

        // Skip the first parameter.
        // The first parameter used to be used to enable/disable slow sync, which is no longer supported.
        p++;

        freezeCheckbox = cb.apply(p++, "Freeze: don't change this window");
        simpleColorsCheckbox = cb.apply(p++, "Simple colors: blue for consensus, green for not");
        labelRoundCheckbox = cb.apply(p++, "Labels: Round created");
        labelRoundRecCheckbox = cb.apply(p++, "Labels: Round received (consensus)");
        labelConsOrderCheckbox = cb.apply(p++, "Labels: Order (consensus)");
        labelConsTimestampCheckbox = cb.apply(p++, "Labels: Timestamp (consensus)");
        labelGenerationCheckbox = cb.apply(p++, "Labels: Generation");
        labelCreatorCheckbox = cb.apply(p++, "Labels: Creator ID");

        eventLimit = new TextField(parameters.length <= p ? "" : parameters[p].trim(), 5);

        final GridBagConstraints constr = new GridBagConstraints();
        constr.fill = GridBagConstraints.NONE; // don't stretch components
        constr.gridwidth = GridBagConstraints.REMAINDER; // each component uses all cells in a row
        constr.anchor = GridBagConstraints.WEST; // left align each component in its cell
        constr.weightx = 0; // don't put extra space in the middle
        constr.weighty = 0;
        constr.gridx = 0; // start in upper-left cell
        constr.gridy = 0;
        constr.insets = new Insets(0, 10, -4, 0); // add external padding on left, remove from bottom

        final Component[] comps = new Component[] {
            freezeCheckbox,
            simpleColorsCheckbox,
            labelRoundCheckbox,
            labelRoundRecCheckbox,
            labelConsOrderCheckbox,
            labelConsTimestampCheckbox,
            labelGenerationCheckbox,
            labelCreatorCheckbox
        };
        for (final Component c : comps) {
            window.add(c, constr);
            constr.gridy++;
        }
        constr.gridwidth = 1; // each component is one cell
        window.add(new Label("Display the last "), constr);
        constr.gridx++;
        constr.insets = new Insets(0, 0, -4, 0); // don't pad on left
        window.add(eventLimit, constr);
        constr.gridx++;
        window.add(new Label(" events"), constr);
        constr.gridx = 0;
        constr.gridy++; // skip a line betwen settings and stats
        window.add(new Label(" "), constr);
        constr.gridy++;
        constr.weighty = 1.0; // give the picture all leftover space
        constr.weightx = 1.0;
        constr.fill = GridBagConstraints.BOTH; // stretch the picture to fit
        constr.gridwidth = GridBagConstraints.REMAINDER; // picture uses a whole row
        picture = new Picture();
        window.add(picture, constr);
        window.setVisible(true);
    }

    @Override
    public void run() {
        while (true) {
            if (window != null && !freezeCheckbox.getState()) {
                eventsCache = ((PlatformWithDeprecatedMethods) platform).getAllEvents();
                // after this getAllEvents call, the set of events to draw is frozen
                // for the duration of this screen redraw. But their status (consensus or not) may change
                // while it is being drawn. If an event is discarded while being drawn, then it forgets its
                // parents, and won't be drawn here.
                window.repaint();
                // the network will stop creating events if there are no user transactions. We will submit 1 byte
                // transactions in order to have events created continuously
                platform.createTransaction(new byte[1]);
            }
            try {
                Thread.sleep(screenUpdateDelay);
            } catch (final Exception e) {
            }
        }
    }

    @Override
    public SwirldState newState() {
        return new HashgraphDemoState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BasicSoftwareVersion getSoftwareVersion() {
        return softwareVersion;
    }
}
