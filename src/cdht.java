import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a peer in the Circular DHT
 * Usage: java cdht <ID> <SUCCESSOR1> <SUCCESSOR2>
 * The peer will find and keep track of its two successors
 * A peer initialized with id = n will have a ping server at UDP port 50000 + n
 *
 * @author Adiswhar Rishi
 */
public class cdht {
    public final int ID;

    public static final int PORT_BASE = 50000;
    //time between each successive ping (seconds)
    private final int PING_RATE;
    public PeerTracker peerTracker;
    private Timer successorPingTimer;
    private PingServer udpServer;
    private TcpServer tcpServer;
    private ExecutorService threadManager;

    public PingServer getUdpServer() {
        return udpServer;
    }

    public TcpServer getTcpServer() {
        return tcpServer;
    }

    /**
     * Initialize Peer with its ID and two successors in the CDHT
     *
     * @param self_ID range 0-255
     * @param s1_ID   range 0-255
     * @param s2_ID   range 0-255
     */
    public cdht(int self_ID, int s1_ID, int s2_ID) {
        ID = self_ID;
        PING_RATE = 1; //seconds
        successorPingTimer = new Timer("Successor Ping Timer");
        udpServer = new PingServer(this);
        tcpServer = new TcpServer(this);
        peerTracker = new PeerTracker(this, s1_ID, s2_ID);
        threadManager = Executors.newFixedThreadPool(2);
    }

    public static void main(String[] args) {
        if (!verifyArgs(args)) System.exit(1);
        cdht self = new cdht(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        self.initialize();
        String line;
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            while ((line = reader.readLine()) != null) {
                if (line.equals("quit")) {
                    break;
                } else if (line.matches("request\\s+\\d+")) {
                    Matcher m = Pattern.compile("\\d+").matcher(line);
                    m.find();
                    String filename = m.group();
                    byte[] request = MessageFormatter.encodeFileRequest(self.ID, filename);
                    InetSocketAddress address = new InetSocketAddress("localhost",
                            self.peerTracker.getSuccessorId(1) + PORT_BASE);
                    self.tcpServer.send(request, address);
                    System.out.println("File request message for " + filename + " has been sent to my successor.");
                } else {
                    System.out.println(line);
                }
            }
            self.shutdown();
            self.udpServer.close();
            self.tcpServer.close();
            self.successorPingTimer.cancel();
            self.threadManager.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initialize() {
        successorPingTimer.scheduleAtFixedRate(peerTracker, 0, PING_RATE * 1000);
        threadManager.execute(udpServer);
        threadManager.execute(tcpServer);
    }


    /**
     * Returns the udp port number that this peer would like to be bound to
     */
    public int getUdpPort() {
        return PORT_BASE + ID;
    }

    public int getTcpPort() {
        return PORT_BASE + ID;
    }

    /*
    Function for verifying that the application was called correctly
     */
    private static boolean verifyArgs(String[] args) {
        boolean result = true;
        if (args.length != 3) {
            result = false;
            System.err.println("Usage: java cdht <ID> <SUCCESSOR1> <SUCCESSOR2>");
        }
        int count = 0;
        while (count < args.length && result) {
            String arg = args[count];
            if (arg.length() > 3) { // a fail fast for efficiency
                result = false;
                System.err.printf("Argument \'%s\' has more than 3 characters and hence is out of range", arg);
                continue;
            }
            try {
                int num = Integer.parseInt(arg);
                if (num < 0 || num > 255) { //range check
                    result = false;
                    System.err.printf("Argument \'%s\' is out of range", arg);
                }
            } catch (NumberFormatException ex) {
                //The string is not a number
                result = false;
                System.err.printf("Argument \'%s\' is not an integer in the range [0,255]", arg);
            }
            count++;
        }
        return result;
    }


    /**
     * Initiates a graceful quit.
     * This peer will sent successor information to its predecessors
     */
    private void shutdown() {
        ArrayList<Integer> predecessorIds = peerTracker.getPredecessors();
        ArrayList<Integer> successors = peerTracker.getSuccessors();
        if (predecessorIds == null || successors == null) return;
//        System.out.println("Sending quit to - " + predecessorIds);
        byte[] data = MessageFormatter.encodeDepartingMessage(ID, successors);
        for (int i = 0; i < predecessorIds.size(); i++) {
            InetSocketAddress address = new InetSocketAddress("localhost", PORT_BASE + predecessorIds.get(i));
            tcpServer.send(data, address);
        }
    }
}