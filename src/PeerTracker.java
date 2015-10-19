import java.net.InetSocketAddress;
import java.util.*;

public class PeerTracker extends TimerTask {
    private ArrayList<Peer> successors;
    private final int MAX_FAILS = 4;
    private cdht employer;
    //these are a list of ping Sequence numbers that are yet to be acknowledged
    private LinkedList<Integer> pastPingRequests;
    private ArrayList<Integer> deathList;


    public PeerTracker(cdht employer, int successor1_ID, int successor2_ID) {
        this.employer = employer;
        successors = new ArrayList<Peer>();
        successors.add(new Peer(successor1_ID));
        successors.add(new Peer(successor2_ID));
        pastPingRequests = new LinkedList<Integer>();
        deathList = new ArrayList<Integer>();
    }

    @Override
    public void run() {
        analyseFailedPings();
        for (Peer s : successors) {
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
        ArrayList<Integer> deadPeers = new ArrayList<Integer>(2);
        //the deadPeers array was created to stop concurrent modification exceptions
        for (Peer s : successors) {
            if (s.isDead()) {
                deadPeers.add(s.ID);
                deathList.add(s.ID);
            }
        }
        for (int s : deadPeers) {
            handleSuccessorDeath(s);
        }
    }

    /**
     * Called automatically, no need to access this method
     *
     * @param successorId One of the two ID's from the outer class
     */
    private void handleSuccessorDeath(int successorId) {
        System.out.println("Peer " + successorId + " is no longer alive");
        removeFromSuccessors(successorId);
        removeFromPastPings(successorId);
        //now we ask our remaining successor for its next two successors
        byte[] request = MessageFormatter.encodeSuccessorRequest(employer.ID);
        InetSocketAddress address = new InetSocketAddress("localhost", successors.get(0).getTcpPort());
        employer.getTcpServer().send(request, address);
    }

    /**
     * Method should be called if a death is detected externally
     */
    public void registerDeathDetection(int peerId) {
        removeFromPastPings(peerId);
        removeFromSuccessors(peerId);
    }

    /**
     * This method should be called everytime a ping response is received.
     * This method is used to keep track of which peer is alive
     *
     * @param peerId    the peer id
     * @param seqNumber the sequence number in the message
     */
    public void registerPingResponse(int peerId, byte seqNumber) {
        for (Peer s : successors) {
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
     *
     * @param peerId peer that sent the request
     */
    public void registerPingRequest(int peerId) {
        if (pastPingRequests.size() > 10) {
            pastPingRequests.removeFirst();
        }
        pastPingRequests.add(peerId);
    }

    /**
     * This method should be called every time a ping sends a graceful departure message
     * This method will initiate the process of connecting to the next peers
     *
     * @param peerId    the peer that departed
     * @param givenSucc the successor information given by said peer
     */
    public void registerGracefulDepart(int peerId, List<Integer> givenSucc) {
        System.out.println("Peer " + peerId + " will depart from the network");
        //the below code cleans out the left peer from past requests
        //this is useful when a successor is also a predecessor
        removeFromPastPings(peerId);
        removeFromSuccessors(peerId);
        //now we add the lower of the two successors to our own
        for (int s : givenSucc) {
            if (addToSuccessors(s)) break;
        }
        System.out.println("My first successor is now " + ((successors.size() > 0) ? "peer " + successors.get(0) : "Nothing"));
        System.out.println("My second successor is now " + ((successors.size() > 1) ? "peer " + successors.get(1) : "Nothing"));
    }

    /**
     * This method should be called every time a ping sends a successor response message
     * This method will initiate the process of connecting to the next peers
     *
     * @param peerId    the responding peer
     * @param givenSucc the successor information given by said peer
     */
    public void registerSuccessorResponse(int peerId, List<Integer> givenSucc) {
        System.out.println("A successor response was received - " + new ArrayList<Integer>(givenSucc));
        for (int s : givenSucc) {
            if (deathList.contains(s)) {
                deathList.remove((Integer) s);
            } else {
                if (addToSuccessors(s)) break;
            }
        }
        System.out.println("My first successor is now " + ((successors.size() > 0) ? "peer " + successors.get(0) : "Nothing"));
        System.out.println("My second successor is now " + ((successors.size() > 1) ? "peer " + successors.get(1) : "Nothing"));
    }

    private void removeFromPastPings(int peerId) {
        Iterator it = pastPingRequests.iterator();
        while (it.hasNext()) {
            int i = (Integer) it.next();
            if (i == peerId) it.remove();
        }
    }

    private void removeFromSuccessors(int peerId) {
        Iterator it = successors.iterator();
        while (it.hasNext()) {
            Peer p = (Peer) it.next();
            if (p.ID == peerId) it.remove();
        }
    }

    /**
     * Returns success or failure
     *
     * @param s successor id
     */
    private boolean addToSuccessors(int s) {
        if (s == employer.ID) return false;
        boolean contains = false;
        for (Peer p : successors) {
            if (p.ID == s) {
                contains = true;
                break;
            }
        }
        if (!contains) {
            successors.add(new Peer(s));
        }
        return !contains;
    }

    public ArrayList<Integer> getSuccessors() {
        ArrayList<Integer> r = new ArrayList<Integer>();
        for (Peer p : successors) {
            r.add(p.ID);
        }
        return r;
    }

    /**
     * Returns successor ID for the given successor number
     *
     * @param successorNumber [1,2]
     */
    public int getSuccessorId(int successorNumber) {
        return ((successors.size() >= successorNumber) ? successors.get(successorNumber - 1).ID : -1);
    }

    /**
     * Returns the predecessors of the employer
     * if the predecessors are known to be dead, they are not returned
     */
    public ArrayList<Integer> getPredecessors() {
        int i = pastPingRequests.size() - 1;
        if (i == -1) return null; //all relevant peers have departed the network
        ArrayList<Integer> r = new ArrayList<Integer>();
        r.add(pastPingRequests.get(i));
        for (; i >= 0; i--) {
            if (!r.contains(pastPingRequests.get(i))) {
                r.add(pastPingRequests.get(i));
                if (r.size() == 2) break;
            }
        }
        return r;
    }

    private class Peer implements Comparable<Peer> {
        public int ID;
        public ArrayList<Byte> unackedPings;
        private byte seqNum;

        public Peer(int ID) {
            this.ID = ID;
            unackedPings = new ArrayList<Byte>();
            seqNum = 0;
        }

        public void incSeqNum() {
            seqNum++;
            if (seqNum == -128) seqNum = 0;
        }

        public int getUdpPort() {
            return cdht.PORT_BASE + ID;
        }

        public int getTcpPort() {
            return cdht.PORT_BASE + ID;
        }

        public boolean isDead() {
            return unackedPings.size() >= MAX_FAILS;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Peer) {
                Peer other = (Peer) obj;
                return this.ID == other.ID;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return "" + ID + ((isDead()) ? " - Dead" : "");
        }

        @Override
        public int compareTo(Peer o) {
            return this.ID - o.ID;
        }
    }
}