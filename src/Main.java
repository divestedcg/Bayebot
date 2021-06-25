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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
    private static final ArrayList<String> classifiedUsers = new ArrayList<>();
    private static final HashMap<String, String> userMessageLog = new HashMap<>();

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
            File userDB = new File(configDirectory + "/Users/");
            if (userDB.exists()) {
                populateUserLog(userDB);
            } else {
                userDB.mkdir();
                System.out.println("[INIT] User database doesn't exist, creating, please populate to use the identifier feature.");
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
            bot.getProperties().setUserProperty(SessionObject.USER_BARE_JID, BareJID.bareJIDInstance(botAccount));
            bot.getProperties().setUserProperty(SessionObject.PASSWORD, botAccountPassword);

            bot.getEventBus().addHandler(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class, new MucModule.MucMessageReceivedHandler() {
                @Override
                public void onMucMessageReceived(SessionObject sessionObject, Message message, Room room, String nickname, Date timestamp) {
                    try {
                        if (message.getBody() != null) {
                            String fakeJID = nickname + "@" + room.getRoomJid();
                            String messageTxt = message.getBody().toString();

                            String userMessagePrevious = "";
                            if (userMessageLog.containsKey(fakeJID)) {
                                userMessagePrevious = userMessageLog.get(fakeJID);
                            }
                            if (checkLine(messageTxt)) {
                                userMessageLog.put(fakeJID, userMessagePrevious + "\n" + messageTxt);
                            }

                            handleBotAction(messageTxt, room);

                            double scoreVC = vc.classify(defaultCategory, messageTxt);
                            double scoreBC = bc.classify(messageTxt);
                            System.out.println("[DEBUG] " + scoreBC + " " + scoreVC + " [" + identifyUser(userMessageLog.get(fakeJID)) + "] " + fakeJID + ": " + messageTxt);
                            if ((scoreBC >= flagThresholdBC || scoreVC >= flagThresholdVC) && messageTxt.length() >= 10) {
                                if (allowedToReport && !notifiedSpammer.contains(nickname) && !notSpammer.contains(nickname)) {
                                    notifiedSpammer.add(nickname);
                                    String detectMessage = "Potential spam detected from " + nickname + ", score: " + scoreBC + " (bayesian) & " + scoreVC + " (vector)";
                                    System.out.println("[SPAM] " + detectMessage);
                                    bot.getModule(MucModule.class).getRoom(room.getRoomJid()).sendMessage(detectMessage); //KEEP COMMENTED WHEN TESTING
                                }
                            }
                        }
                    } catch (ClassifierException | JaxmppException e) {
                        //e.printStackTrace();
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
                while(true) { //XXX: This shouldn't be necessary, but my connection is killed without it?
                    Thread.sleep(1000);
                    bot.keepalive();
                }
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
        for (String line : knownBad) {
            if (checkLine(line)) {
                try {
                    vc.teachMatch(defaultCategory, line);
                    bc.teachMatch(defaultCategory, line);
                    count++;
                } catch (Exception e) {
                    //e.printStackTrace();
                }
            }
        }

        System.out.println("[DATABASE] Added bad matches count: " + count);
    }

    public static void populateGoodMessages(File db) {
        int count = 0;
        ArrayList<String> knownGood = readFileToArray(db);
        for (String line : knownGood) {
            if (checkLine(line)) {
                try {
                    bc.teachNonMatch(defaultCategory, line);
                    count++;
                } catch (Exception e) {
                    //e.printStackTrace();
                }
            }
        }
        System.out.println("[DATABASE] Added good matches count: " + count);
    }

    public static void populateUserLog(File userDB) {
        int countUser = 0;
        int countLine = 0;
        for (File file : Objects.requireNonNull(userDB.listFiles())) {
            String user = file.getName().replaceAll(".txt", "");
            ArrayList<String> userMessages = readFileToArray(file);
            for (String line : userMessages) {
                if (checkLine(line)) {
                    try {
                        countLine++;
                        vc.teachMatch(user, line);
                    } catch (Exception e) {
                        //e.printStackTrace();
                    }
                }
            }

            classifiedUsers.add(user);
            countUser++;
        }
        System.out.println("[DATABASE] Added " + countUser + " users, with " + countLine + " messages");
    }

    public static boolean checkLine(String line) {
        return line.length() >= 20 && !line.startsWith("> ") && !line.startsWith("https://") && StandardCharsets.US_ASCII.newEncoder().canEncode(line);
    }

    public static String identifyUser(String message) {
        double likelyMatchPercent = 0.5;
        String likelyMatchUser = "UNKNOWN";
        try {
            for (String user : classifiedUsers) {
                double matchPercent = vc.classify(user, message);
                if (matchPercent > likelyMatchPercent) {
                    likelyMatchPercent = matchPercent;
                    likelyMatchUser = user;
                }
            }
        } catch (Exception e) {
            //e.printStackTrace();
        }
        return likelyMatchUser;
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
            if (message.startsWith("bayebot identify ")) {
                String[] msgSplit = message.split(" ");
                //bot.getModule(MucModule.class).getRoom(room.getRoomJid()).sendMessage("Potential match: " + identifyUser(msgSplit[2] + "@" + room.getRoomJid()));
            }
        } catch (JaxmppException e) {
            e.printStackTrace();
        }
    }

    public static String arrayToStringChecked(ArrayList<String> array) {
        String result = "";
        for(String line : array) {
            if(checkLine(line)) {
                result += "\n" + line;
            }
        }
        return result;
    }

    public static String identifyOneOff(String user, File file) {
        try {
            return user + " vs. " + file.getName().replaceAll(".txt", "") + ": " + vc.classify(user, arrayToStringChecked(readFileToArray(file)));
        } catch (ClassifierException e) {
            e.printStackTrace();
        }
        return "Unknown";
    }
}
