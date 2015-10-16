import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Represents a peer in the Circular DHT
 * Usage: java cdht <ID> <SUCCESSOR1> <SUCCESSOR2>
 * The peer will find and keep track of its two successors
 * A peer initialized with id = n will have a ping server at UDP port 50000 + n
 */
public class cdht {
    private final int ID;
    private final int successor1_ID;
    private final int successor2_ID;
    public static final int PORT_BASE = 50000;
    //time between each successive ping (seconds)
    private final int PING_RATE;
    public SuccessorManager successorManager;
    private Timer successorPingTimer;
    private PingServer udpServer;
    private TcpServer tcpServer;
    private ExecutorService threadManager;

    public PingServer getUdpServer() {
        return udpServer;
    }

    /**
     * Initialize Peer with its ID and two successors in the CDHT
     *
     * @param self_ID range 0-255
     * @param s1_ID   range 0-255
     * @param s2_ID   range 0-255
     */
    public cdht(int self_ID, int s1_ID, int s2_ID) {
        successor1_ID = s1_ID;
        successor2_ID = s2_ID;
        ID = self_ID;
        PING_RATE = 5; //seconds
        successorPingTimer = new Timer("Successor Ping Timer");
        udpServer = new PingServer(this);
        tcpServer = new TcpServer(this);
        successorManager = new SuccessorManager(this);
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
                }
                System.out.println(line);
            }
            self.udpServer.close();
            self.tcpServer.close();
            self.successorPingTimer.cancel();
            self.threadManager.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initialize() {
        successorPingTimer.scheduleAtFixedRate(successorManager, 0, PING_RATE * 1000);
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

    public int getSuccessor1UdpPort() {
        return PORT_BASE + successor1_ID;
    }

    public int getSuccessor2UdpPort() {
        return PORT_BASE + successor2_ID;
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

    public int getSuccessor1_ID() {
        return successor1_ID;
    }

    public int getSuccessor2_ID() {
        return successor2_ID;
    }
}