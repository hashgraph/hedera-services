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

package com.swirlds.test.game;
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

import static com.swirlds.platform.gui.SwirldsGui.createWindow;

import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.Browser;
import com.swirlds.platform.ExternalIpAddress;
import com.swirlds.platform.IpAddressStatus;
import com.swirlds.platform.Network;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.gui.SwirldsGui;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * This game allows the user to wander around the board using the arrow keys. The first user to hit the goal
 * gets one point, and the goal resets to a pseudorandom location.
 *
 * The transactions are each 3 longs, giving the speed and (x,y) location of the player's destination.
 *
 * The user can hit the spacebar to start automatic movement, which is on by default. Hitting any arrow key
 * will turn off automatic movement.
 */
public class GameTestMain implements SwirldMain {
    /** bit in arrowsDown that is set while the LEFT arrow is down */
    static final int LEFT_BIT = 1;
    /** bit in arrowsDown that is set while the RIGHT arrow is down */
    static final int RIGHT_BIT = 2;
    /** bit in arrowsDown that is set while the UP arrow is down */
    static final int UP_BIT = 4;
    /** bit in arrowsDown that is set while the DOWN arrow is down */
    static final int DOWN_BIT = 8;

    /** delay after each time through the main game loop in milliseconds (which updates screen, etc) */
    private final int gameLoopDelay = 100;
    /** the app is run by this */
    public Platform platform;
    /** ID number for this member */
    public long selfId;
    /** so user can use arrows and spacebar */
    GuiKeyListener keyListener = new GuiKeyListener();
    /** the entire window */
    JFrame frame;
    /** should computer play for the user? */
    boolean automove = true;
    /** previous value of automove (used to trigger a transaction when it is turned on) */
    boolean prevAutomove = true;
    /** the internal IP port for this machine */
    int portInternal = -1;
    /** the external IP port for this machine */
    int portExternal = -1;
    /** the internal IP address for this machine */
    String ipInternal = "";
    /** the external IP address for this machine (or an error message if none) */
    ExternalIpAddress ipExternal;
    /** which keys are down, so keyIsDown[KeyEvent.VK_LEFT] is true while the left arrow is held down */
    boolean[] keyIsDown = new boolean[65536];
    /** which arrow keys are now down, so bits 0,1,2,3 are set while left,right,up,down are down */
    int arrowsDown = 0;
    /** which arrow keys were held down during the last screen update */
    int prevArrowsDown = 0;
    /** current x location of the goal (where the first player to reach it gets a point) */
    long prevXGoal = -1;
    /** current y location of the goal (where the first player to reach it gets a point) */
    long prevYGoal = -1;

    private static final BasicSoftwareVersion softwareVersion = new BasicSoftwareVersion(1);

    /**
     * Listen for input from the keyboard, and remember the set of keys currently held down. For
     * convenience, the statuses of the the 4 arrow keys are packed into 4 bits in an integer, to make it
     * easy to check when they change.
     *
     * There is a severe bug in Java for MacOS 10.13 (since at least 2016), where Java games will fail if
     * users hold down the WASD keys for a few seconds, but it's ok to hold down arrow keys or TFGH.
     * (https://community.oracle.com/thread/4115318). A command line workaround
     * (https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8167263):
     *
     * COMMAND LINE: defaults write -g ApplePressAndHoldEnabled -bool false
     */
    private class GuiKeyListener implements KeyListener {
        @Override
        public void keyPressed(KeyEvent e) {
            int t = arrowsDown;
            keyIsDown[e.getKeyCode()] = true;
            arrowsDown = (keyIsDown[KeyEvent.VK_LEFT] ? LEFT_BIT : 0)
                    | (keyIsDown[KeyEvent.VK_RIGHT] ? RIGHT_BIT : 0)
                    | (keyIsDown[KeyEvent.VK_UP] ? UP_BIT : 0)
                    | (keyIsDown[KeyEvent.VK_DOWN] ? DOWN_BIT : 0);
            if (arrowsDown != t) {
                // printArrowsDown();
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            int t = arrowsDown;
            keyIsDown[e.getKeyCode()] = false;
            arrowsDown = (keyIsDown[KeyEvent.VK_LEFT] ? LEFT_BIT : 0)
                    | (keyIsDown[KeyEvent.VK_RIGHT] ? RIGHT_BIT : 0)
                    | (keyIsDown[KeyEvent.VK_UP] ? UP_BIT : 0)
                    | (keyIsDown[KeyEvent.VK_DOWN] ? DOWN_BIT : 0);
            if (arrowsDown != t) {
                // printArrowsDown();
            }
        }

        @Override
        public void keyTyped(KeyEvent e) {
            int k = e.getKeyChar();
            if (k == ' ') {
                automove = true;
            }
        }
    }

    /** print all the arrow keys currently held down (such as for debugging) */
    void printArrowsDown() {
        System.out.println("Arrows held down:      " + (((arrowsDown & LEFT_BIT) == 0) ? "  -  " : "left ")
                + (((arrowsDown & UP_BIT) == 0) ? " - " : "up ")
                + (((arrowsDown & DOWN_BIT) == 0) ? "  -  " : "down ")
                + (((arrowsDown & RIGHT_BIT) == 0) ? "  -   " : "right "));
    }

