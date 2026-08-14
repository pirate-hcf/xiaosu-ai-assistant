package com.xiaosu.mockapi;

import java.time.LocalDate;

public record EmployeeRecord(
        String id,
        String name,
        String department,
        String title,
        LocalDate hireDate,
        EmployeeStatus status) {
}
