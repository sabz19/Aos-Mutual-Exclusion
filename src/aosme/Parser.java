package aosme;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Scanner;

import aosme.Kernel.Neighbor;

public class Parser {
	
    public static boolean startsWithToken(String configPath, int id) {
        Scanner parser = null;
        try {
            parser = new Scanner(new File(configPath));
        } catch (FileNotFoundException e) {
            errExit("Configuration file not found.");
        }
        boolean globalsDone = false;
        while (parser.hasNextLine()) {
            String line = parser.nextLine();
            Scanner lineParser = new Scanner(line);
            String[] tokens = new String[4];
            try {
                tokens[0] = lineParser.next();
            } catch (NoSuchElementException e) {
                lineParser.close();
                continue;
            }
            if (tokens[0].charAt(0) == '#') {
                lineParser.close();
                continue;
            } else {
                for (int i = 1; i < 4; i++) {
                    try {
                        tokens[i] = lineParser.next();
                    } catch (NoSuchElementException e) {
                        errExit("Configuration file has bad format.");
                    }
                }
                int[] vals = new int[4];
                for (int i = 0; i < 4; i++) {
                    try {
                        vals[i] = Integer.parseInt(tokens[i]);
                    } catch (NumberFormatException e) {
                        if (i == 1) {
                            continue;
                        } else if (i == 3) {
                            int cStart = tokens[3].indexOf('#');
                            if (cStart != -1) {
                                try {
                                    vals[i] = Integer.parseInt(tokens[i].substring(0, cStart));
                                } catch (NumberFormatException f) {
                                    errExit("Configuration file has bad format.");
                                }
                            } else {
                                errExit("Configuration file has bad format.");
                            }
                        } else {
                            errExit("Configuration file has bad format.");
                        }
                    }
                }
                if (globalsDone == false) {
                    globalsDone = true;
                } else {
                    // If the node's parent is this one's ID and the node's ID is this node's ID
                    if (vals[3] == id && vals[0] == id) {
                        lineParser.close();
                        parser.close();
                        return true;
                    }
                }
            }
            lineParser.close();
        }
        parser.close();
        return false;
    }

    public static int parseChildCount(String configPath, int selfId) throws IOException {
        int childCount = 0;
        Scanner parser = null;
        try {
            parser = new Scanner(new File(configPath));
        } catch (FileNotFoundException e) {
            errExit("Configuration file not found.");
        }
        boolean globalsDone = false;
        while (parser.hasNextLine()) {
            String line = parser.nextLine();
            Scanner lineParser = new Scanner(line);
            String[] tokens = new String[4];
            try {
                tokens[0] = lineParser.next();
            } catch (NoSuchElementException e) {
                lineParser.close();
                continue;
            }
            if (tokens[0].charAt(0) == '#') {
                lineParser.close();
                continue;
            } else {
                for (int i = 1; i < 4; i++) {
                    try {
                        tokens[i] = lineParser.next();
                        /**
                         * Calling add_Neighbors method to add all neighbor information in Kernel Class
                         */
                        String split[] = tokens[i].split("\\s+");
                        int node_id = Integer.parseInt(split[0]);
                        int port = Integer.parseInt(split[2]);
                        String host_name = split[1];
                        Kernel kernel = new Kernel();
                        if(node_id != selfId)
                        	kernel.add_Neighbors(node_id,host_name,port);
                        
                        
                    } catch (NoSuchElementException e) {
                        errExit("Configuration file has bad format.");
                    }
                }
                
                int[] vals = new int[4];
                for (int i = 0; i < 4; i++) {
                    try {
                        vals[i] = Integer.parseInt(tokens[i]);
                        
                    } catch (NumberFormatException e) {
                        if (i == 1) {
                            continue;
                        } else if (i == 3) {
                            int cStart = tokens[3].indexOf('#');
                            if (cStart != -1) {
                                try {
                                    vals[i] = Integer.parseInt(tokens[i].substring(0, cStart));
                                } catch (NumberFormatException f) {
                                    errExit("Configuration file has bad format.");
                                }
                            } else {
                                errExit("Configuration file has bad format.");
                            }
                        } else {
                            errExit("Configuration file has bad format.");
                        }
                    }
                }
                if (globalsDone == false) {
                    globalsDone = true;
                } else {
                    // If the node's parent is this one's ID and the node's ID is not this node's ID
                    if (vals[3] == selfId && vals[0] != selfId) {
                        childCount++;
                    }
                }
            }
            lineParser.close();
        }
        parser.close();
        return childCount;
    }

    private static void errExit(String s) {
        System.err.println("[ERROR] " + s);
        System.exit(1);
    }
}
