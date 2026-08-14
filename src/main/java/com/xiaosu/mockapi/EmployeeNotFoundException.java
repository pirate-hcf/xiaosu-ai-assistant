package com.xiaosu.mockapi;

public class EmployeeNotFoundException extends RuntimeException {

    private final String employeeId;

    public EmployeeNotFoundException(String employeeId) {
        super("员工 " + employeeId + " 不存在");
        this.employeeId = employeeId;
    }

    public String employeeId() {
        return employeeId;
    }
}
