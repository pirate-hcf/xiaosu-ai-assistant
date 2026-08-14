package com.xiaosu.mockapi;

import java.time.LocalDate;

public class InvalidDateRangeException extends RuntimeException {

    public InvalidDateRangeException(LocalDate startDate, LocalDate endDate) {
        super("startDate 不能晚于 endDate：" + startDate + " > " + endDate);
    }
}
