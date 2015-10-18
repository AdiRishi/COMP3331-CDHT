/**
 * This is a static class that can be used to check if a given peer has a file requested
 *
 * @author Adiswhar Rishi
 */
public class FileTracker {

    /**
     * Call this method to determine if a cdht has the requested file
     *
     * @param peer     the cdht to check
     * @param fileName the un-hashed filename
     */
    public static boolean hasFile(cdht peer, String fileName) {
        boolean returnVal = false;
        int fileId = getFileId(fileName);
        int successor = peer.peerTracker.getSuccessorId(1);
        if (fileId > peer.ID) {
            if (successor < peer.ID) {
                returnVal = true;
            } else if (fileId < successor) {
                returnVal = true;
            }
        }
        return returnVal;
    }

    /**
     * Returns the file ID given by the file hash function
     */
    private static int getFileId(String fileName) {
        return (Integer.parseInt((fileName.trim())) + 1) % 256;
    }
}
