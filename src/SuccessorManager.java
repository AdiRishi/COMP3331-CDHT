import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.TimerTask;

public class SuccessorManager extends TimerTask {
    private byte s1_seqNumber;
    private byte s2_seqNumber;
    private final int MAX_FAILS = 4;
    private cdht employer;
    //these are a list of ping Sequence numbers that are yet to be acknowledged
    private ArrayList<Byte> unackedPings_1;
    private ArrayList<Byte> unackedPings_2;

    public SuccessorManager(cdht employer) {
        this.employer = employer;
        s1_seqNumber = 0;
        s2_seqNumber = 0;
        unackedPings_1 = new ArrayList<Byte>();
        unackedPings_2 = new ArrayList<Byte>();
    }

    @Override
    public void run() {
        analyseFailedPings();
        employer.getUdpServer().sendDatagram(MessageFormatter.encodePingRequest(s1_seqNumber),
                new InetSocketAddress("localhost", employer.getSuccessor1UdpPort()));
        employer.getUdpServer().sendDatagram(MessageFormatter.encodePingRequest(s2_seqNumber),
                new InetSocketAddress("localhost", employer.getSuccessor2UdpPort()));
        unackedPings_1.add(s1_seqNumber);
        unackedPings_2.add(s2_seqNumber);
        s1_seqNumber++;
        s2_seqNumber++;
        if (s1_seqNumber == -128) s1_seqNumber = 0;
        if (s2_seqNumber == -128) s2_seqNumber = 0;
    }

    /**
     * This method does the necessary actions if a ping fails
     * Also detects successor death
     */
    private void analyseFailedPings() {
        if (unackedPings_1.size() >= MAX_FAILS) {
            handleSuccessorDeath(employer.getSuccessor1_ID());
        }
        if (unackedPings_2.size() >= MAX_FAILS) {
            handleSuccessorDeath(employer.getSuccessor2_ID());
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

    public void registerPingResponse(int peerId, byte seqNumber) {
        if (peerId == employer.getSuccessor1_ID()) {
            if (!unackedPings_1.contains(seqNumber)) {
                System.out.println("Sequence number " + seqNumber + " does not exist for peer " + peerId);
                System.out.println(unackedPings_1);
            } else {
                unackedPings_1.subList(0, unackedPings_1.indexOf(seqNumber) + 1).clear();
            }
        } else if (peerId == employer.getSuccessor2_ID()) {
            if (!unackedPings_2.contains(seqNumber)) {
                System.out.println("Sequence number " + seqNumber + " does not exist for peer " + peerId);
                System.out.println(unackedPings_2);
            } else {
                unackedPings_2.subList(0, unackedPings_2.indexOf(seqNumber) + 1).clear();
            }
        }
    }
}