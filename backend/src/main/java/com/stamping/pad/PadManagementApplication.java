package com.stamping.pad;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.stamping.pad.mapper")
public class PadManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(PadManagementApplication.class, args);
    }
}
