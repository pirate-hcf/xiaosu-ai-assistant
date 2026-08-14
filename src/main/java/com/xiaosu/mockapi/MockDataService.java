package com.xiaosu.mockapi;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class MockDataService {

    private static final TypeReference<List<EmployeeRecord>> EMPLOYEE_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<AttendanceRecord>> ATTENDANCE_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<OrderRecord>> ORDER_LIST = new TypeReference<>() {
    };

    private final Map<String, EmployeeRecord> employees;
    private final List<AttendanceRecord> attendance;
    private final List<OrderRecord> orders;

    public MockDataService(ObjectMapper objectMapper, MockDataProperties properties) {
        Path directory = properties.directory().toAbsolutePath().normalize();
        List<EmployeeRecord> employeeList = read(objectMapper, directory.resolve("employees.json"), EMPLOYEE_LIST);
        this.employees = employeeList.stream()
                .collect(Collectors.toUnmodifiableMap(EmployeeRecord::id, Function.identity()));
        this.attendance = List.copyOf(read(objectMapper, directory.resolve("attendance.json"), ATTENDANCE_LIST));
        this.orders = List.copyOf(read(objectMapper, directory.resolve("orders.json"), ORDER_LIST));
    }

    public EmployeeRecord getEmployee(String employeeId) {
        EmployeeRecord employee = employees.get(employeeId);
        if (employee == null) {
            throw new EmployeeNotFoundException(employeeId);
        }
        return employee;
    }

    public List<AttendanceRecord> queryAttendance(String employeeId, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return attendance.stream()
                .filter(item -> item.employeeId().equals(employeeId))
                .filter(item -> isWithin(item.date(), startDate, endDate))
                .sorted(Comparator.comparing(AttendanceRecord::date).thenComparing(AttendanceRecord::id))
                .toList();
    }

    public List<OrderRecord> queryOrders(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return orders.stream()
                .filter(item -> isWithin(item.orderDate(), startDate, endDate))
                .sorted(Comparator.comparing(OrderRecord::orderDate).thenComparing(OrderRecord::id))
                .toList();
    }

    private static boolean isWithin(LocalDate date, LocalDate startDate, LocalDate endDate) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException(startDate, endDate);
        }
    }

    private static <T> T read(ObjectMapper objectMapper, Path path, TypeReference<T> type) {
        try {
            return objectMapper.readValue(path, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法读取 Mock 数据文件：" + path, exception);
        }
    }
}
