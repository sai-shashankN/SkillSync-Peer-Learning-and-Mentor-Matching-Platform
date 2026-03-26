package com.skillsync.payment.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Refund;
import com.razorpay.Utils;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.payment.config.RazorpayConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RazorpayService {

    private final RazorpayClient razorpayClient;
    private final RazorpayConfig razorpayConfig;

    public RazorpayOrderResult createOrder(BigDecimal amount, String currency, String receipt) {
        try {
            JSONObject request = new JSONObject();
            request.put("amount", toPaise(amount));
            request.put("currency", currency);
            request.put("receipt", receipt);
            Order order = razorpayClient.orders.create(request);
            return new RazorpayOrderResult(String.valueOf(order.get("id")), String.valueOf(order.get("status")));
        } catch (Exception ex) {
            throw new BadRequestException("Unable to create payment order at this time");
        }
    }

    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);
            return Utils.verifyPaymentSignature(attributes, razorpayConfig.getKeySecret());
        } catch (Exception ex) {
            throw new BadRequestException("Unable to verify payment signature");
        }
    }

    public void verifyWebhookSignature(String payload, String signature) {
        try {
            Utils.verifyWebhookSignature(payload, signature, razorpayConfig.getKeySecret());
        } catch (Exception ex) {
            throw new BadRequestException("Invalid webhook signature");
        }
    }

    public String initiateRefund(String paymentId, BigDecimal amount) {
        try {
            JSONObject request = new JSONObject();
            request.put("amount", toPaise(amount));
            Refund refund = razorpayClient.payments.refund(paymentId, request);
            return String.valueOf(refund.get("id"));
        } catch (Exception ex) {
            throw new BadRequestException("Unable to initiate refund at this time");
        }
    }

    private long toPaise(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    public record RazorpayOrderResult(String razorpayOrderId, String providerStatus) {
    }
}