    /** the graphics area where the game board is drawn */
    private class Board extends JPanel {
        /** used for serializing */
        private static final long serialVersionUID = 1L;

        /**
         * Update the current state, display it on the screen, and perform appropriate actions. The actions
         * include creating transactions to move when arrow keys are pressed or released, and automatically
         * move if that was enabled earlier by pressing the spacebar.
         */
        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            // the following hold the state of the world, copied from GameTestState
            AddressBook addr;
            int numPlayers; // number of members. (The arrays are 1 element larger, to include the goal)
            long xBoardSize;
            long yBoardSize;
            Color colorGoal;
            Color[] colorPlayer;
            long[] xPlayer;
            long[] yPlayer;
            int[] score;
            int[] numTrans;
            long xGoal; // equals xPLayer[numPlayers]
            long yGoal; // equals yPlayer[numPlayers]

            final GameTestState state = ((PlatformWithDeprecatedMethods) platform).getState();
            try {
                // The state methods are all synchronized, but we still want to put them all in a
                // single synchronized block to ensure the state doesn't change in between them.
                // For maximum speed, put as little as possible inside the synchronized block.
                synchronized (state) {
                    // Run the simulated world to the time when an action done now will take effect.
                    // We will display the world on the screen, as it is predicted to be at that time.
                    state.advanceWorld(platform.estimateTime());

                    // get a copy of the state of the world at that time
                    addr = platform.getAddressBook();
                    numPlayers = addr.getSize();
                    xBoardSize = state.getxBoardSize();
                    yBoardSize = state.getyBoardSize();
                    colorPlayer = state.getColor();
                    xPlayer = state.getX();
                    yPlayer = state.getY();
                    score = state.getScore();
                    numTrans = state.getNumTrans();
                    xGoal = xPlayer[numPlayers];
                    yGoal = yPlayer[numPlayers];
                    colorGoal = colorPlayer[numPlayers];
                }
                // if automoving, create transactions whenever the goal moves or automove is first activated
                if (automove && (automove != prevAutomove || xGoal != prevXGoal || yGoal != prevYGoal)) {
                    // when automoving, if the goal has moved, change direction toward it,
                    // and move only 90 percent of the speed of manual, to make the game more fun. :-)
                    if (sendTransaction((long) (0.9 * state.getMaxSpeed()), xGoal, yGoal)) {
                        // only update these 3 if the submission succeeded.  Otherwise, don't update them,
                        // so this will try again next time
                        prevXGoal = xGoal;
                        prevYGoal = yGoal;
                        prevAutomove = automove;
                    }
                }
                // if any arrows have been pressed or released since last time, then perform that action
                if (arrowsDown != prevArrowsDown) {
                    // if any arrow keys have changed (pressed or released) since last update, then move
                    long xDest = xPlayer[(int) selfId];
                    long yDest = yPlayer[(int) selfId];

                    // allow movement in 8 directions,
                    // or stay still if holding no arrows, or holding LEFT and RIGHT together, etc
                    if ((arrowsDown & LEFT_BIT) != 0) {
                        xDest -= 10 * xBoardSize;
                    }
                    if ((arrowsDown & RIGHT_BIT) != 0) {
                        xDest += 10 * xBoardSize;
                    }
                    if ((arrowsDown & UP_BIT) != 0) {
                        yDest -= 10 * yBoardSize;
                    }
                    if ((arrowsDown & DOWN_BIT) != 0) {
                        yDest += 10 * yBoardSize;
                    }
                    automove = false; // hitting an arrow switches from auto to manual control
                    prevAutomove = false;
                    long speed = (arrowsDown == 0) ? 0 : state.getMaxSpeed(); // only move when arrows held down
                    sendTransaction(speed, xDest, yDest);
                }
                prevArrowsDown = arrowsDown;
            } finally {
                ((PlatformWithDeprecatedMethods) platform).releaseState();
            }

            // redraw the screen with the current state

            int textHeight = 15;
            int width = getWidth();
            int height1 = Math.max(6, numPlayers) * textHeight; // scoreboard
            int height2 = getHeight() - height1; // playing board

            int x = (int) (width * xGoal / xBoardSize);
            int y = (int) (height1 + height2 * yGoal / yBoardSize);
            int dx = width / GameTestState.SYMBOL_FRACTION; // symbol width in pixels
            int dy = width / GameTestState.SYMBOL_FRACTION; // symbol height in pixels
            g.setColor(Color.RED);
            g.fillOval(x, y, dx, dy); // outer ring around the goal
            g.setColor(Color.WHITE);
            g.fillOval(x + dx / 6, y + dy / 6, 2 * dx / 3, 2 * dy / 3); // inner ring around the goal
            g.setColor(colorGoal);
            g.fillOval(x + dx / 3, y + dy / 3, dx / 3, dy / 3); // innermost ring around the goal

