package com.liang.knowledge.gateway.user;

public interface UserCreditGateway {
    boolean addCredits(Long userId, int amount, String type, String remark, String relatedOrderNo);

    boolean deductCredits(Long userId, int amount, String type, String remark, String relatedOrderNo);

    int getCredits(Long userId);
}
