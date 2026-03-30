ALTER TABLE payments RENAME COLUMN razorpay_order_id TO provider_order_id;
ALTER TABLE payments RENAME COLUMN razorpay_payment_id TO provider_payment_id;
ALTER TABLE payments RENAME COLUMN razorpay_signature TO provider_signature;

ALTER TABLE payments
    ADD COLUMN provider VARCHAR(30) NOT NULL DEFAULT 'RAZORPAY';

ALTER TABLE payments
    ALTER COLUMN provider SET DEFAULT 'PAYPAL';
