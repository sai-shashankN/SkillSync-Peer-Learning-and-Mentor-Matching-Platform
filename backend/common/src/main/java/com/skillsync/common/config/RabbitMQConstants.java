package com.skillsync.common.config;

public final class RabbitMQConstants {
    public static final String EXCHANGE = "skillsync.events";
    public static final String DLX_EXCHANGE = "skillsync.events.dlx";
    public static final String RETRY_EXCHANGE = "skillsync.events.retry";

    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String NOTIFICATION_DLQ = "notification.queue.dlq";
    public static final String AUDIT_QUEUE = "audit.queue";
    public static final String AUDIT_DLQ = "audit.queue.dlq";
    public static final String PAYMENT_SESSION_QUEUE = "payment.session.queue";
    public static final String PAYMENT_SESSION_DLQ = "payment.session.queue.dlq";
    public static final String PAYMENT_SESSION_COMPLETED_QUEUE = "payment.session.completed.queue";
    public static final String REVIEW_SESSION_QUEUE = "review.session.queue";
    public static final String MENTOR_REVIEW_QUEUE = "mentor.review.queue";
    public static final String MENTOR_WAITLIST_QUEUE = "mentor.waitlist.queue";

    public static final String RK_USER_REGISTERED = "event.user.registered";
    public static final String RK_MENTOR_APPLIED = "event.mentor.applied";
    public static final String RK_MENTOR_APPROVED = "event.mentor.approved";
    public static final String RK_MENTOR_REJECTED = "event.mentor.rejected";
    public static final String RK_SESSION_BOOKED = "event.session.booked";
    public static final String RK_SESSION_ACCEPTED = "event.session.accepted";
    public static final String RK_SESSION_REJECTED = "event.session.rejected";
    public static final String RK_SESSION_COMPLETED = "event.session.completed";
    public static final String RK_SESSION_CANCELLED = "event.session.cancelled";
    public static final String RK_PAYMENT_RECEIVED = "event.payment.received";
    public static final String RK_PAYMENT_REFUNDED = "event.payment.refunded";
    public static final String RK_BADGE_EARNED = "event.badge.earned";
    public static final String RK_WAITLIST_SLOT_OPEN = "event.waitlist.slot_open";
    public static final String RK_REVIEW_SUBMITTED = "event.review.submitted";

    private RabbitMQConstants() {
    }
}
