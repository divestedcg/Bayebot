/*
 * Copyright (c) 2021 Divested Computing Group
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import net.sf.classifier4J.ClassifierException;
import net.sf.classifier4J.bayesian.BayesianClassifier;
import net.sf.classifier4J.vector.VectorClassifier;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.Room;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.Presence;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;

public class Main {

    private static boolean allowedToReport = true;
    private static final ArrayList<String> notifiedSpammer = new ArrayList<String>();
    private static final ArrayList<String> notSpammer = new ArrayList<String>();
    private static final Jaxmpp bot = new Jaxmpp();
    private static final VectorClassifier vc = new VectorClassifier();
    private static final BayesianClassifier bc = new BayesianClassifier();
    private static final String defaultCategory = "DEFAULT";
    private static final String joiningNickname = "bayebot";
    private static final double flagThresholdBC = 0.7;
    private static final double flagThresholdVC = 0.9;

    private static String botAccount = "";
    private static String botAccountPassword = "";
    private static File cfgRooms;

    public static void main(String[] args) {
        boolean fatal = false;
        try {
            File configDirectory = new File(args[0]);
            if (!configDirectory.exists() || !configDirectory.isDirectory()) {
                System.out.println("[INIT] Invalid config directory path");
                System.exit(1);
            }
            File dbSpam = new File(configDirectory + "/Messages-Spam.txt");
            if (dbSpam.exists()) {
                populateBadMessageArray(dbSpam);
            } else {
                dbSpam.createNewFile();
                System.out.println("[INIT] Spam message database doesn't exist, creating, please populate.");
                fatal = true;
            }
            File dbKnownGood = new File(configDirectory + "/Messages-KnownGood.txt");
            if (dbKnownGood.exists()) {
                populateGoodMessages(dbKnownGood);
            } else {
                dbKnownGood.createNewFile();
                System.out.println("[INIT] Known good message database doesn't exist, creating, please populate.");
                fatal = true;
            }
            cfgRooms = new File(configDirectory + "/Rooms.txt");
            if (!cfgRooms.exists()) {
                cfgRooms.createNewFile();
                PrintWriter out = new PrintWriter(cfgRooms, "UTF-8");
                out.println("ontopic@conference.example.org");
                out.println("offtopic@conference.example.org");
                out.close();
                System.out.println("[INIT] Room list doesn't exist, creating, please populate.");
                fatal = true;
            }
            File cfgAccount = new File(configDirectory + "/Account.txt");
            if (cfgAccount.exists()) {
                parseAccountFromFile(cfgAccount);
                if (botAccount.length() == 0 || botAccountPassword.length() == 0) {
                    fatal = true;
                }
            } else {
                cfgAccount.createNewFile();
                PrintWriter out = new PrintWriter(cfgAccount, "UTF-8");
                out.println("bayebot@example.org");
                out.println("password");
                out.close();
                System.out.println("[INIT] Room list doesn't exist, creating, please populate.");
                fatal = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (fatal) {
            System.out.println("[INIT] Input requirements unsatisfied. Exiting!");
            System.exit(1);
        }

        try {
            Presence.initialize(bot);
            bot.getModulesManager().register(new MucModule());
            //bot.getSessionObject().setUserProperty(StreamManagementModule.STREAM_MANAGEMENT_DISABLED_KEY, true);
            bot.getProperties().setUserProperty(SessionObject.USER_BARE_JID, BareJID.bareJIDInstance(botAccount));
            bot.getProperties().setUserProperty(SessionObject.PASSWORD, botAccountPassword);

            bot.getEventBus().addHandler(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class, new MucModule.MucMessageReceivedHandler() {
                @Override
                public void onMucMessageReceived(SessionObject sessionObject, Message message, Room room, String nickname, Date timestamp) {
                    try {
                        if (message.getBody() != null) {
                            String messageTxt = message.getBody().toString();
                            handleBotAction(messageTxt, room);

                            double scoreVC = vc.classify(defaultCategory, messageTxt);
                            double scoreBC = bc.classify(messageTxt);
                            System.out.println("[DEBUG] " + scoreBC + " " + scoreVC + " " + nickname + "@" + room.getRoomJid() + ": " + messageTxt);
                            if ((scoreBC >= flagThresholdBC || scoreVC >= flagThresholdVC) && messageTxt.length() >= 10) {
                                if (allowedToReport && !notifiedSpammer.contains(nickname) && !notSpammer.contains(nickname)) {
                                    notifiedSpammer.add(nickname);
                                    String detectMessage = "Potential spam detected from " + nickname + ", score: " + scoreBC + " (bayesian) & " + scoreVC + " (vector)";
                                    System.out.println("[SPAM] " + detectMessage);
                                    bot.getModule(MucModule.class).getRoom(room.getRoomJid()).sendMessage(detectMessage);
                                }
                            }
                        }
                    } catch (ClassifierException | JaxmppException e) {
                        e.printStackTrace();
                    }
                }
            });

            bot.login();
            int tryCounter = 0;
            while (!bot.isConnected() && tryCounter <= 10) {
                Thread.sleep(1000);
                tryCounter++;
            }
            if (bot.isConnected()) {
                System.out.println("[INIT] Connected");
                connectToRooms(cfgRooms);
            } else {
                System.out.println("[INIT] Unable to connect within 10 seconds.");
                System.exit(1);
            }
        } catch (JaxmppException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<String> readFileToArray(File file) {
        ArrayList<String> contents = new ArrayList<>();
        if (file.exists() && file.canRead()) {
            try {
                Scanner reader = new Scanner(file);
                while (reader.hasNextLine()) {
                    contents.add(reader.nextLine());
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return contents;
    }

    public static void populateBadMessageArray(File db) {
        int count = 0;
        ArrayList<String> knownBad = readFileToArray(db);
        try {
            for (String line : knownBad) {
                if (line.length() >= 20 && !line.startsWith("> ") && !line.startsWith("https://")) { //KEEP THIS IN SYNC
                    vc.teachMatch(defaultCategory, line);
                    bc.teachMatch(defaultCategory, line);
                    count++;
                }
            }
        } catch (ClassifierException e) {
            e.printStackTrace();
        }
        System.out.println("[DATABASE] Added bad matches count: " + count);
    }

    public static void populateGoodMessages(File db) {
        int count = 0;
        ArrayList<String> knownGood = readFileToArray(db);
        try {
            for (String line : knownGood) {
                if (line.length() > 20 && !line.startsWith("> ") && !line.startsWith("https://")) { //KEEP THIS IN SYNC
                    bc.teachNonMatch(defaultCategory, line);
                    count++;
                }
            }
        } catch (ClassifierException e) {
            e.printStackTrace();
        }
        System.out.println("[DATABASE] Added good matches count: " + count);
    }

    public static void connectToRooms(File cfgRooms) {
        int count = 0;
        ArrayList<String> rooms = readFileToArray(cfgRooms);
        try {
            for (String room : rooms) {
                if (room.contains("@")) {
                    String[] roomSplit = room.split("@");
                    bot.getModule(MucModule.class).join(roomSplit[0], roomSplit[1], joiningNickname);
                    count++;
                }
            }
        } catch (JaxmppException e) {
            e.printStackTrace();
        }
        System.out.println("[BOT] Connecting to " + count + " rooms");
    }

    public static void parseAccountFromFile(File cfgAccount) {
        ArrayList<String> account = readFileToArray(cfgAccount);
        botAccount = account.get(0);
        botAccountPassword = account.get(1);
        System.out.println("[BOT] Operating under " + botAccount);
    }

    public static void handleBotAction(String message, Room room) {
        try {
            if (message.equalsIgnoreCase("bayebot stop") && allowedToReport) {
                allowedToReport = false;
                bot.getModule(MucModule.class).getRoom(room.getRoomJid()).sendMessage("Disabled reporting");
            }
            if (message.equalsIgnoreCase("bayebot start") && !allowedToReport) {
                allowedToReport = true;
                bot.getModule(MucModule.class).getRoom(room.getRoomJid()).sendMessage("Enabled reporting");
            }
            if (message.equalsIgnoreCase("bayebot status")) {
                bot.getModule(MucModule.class).getRoom(room.getRoomJid()).sendMessage("Still here...");
            }
            if (message.equalsIgnoreCase("bayebot halt")) {
                bot.getModule(MucModule.class).getRoom(room.getRoomJid()).sendMessage("Goodbye!");
                bot.disconnect();
                System.exit(0);
            }
        } catch (JaxmppException e) {
            e.printStackTrace();
        }
    }
}
