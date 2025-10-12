package com.liang.bbs.user.service.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB配置类
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.liang.bbs.user.service.impl")
public class MongoDBConfig {

    @Value("${spring.data.mongodb.uri:mongodb://127.0.0.1:27017/ns_bbs}")
    private String uri;

    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(uri);
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        // 从URI中提取数据库名
        String databaseName = uri.substring(uri.lastIndexOf("/") + 1);
        return new MongoTemplate(mongoClient(), databaseName);
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(java.util.Collections.emptyList());
    }
}