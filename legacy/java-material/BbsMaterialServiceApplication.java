package com.liang.bbs.material.service;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * RAG资料服务启动类
 *
 */
@SpringBootApplication
@EnableDubbo
@MapperScan("com.liang.bbs.material.persistence.mapper")
@EnableCaching
public class BbsMaterialServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BbsMaterialServiceApplication.class, args);
    }
}
