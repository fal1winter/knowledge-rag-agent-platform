package com.liang.knowledge.gateway.payment;

import com.liang.knowledge.gateway.config.PaymentProperties;
import com.liang.knowledge.gateway.order.MaterialOrderGateway;
import com.liang.knowledge.gateway.order.MaterialOrderSnapshot;
import com.liang.knowledge.gateway.user.UserCreditGateway;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentOrderRepository orderRepository;
    private final UserCreditGateway userCreditGateway;
    private final PaymentProperties paymentProperties;
    private final MaterialOrderGateway materialOrderGateway;
    private final Map<PayChannel, PaymentProvider> providers = new EnumMap<>(PayChannel.class);

    public PaymentService(
            PaymentOrderRepository orderRepository,
            UserCreditGateway userCreditGateway,
            PaymentProperties paymentProperties,
            MaterialOrderGateway materialOrderGateway,
            List<PaymentProvider> providerList
    ) {
        this.orderRepository = orderRepository;
        this.userCreditGateway = userCreditGateway;
        this.paymentProperties = paymentProperties;
        this.materialOrderGateway = materialOrderGateway;
        for (PaymentProvider provider : providerList) {
            providers.put(provider.channel(), provider);
        }
    }

    public PaymentCreateResult create(PaymentRequest request) {
        PaymentAmount amount = resolvePaymentAmount(request);
        PaymentOrder order = PaymentOrder.builder()
                .orderNo(nextOrderNo())
                .userId(request.getUserId())
                .tenantId(request.getTenantId())
                .subject(request.getSubject())
                .amountFen(amount.amountFen)
                .credits(amount.credits)
                .channel(request.getChannel())
                .status(PaymentStatus.CREATED)
                .relatedBizType(request.getRelatedBizType())
                .relatedBizId(request.getRelatedBizId())
                .createdAt(LocalDateTime.now())
                .build();
        orderRepository.save(order);

        if (request.getChannel() == PayChannel.CREDITS) {
            boolean deducted = userCreditGateway.deductCredits(order.getUserId(), order.getCredits(), "knowledge_consume", order.getSubject(), order.getOrderNo());
            if (!deducted) {
                order.setStatus(PaymentStatus.FAILED);
                orderRepository.save(order);
                throw new IllegalStateException("insufficient credits or deduction failed");
            }
            order.setStatus(PaymentStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
            orderRepository.save(order);
            if (isMaterialPurchase(order)) {
                materialOrderGateway.confirmPaid(order.getRelatedBizId(), order.getUserId(), order.getOrderNo(), null, order.getChannel().name());
            }
            return PaymentCreateResult.builder()
                    .orderNo(order.getOrderNo())
                    .channel(PayChannel.CREDITS)
                    .status(PaymentStatus.PAID)
                    .credits(order.getCredits())
                    .build();
        }

        PaymentProvider provider = providers.get(request.getChannel());
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported payment channel: " + request.getChannel());
        }
        PaymentCreateResult result = provider.create(order);
        order.setStatus(PaymentStatus.PENDING);
        orderRepository.save(order);
        return result;
    }

    public PaymentCallbackResult handleNotify(PayChannel channel, Map<String, String> params, String rawBody, Map<String, String> headers) {
        PaymentProvider provider = providers.get(channel);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported payment channel: " + channel);
        }
        PaymentCallbackResult result = provider.verifyAndParseNotify(params, rawBody, headers);
        if (!result.isSuccess()) {
            return result;
        }
        PaymentOrder order = orderRepository.findByOrderNo(result.getOrderNo())
                .orElseThrow(() -> new IllegalStateException("Payment order not found: " + result.getOrderNo()));
        if (order.getStatus() == PaymentStatus.PAID) {
            return result;
        }
        order.setStatus(PaymentStatus.PAID);
        order.setProviderTradeNo(result.getProviderTradeNo());
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);
        if (isMaterialPurchase(order)) {
            materialOrderGateway.confirmPaid(
                    order.getRelatedBizId(),
                    order.getUserId(),
                    order.getOrderNo(),
                    result.getProviderTradeNo(),
                    order.getChannel().name()
            );
        } else {
            userCreditGateway.addCredits(order.getUserId(), order.getCredits(), "payment_recharge", order.getSubject(), order.getOrderNo());
        }
        return result;
    }

    private PaymentAmount resolvePaymentAmount(PaymentRequest request) {
        if ("material_purchase".equals(request.getRelatedBizType()) && request.getRelatedBizId() != null) {
            MaterialOrderSnapshot snapshot = materialOrderGateway.getPaymentSnapshot(request.getRelatedBizId());
            if (snapshot.getBuyerId() == null || !snapshot.getBuyerId().equals(request.getUserId())) {
                throw new IllegalArgumentException("material order buyer does not match payment user");
            }
            if (snapshot.getPriceCredits() == null || snapshot.getPriceCredits() <= 0) {
                throw new IllegalArgumentException("material order price is invalid");
            }
            return new PaymentAmount(creditsToAmountFen(snapshot.getPriceCredits()), snapshot.getPriceCredits());
        }
        return new PaymentAmount(request.getAmountFen(), toCredits(request.getAmountFen()));
    }

    private boolean isMaterialPurchase(PaymentOrder order) {
        return "material_purchase".equals(order.getRelatedBizType()) && order.getRelatedBizId() != null;
    }

    private int creditsToAmountFen(int credits) {
        int rate = Math.max(1, paymentProperties.getCreditRate());
        return Math.max(1, (credits * 100 + rate - 1) / rate);
    }

    private int toCredits(int amountFen) {
        return Math.max(1, amountFen * paymentProperties.getCreditRate() / 100);
    }

    private String nextOrderNo() {
        return "KR" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static class PaymentAmount {
        private final int amountFen;
        private final int credits;

        private PaymentAmount(int amountFen, int credits) {
            this.amountFen = amountFen;
            this.credits = credits;
        }
    }
}
