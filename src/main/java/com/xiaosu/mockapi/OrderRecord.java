package com.xiaosu.mockapi;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderRecord(
        String id,
        String customerName,
        LocalDate orderDate,
        BigDecimal amount,
        OrderStatus status,
        BigDecimal refundAmount) {
}