            for (int i = 0; i < numPlayers; i++) {
                x = (int) (width * xPlayer[i] / xBoardSize);
                y = (int) (height1 + height2 * yPlayer[i] / yBoardSize);
                g.setColor(colorPlayer[i]);
                g.fillRect(x, y, dx, dy); // a player
                g.fillRect(
                        0, // a color box on the scoreboard
                        ((i) * textHeight),
                        textHeight,
                        textHeight);
            }

            g.setColor(Color.BLACK);
            g.setFont(new Font(Font.MONOSPACED, 12, 12));
            g.drawLine(0, height1, width, height1);

            int row = 1;
            int col = 190;
            g.drawString("Arrows move", col, row++ * textHeight - 3);
            g.drawString("Spacebar automoves", col, row++ * textHeight - 3);
            row++;
            final Address address = platform.getSelfAddress();
            if (ipInternal.equals("")) {
                ipInternal = Network.getInternalIPAddress();
                portInternal = address.getPortInternalIpv4();
            }

            g.drawString(
                    "Internal: " + (ipInternal.equals("") ? "" : ipInternal + " : " + portInternal),
                    col,
                    row++ * textHeight - 3);

            if (ipExternal == null) {
                ipExternal = Network.getExternalIpAddress();
                portExternal = address.getPortExternalIpv4();
            }

            String externalIpAddress = ipExternal.toString();
            if (ipExternal.getStatus() == IpAddressStatus.IP_FOUND) {
                externalIpAddress += " : " + portExternal;
            }

            g.drawString("External: " + externalIpAddress, col, row++ * textHeight - 3);

            for (int i = 0; i < numPlayers; i++) {
                g.drawString(
                        String.format( // scores and names on the scoreboard
                                "% 5d %-5s (trans: %d)",
                                score[i], addr.getAddress(i).getSelfName(), numTrans[i]),
                        0,
                        (int) ((i + 0.9) * textHeight));
            }
        }
    }

    /**
     * Create a new transaction and send it to the platform. This will send the transaction to the Platform,
     * which will then forward it to the SwirldState object immediately, and also send it to all the other
     * members of the community by sending them an Event containing it during syncs with them.
     *
     * @param speed
     * 		the speed to move toward the destination (in steps per millisecond), which can range from
     * 		0 to state.maxSpeed
     * @param x
     * 		the x coordinate of the destination, which can range from 0 to xBoardSize
     * @param y
     * 		the y coordinate of the destination, which can range from 0 to yBoardSize
     * @return true if it was successfully submitted. False if the platform can't currently accept transactions.
     */
    private boolean sendTransaction(long speed, long x, long y) {
        final byte[] transaction = new byte[24];
        Utilities.toBytes(speed, transaction, 0);
        Utilities.toBytes(x, transaction, 8);
        Utilities.toBytes(y, transaction, 16);
        return platform.createTransaction(transaction);
    }

    /**
     * This is just for use while developing an app. It allows the app to run in Eclipse, or other IDEs.
     * Just run the app in the IDE in the normal way, such as the green triangle button in Eclipse. That
     * will execute this method, which will call Browser.main. If the config.txt says to run this app, then
     * the Browser will instantiate a new copy of the app, and run that.
     *
     * When it is time to deploy the app, create a .jar file for it, using the normal procedure for create
     * .jar files for applications in the IDE. For example, in Eclipse it is the "export" command, and from
     * the command line it is the javatool. Then, move the .jar file to the data/apps folder, give that to
     * users along with the swirlds.jar file, and the users will be able to run the app. In that case, this
     * main() method is not called. The Browser.main will be called directly when the user runs swirlds.jar
     * by double clicking on it or by running "java -jar swirlds.jar" from the command line.
     *
     * @param args
     * 		this is not used
     */
    public static void main(String[] args) {
        // the following has the same effect as building a file named GameTest.jar then moving it to
        // sdk/data/apps then launching swirlds.jar in Java.
        Browser.launch(args);
    }

    /////////////////////////////////////////////////////////////////////

    @Override
    public void init(final Platform platform, final NodeId id) {
        this.platform = platform;
        this.selfId = id.getId();
        SwirldsGui.setAbout(platform.getSelfId().getId(), "Game Test v. 1.0\n");
        frame = createWindow(platform, false); // create the default size window, and be visible
        frame.addKeyListener(keyListener);
        frame.add(new Board());
        frame.pack();
        frame.setVisible(true);
    }

    @Override
    public void run() {
        ActionListener repaintPeriodically = evt -> updateAndRepaint();

        (new Timer(gameLoopDelay, repaintPeriodically)).start();
    }

    /**
     * This is the main event loop, called periodically (such as every 100 milliseconds) to move and update
     * the screen
     */
    public void updateAndRepaint() {
        if (frame != null) {
            frame.repaint();
        }
    }

    @Override
    public SwirldState newState() {
        return new GameTestState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BasicSoftwareVersion getSoftwareVersion() {
        return softwareVersion;
    }
}
