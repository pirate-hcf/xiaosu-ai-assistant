package com.xiaosu.mockapi;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mock")
public class MockApiController {

    private final MockDataService mockDataService;

    public MockApiController(MockDataService mockDataService) {
        this.mockDataService = mockDataService;
    }

    @GetMapping("/employees/{id}")
    public EmployeeRecord getEmployee(@PathVariable String id) {
        return mockDataService.getEmployee(id);
    }

    @GetMapping("/attendance")
    public List<AttendanceRecord> queryAttendance(
            @RequestParam String employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return mockDataService.queryAttendance(employeeId, startDate, endDate);
    }

    @GetMapping("/orders")
    public List<OrderRecord> queryOrders(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return mockDataService.queryOrders(startDate, endDate);
    }
}
