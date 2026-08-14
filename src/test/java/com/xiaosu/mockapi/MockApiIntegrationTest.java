package com.xiaosu.mockapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.enabled=false")
class MockApiIntegrationTest {

    private static final TypeReference<List<AttendanceRecord>> ATTENDANCE_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<OrderRecord>> ORDER_LIST = new TypeReference<>() {
    };

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void employee001ReturnsStableTypedData() throws Exception {
        HttpResponse<String> response = get("/api/mock/employees/001");

        assertEquals(200, response.statusCode());
        assertJson(response);
        EmployeeRecord employee = objectMapper.readValue(response.body(), EmployeeRecord.class);
        assertEquals("001", employee.id());
        assertEquals("张伟", employee.name());
        assertEquals("研发部", employee.department());
        assertEquals("后端工程师", employee.title());
        assertEquals(LocalDate.of(2023, 3, 6), employee.hireDate());
        assertEquals(EmployeeStatus.ACTIVE, employee.status());
    }

    @Test
    void attendanceUsesInclusiveEmployeeAndDateFilters() throws Exception {
        HttpResponse<String> response = get(
                "/api/mock/attendance?employeeId=001&startDate=2026-06-09&endDate=2026-06-11");

        assertEquals(200, response.statusCode());
        List<AttendanceRecord> records = objectMapper.readValue(response.body(), ATTENDANCE_LIST);
        assertEquals(3, records.size());
        assertEquals(List.of(
                LocalDate.of(2026, 6, 9),
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 11)), records.stream().map(AttendanceRecord::date).toList());
        assertTrue(records.stream().allMatch(record -> record.employeeId().equals("001")));
        assertEquals(AttendanceStatus.LATE, records.getFirst().status());
        assertEquals(AttendanceStatus.LEAVE, records.get(1).status());
        assertEquals("2.5", records.getLast().overtimeHours().stripTrailingZeros().toPlainString());
    }

    @Test
    void ordersUseInclusiveDateFiltersAndKeepRefundStatus() throws Exception {
        HttpResponse<String> response = get(
                "/api/mock/orders?startDate=2026-06-08&endDate=2026-06-14");

        assertEquals(200, response.statusCode());
        List<OrderRecord> records = objectMapper.readValue(response.body(), ORDER_LIST);
        assertEquals(7, records.size());
        assertEquals(LocalDate.of(2026, 6, 8), records.getFirst().orderDate());
        assertEquals(LocalDate.of(2026, 6, 14), records.getLast().orderDate());
        assertFalse(records.stream().anyMatch(order -> order.orderDate().isBefore(LocalDate.of(2026, 6, 8))));
        assertFalse(records.stream().anyMatch(order -> order.orderDate().isAfter(LocalDate.of(2026, 6, 14))));
        assertEquals(2, records.stream().filter(order -> order.status() == OrderStatus.REFUNDED).count());
        assertTrue(records.stream()
                .filter(order -> order.status() == OrderStatus.REFUNDED)
                .allMatch(order -> order.refundAmount().signum() > 0));
    }

    @Test
    void unknownEmployeeReturnsExplicitJson404() throws Exception {
        HttpResponse<String> response = get("/api/mock/employees/999");

        assertEquals(404, response.statusCode());
        assertJson(response);
        MockApiExceptionHandler.ApiError error = objectMapper.readValue(
                response.body(), MockApiExceptionHandler.ApiError.class);
        assertEquals("EMPLOYEE_NOT_FOUND", error.code());
        assertEquals("员工 999 不存在", error.message());
        assertEquals("/api/mock/employees/999", error.path());
    }

    @Test
    void reversedDateRangeReturnsExplicitJson400() throws Exception {
        HttpResponse<String> response = get(
                "/api/mock/orders?startDate=2026-06-14&endDate=2026-06-08");

        assertEquals(400, response.statusCode());
        assertJson(response);
        MockApiExceptionHandler.ApiError error = objectMapper.readValue(
                response.body(), MockApiExceptionHandler.ApiError.class);
        assertEquals("INVALID_DATE_RANGE", error.code());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertJson(HttpResponse<String> response) {
        String contentType = response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse("");
        assertTrue(contentType.startsWith("application/json"), contentType);
    }
}
