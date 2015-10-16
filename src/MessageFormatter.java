import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageFormatter {
    public static int MAX_PING_SIZE = 2;
    public static int MAX_TCP_SIZE = 9;
    private static String departingPattern = "D:(\\d+),(\\d+)";

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
     *
     * @param peerAddress the address information
     * @return The peer ID
     */
    public static int determinePeer(InetSocketAddress peerAddress) {
        return peerAddress.getPort() - cdht.PORT_BASE;
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
        return new Ping(request[0], MessageFormatter.determinePeer(peerAddress));
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

    public static byte[] encodeDepartingMessage (int successor1, int successor2) {
        return ("D:"+successor1+","+successor2).getBytes();
    }

    public static boolean isDepartingMessage (byte[] data) {
        String input = new String(data);
        input = input.trim();
        return input.matches(departingPattern);
    }

    public static int[] decodeDepartingMessage (byte[] data) {
        String input = new String(data).trim();
        System.out.println(input.length());
        Matcher m = Pattern.compile(departingPattern).matcher(input);
        System.out.println(m.find());
        return new int[] {Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2))};
    }
}