import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InquiryService {

    public enum Status {
        PENDING,    
        ACCEPTED,   
        DECLINED,   
        CANCELLED   
    }

        public static class Inquiry {
        private final int id;
        private final int listingId;        
        private final String ownerId;      
        private final String senderId;      
        private final String message;
        private final LocalDateTime createdAt;
        private Status status;

        private Inquiry(int id, int listingId, String ownerId, String senderId, String message) {
            this.id = id;
            this.listingId = listingId;
            this.ownerId = ownerId;
            this.senderId = senderId;
            this.message = message;
            this.createdAt = LocalDateTime.now();
            this.status = Status.PENDING;
        }

        public int getId() { return id; }
        public int getListingId() { return listingId; }
        public String getOwnerId() { return ownerId; }
        public String getSenderId() { return senderId; }
        public String getMessage() { return message; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public Status getStatus() { return status; }

        @Override
        public String toString() {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
            return "Inquiry #" + id
                    + " | listing " + listingId
                    + " | " + senderId + " -> " + ownerId
                    + " | " + status
                    + " | " + createdAt.format(fmt)
                    + " | \"" + message + "\"";
        }
    }
    private static final int MAX_MESSAGE_LENGTH = 200;

    private final Map<Integer, Inquiry> inquiries = new LinkedHashMap<>();
    private int nextId = 1;

     * @param listingId id of the listing (from ListingService)
     * @param ownerId   student who posted that listing
     * @param senderId  student sending the inquiry
     * @param message   short note to the owner
     * @throws IllegalArgumentException if the input is invalid, the sender is
     *         the listing owner, or a pending inquiry already exists
     */
    public Inquiry sendInquiry(int listingId, String ownerId, String senderId, String message) {
        if (listingId <= 0) {
            throw new IllegalArgumentException("A valid listing is required.");
        }
        requireText(ownerId, "Listing owner");
        requireText(senderId, "Sender");
        requireText(message, "Message");

        ownerId = ownerId.trim();
        senderId = senderId.trim();
        message = message.trim();

        if (ownerId.equalsIgnoreCase(senderId)) {
            throw new IllegalArgumentException("You cannot send an inquiry on your own listing.");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message must be at most " + MAX_MESSAGE_LENGTH + " characters.");
        }
        if (hasPendingInquiry(listingId, senderId)) {
            throw new IllegalArgumentException("You already have a pending inquiry on this listing.");
        }

        Inquiry inquiry = new Inquiry(nextId++, listingId, ownerId, senderId, message);
        inquiries.put(inquiry.getId(), inquiry);
        return inquiry;
    }

    public Inquiry getInquiry(int inquiryId) {
        Inquiry inquiry = inquiries.get(inquiryId);
        if (inquiry == null) {
            throw new IllegalArgumentException("Inquiry #" + inquiryId + " was not found.");
        }
        return inquiry;
    }

   
    public List<Inquiry> getReceivedInquiries(String ownerId) {
        List<Inquiry> result = new ArrayList<>();
        for (Inquiry i : inquiries.values()) {
            if (i.getOwnerId().equalsIgnoreCase(ownerId)) {
                result.add(i);
            }
        }
        return result;
    }

    /** Inquiries the student has sent. */
    public List<Inquiry> getSentInquiries(String senderId) {
        List<Inquiry> result = new ArrayList<>();
        for (Inquiry i : inquiries.values()) {
            if (i.getSenderId().equalsIgnoreCase(senderId)) {
                result.add(i);
            }
        }
        return result;
    }

    /** All inquiries made on one listing. */
    public List<Inquiry> getInquiriesForListing(int listingId) {
        List<Inquiry> result = new ArrayList<>();
        for (Inquiry i : inquiries.values()) {
            if (i.getListingId() == listingId) {
                result.add(i);
            }
        }
        return result;
    }

    /** Inquiries received by the owner that are still waiting for a reply. */
    public List<Inquiry> getPendingInquiries(String ownerId) {
        List<Inquiry> result = new ArrayList<>();
        for (Inquiry i : getReceivedInquiries(ownerId)) {
            if (i.getStatus() == Status.PENDING) {
                result.add(i);
            }
        }
        return result;
    }

   
    /** The listing owner accepts a pending inquiry. */
    public Inquiry acceptInquiry(int inquiryId, String ownerId) {
        Inquiry inquiry = getInquiry(inquiryId);
        requireOwner(inquiry, ownerId);
        requirePending(inquiry);
        inquiry.status = Status.ACCEPTED;
        return inquiry;
    }

    /** The listing owner declines a pending inquiry. */
    public Inquiry declineInquiry(int inquiryId, String ownerId) {
        Inquiry inquiry = getInquiry(inquiryId);
        requireOwner(inquiry, ownerId);
        requirePending(inquiry);
        inquiry.status = Status.DECLINED;
        return inquiry;
    }

    /** The sender cancels their own pending inquiry. */
    public Inquiry cancelInquiry(int inquiryId, String senderId) {
        Inquiry inquiry = getInquiry(inquiryId);
        if (!inquiry.getSenderId().equalsIgnoreCase(senderId)) {
            throw new IllegalArgumentException("Only the sender can cancel this inquiry.");
        }
        requirePending(inquiry);
        inquiry.status = Status.CANCELLED;
        return inquiry;
    }

        /** Deletes an inquiry. Only the sender or the listing owner may do this. */
    public void deleteInquiry(int inquiryId, String studentId) {
        Inquiry inquiry = getInquiry(inquiryId);
        boolean isSender = inquiry.getSenderId().equalsIgnoreCase(studentId);
        boolean isOwner = inquiry.getOwnerId().equalsIgnoreCase(studentId);
        if (!isSender && !isOwner) {
            throw new IllegalArgumentException("You are not allowed to delete this inquiry.");
        }
        inquiries.remove(inquiryId);
    }

    /** Used when a listing is removed: deletes every inquiry on it. */
    public void deleteInquiriesForListing(int listingId) {
        inquiries.values().removeIf(i -> i.getListingId() == listingId);
    }

    
    private boolean hasPendingInquiry(int listingId, String senderId) {
        for (Inquiry i : inquiries.values()) {
            if (i.getListingId() == listingId
                    && i.getSenderId().equalsIgnoreCase(senderId)
                    && i.getStatus() == Status.PENDING) {
                return true;
            }
        }
        return false;
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }

    private void requireOwner(Inquiry inquiry, String ownerId) {
        if (!inquiry.getOwnerId().equalsIgnoreCase(ownerId)) {
            throw new IllegalArgumentException("Only the listing owner can respond to this inquiry.");
        }
    }

    private void requirePending(Inquiry inquiry) {
        if (inquiry.getStatus() != Status.PENDING) {
            throw new IllegalArgumentException("This inquiry is already " + inquiry.getStatus() + ".");
        }
    }

   