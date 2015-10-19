import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is a static class that is used to encode, decode and generally work with messages
 *
 * @author Adiswhar Rishi
 */
public class MessageFormatter {
    public static int MAX_PING_SIZE = 2;
    public static int MAX_TCP_SIZE = 17;
    private static String departingPattern = "D:(\\d+)(,\\d+)+";
    private static String sRequestPattern = "SR:(\\d+)";
    private static String sResponsePattern = "Sr:(\\d+)(,\\d+)+";
    private static String fileRequestPattern = "FR:\\d+,\\d+";
    private static String fileResponsePattern = "Fr:(\\d+)(,\\d+){3}";


    /* ------------------------ UDP encoding functions ----------------------------- */

    /**
     * Creates an byte string containing the necessary ping data
     *
     * @param seqNum the seq number of the packet
     * @return the byte array
     */
    public static byte[] encodePingRequest(byte seqNum) {
        return new byte[]{seqNum, 'R'};
    }

    /**
     * Creates an byte string containing the necessary ping response data
     * <em>Fails gracefully if the request given in is of the wrong type</em>
     *
     * @param request The request for which the response must be generated
     * @return the byte array
     */
    public static byte[] encodePingResponse(byte[] request) {
        if (!isPingRequest(request)) {
            System.err.println("INVALID PING REQUEST");
        } else {
            return new byte[]{request[0], 'r'};
        }
        return null;
    }

    /**
     * Checks if request given is a ping request
     */
    public static boolean isPingRequest(byte[] request) {
        return request.length == 2 && request[1] == 'R';
    }

    /**
     * Checks if request is a ping response
     */
    public static boolean isPingResponse(byte[] request) {
        return request.length == 2 && request[1] == 'r';
    }

    /**
     * Determines what peer sent the message
     * NOTE: the socketAddress must have been received by a UDP message
     *
     * @param peerAddress the address information
     * @return The peer ID
     */
    public static int determineUdpPeer(InetSocketAddress peerAddress) {
        return peerAddress.getPort() - cdht_ex.PORT_BASE;
    }

    /**
     * Creates and returns a Ping object. The object is populated with the request data
     * The request must be a ping
     *
     * @param request     the request received
     * @param peerAddress the address the request came from
     * @return A Ping object containing the response data or NULL if wrong request type
     */
    public static PingData decodePing(byte[] request, InetSocketAddress peerAddress) {
        if (!(isPingRequest(request) || isPingResponse(request))) {
            System.err.println("Request type not recognised");
            return null;
        }
        return new Ping(request[0], MessageFormatter.determineUdpPeer(peerAddress));
    }

    /**
     * An object that simplifies data retrieval from a ping request
     */
    public interface PingData {
        byte getSequenceNumber();

        int getPeerId();
    }

    private static class Ping implements PingData {
        byte seqNumber;
        int peerId;

        public Ping(byte seqNumber, int peerId) {
            this.seqNumber = seqNumber;
            this.peerId = peerId;
        }

        @Override
        public byte getSequenceNumber() {
            return seqNumber;
        }

        @Override
        public int getPeerId() {
            return peerId;
        }
    }

    /* ------------------------ TCP encoding functions ----------------------------- */

    /**
     * Determines what peer sent the message
     * NOTE: the socketAddress must have been received by a UDP message
     *
     * @param request the request given by the peer
     * @return The peer ID
     */
    public static int determineTcpPeer(byte[] request) {
        String input = new String(request);
        input = input.trim();
        Matcher m = Pattern.compile("\\d+").matcher(input);
        m.find();
        return Integer.parseInt(m.group());
    }

    public static byte[] encodeDepartingMessage(int peerId, List<Integer> successors) {
        String s = "D:" + peerId;
        for (int i : successors) {
            s += "," + i;
        }
        return s.getBytes();
    }

    public static boolean isDepartingMessage(byte[] data) {
        String input = new String(data);
        input = input.trim();
        return input.matches(departingPattern);
    }

    /**
     * Returns an ArrayList containing decoded info
     * [0] -> sending peer ID
     * [1] -> successor 1
     * [2] -> successor 2
     * NOTE: The array may contain 1 or 2 successors
     */
    public static ArrayList<Integer> decodeDepartingMessage(byte[] data) {
        String input = new String(data).trim();
        Pattern p = Pattern.compile("\\d+");
        Matcher m = p.matcher(input);
        ArrayList<Integer> r = new ArrayList<Integer>();
        while (m.find()) {
            r.add(Integer.parseInt(m.group()));
        }
        return r;
    }

    public static byte[] encodeSuccessorRequest(int requestingPeer) {
        return ("SR:" + requestingPeer).getBytes();
    }

    public static boolean isSuccessorRequest(byte[] request) {
        String input = new String(request);
        input = input.trim();
        return input.matches(sRequestPattern);
    }

    public static byte[] encodeSuccessorResponse(int peerId, List<Integer> successors) {
        String s = "Sr:" + peerId;
        for (int i : successors) {
            s += "," + i;
        }
        return s.getBytes();
    }

    /**
     * Returns an ArrayList containing decoded info
     * [0] -> sending peer ID
     * [1] -> successor 1
     * [2] -> successor 2
     * NOTE: The array may contain 1 or 2 successors
     */
    public static ArrayList<Integer> decodeSuccessorResponse(byte[] data) {
        String input = new String(data);
        Matcher m = Pattern.compile("\\d+").matcher(input);
        ArrayList<Integer> r = new ArrayList<Integer>();
        while (m.find()) {
            r.add(Integer.parseInt(m.group()));
        }
        return r;
    }

    public static boolean isSuccessorResponse(byte[] request) {
        String input = new String(request);
        input = input.trim();
        return input.matches(sResponsePattern);
    }

    public static byte[] encodeFileRequest(int peerId, String filename) {
        return ("FR:" + peerId + "," + filename).getBytes();
    }

    public static byte[] encodeFileResponse(int peerId, String filename, boolean hasFile, int requestingPeer) {
        return ("Fr:" + peerId + "," + filename + "," + ((hasFile) ? "1" : "0") + "," + requestingPeer).getBytes();
    }

    public static boolean isFileRequest(byte[] request) {
        String input = new String(request);
        input = input.trim();
        return input.matches(fileRequestPattern);
    }

    public static boolean isFileResponse(byte[] request) {
        String input = new String(request);
        input = input.trim();
        return input.matches(fileResponsePattern);
    }

    /**
     * The array returned is -
     * [0] -> requesting peer
     * [1] -> file name
     *
     * @param request the request
     */
    public static ArrayList<Integer> decodeFileRequest(byte[] request) {
        String input = new String(request);
        input = input.trim();
        Matcher m = Pattern.compile("\\d+").matcher(input);
        ArrayList<Integer> r = new ArrayList<Integer>();
        while (m.find()) {
            r.add(Integer.parseInt(m.group()));
        }
        return r;
    }

    /**
     * The array returned is -
     * [0] -> responding peer
     * [1] -> file name (un-hashed)
     * [2] -> 1 | 0 (based on success or failure, NOTE - unnecessary)
     * [3] -> requesting peer (the peer who started the request)
     *
     * @param request the request
     */
    public static ArrayList<Integer> decodeFileResponse(byte[] request) {
        String input = new String(request);
        input = input.trim();
        Matcher m = Pattern.compile("\\d+").matcher(input);
        ArrayList<Integer> r = new ArrayList<Integer>();
        while (m.find()) {
            r.add(Integer.parseInt(m.group()));
        }
        return r;
    }
}