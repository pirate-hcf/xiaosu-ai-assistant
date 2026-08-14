package com.xiaosu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class XiaosuApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaosuApplication.class, args);
    }
}
