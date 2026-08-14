package com.xiaosu.persistence;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PersistenceTransaction {

    private final TransactionTemplate transactionTemplate;

    public PersistenceTransaction(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public void required(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> work.run());
    }

    public <T> T required(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
