package com.xiaosu.mockapi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceRecord(
        String id,
        String employeeId,
        LocalDate date,
        AttendanceStatus status,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        BigDecimal overtimeHours,
        String note) {
}
