import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.TimerTask;

public class SuccessorManager extends TimerTask {
    private ArrayList<Successor> successors;
    private final int MAX_FAILS = 4;
    private cdht employer;
    //these are a list of ping Sequence numbers that are yet to be acknowledged
    LinkedList<Integer> pastPingRequests;


    public SuccessorManager(cdht employer, int successor1_ID, int successor2_ID) {
        this.employer = employer;
        successors = new ArrayList<Successor>();
        successors.add(new Successor(successor1_ID));
        successors.add(new Successor(successor2_ID));
        pastPingRequests = new LinkedList<Integer>();
    }

    @Override
    public void run() {
        analyseFailedPings();
        for (Successor s : successors) {
            employer.getUdpServer().sendDatagram(MessageFormatter.encodePingRequest(s.seqNum),
                    new InetSocketAddress("localhost", s.getUdpPort()));
            s.unackedPings.add(s.seqNum);
            s.incSeqNum();
        }
    }

    /**
     * This method does the necessary actions if a ping fails
     * Also detects successor death
     */
    private void analyseFailedPings() {
        for (Successor s : successors) {
            if (s.isDead()) {
                handleSuccessorDeath(s.ID);
            }
        }
    }

    /**
     * Called automatically, no need to access this method
     *
     * @param successorId One of the two ID's from the outer class
     */
    private void handleSuccessorDeath(int successorId) {
        System.out.println("Peer " + successorId + " is no longer alive");
    }

    /**
     * This method should be called everytime a ping response is received.
     * This method is used to keep track of which peer is alive
     * @param peerId the peer id
     * @param seqNumber the sequence number in the message
     */
    public void registerPingResponse(int peerId, byte seqNumber) {
        for (Successor s : successors) {
            if (s.ID == peerId) {
                if (!s.unackedPings.contains(seqNumber)) {
                    System.out.println("Sequence number " + seqNumber + " does not exist for peer " + peerId);
                    System.out.println(s.unackedPings);
                } else {
                    s.unackedPings.subList(0, s.unackedPings.indexOf(seqNumber) + 1).clear();
                }
            }
        }
    }

    /**
     * This method should be called every time a ping request is received.
     * This method keeps track of predecessor peers
     * @param peerId
     */
    public void registerPingRequest (int peerId) {
        if (pastPingRequests.size() > 20) {
            pastPingRequests.removeFirst();
        }
        pastPingRequests.add(peerId);
    }

    public int getSuccessor1_ID() {
        return successors.get(0).ID;
    }

    public int getSuccessor2_ID() {
        return successors.get(0).ID;
    }

    public int[] getPredecessors() {
        int i = pastPingRequests.size() - 1;
        int[] r = new int[2];
        r[0] = pastPingRequests.get(i);
        for (;i >= 0; i--) {
            if (pastPingRequests.get(i) != r[0]) {
                r[1] = pastPingRequests.get(i);
            }
        }
        System.out.print("Sending departing message to - " + r[0] + " + " + r[1]);
        return r;
    }

    private class Successor {
        public int ID;
        public ArrayList<Byte> unackedPings;
        private byte seqNum;

        public Successor (int ID) {
            this.ID = ID;
            unackedPings = new ArrayList<Byte>();
            seqNum = 0;
        }

        public void incSeqNum () {
            seqNum++;
            if (seqNum == -128) seqNum = 0;
        }

        public int getUdpPort() {
            return cdht.PORT_BASE + ID;
        }

        public boolean isDead() {
            return unackedPings.size() >= MAX_FAILS;
        }

    }
}