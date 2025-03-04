package com.mingri.shortlink.admin;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author: mingri31164
 * @CreateTime: 2025/2/18 21:05
 * @ClassName: ShortLinkAdminApplication
 * @Version: 1.0
 */
@SpringBootApplication
@MapperScan("com.mingri.shortlink.admin.dao.mapper")
public class ShortLinkAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortLinkAdminApplication.class, args);
    }

}
