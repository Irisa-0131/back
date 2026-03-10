package com.xupu.smartdose;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.xupu.smartdose.mapper")
public class XupuSmartDoseApplication {
    public static void main(String[] args) {
        SpringApplication.run(XupuSmartDoseApplication.class, args);
    }
}
