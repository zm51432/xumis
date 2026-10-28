package com.shine.xumis.service;

import com.shine.xumis.model.Subscription;
import com.shine.xumis.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /** 订阅邮箱；已存在则返回 false（不重复写入） */
    public boolean subscribe(String email) {
        if (email == null || email.isBlank()) return false;
        if (subscriptionRepository.existsByEmail(email)) return false;
        subscriptionRepository.save(new Subscription(email.trim().toLowerCase()));
        return true;
    }

    public long countAll() {
        return subscriptionRepository.count();
    }

    public List<Subscription> findAll() {
        return subscriptionRepository.findAll();
    }
}
