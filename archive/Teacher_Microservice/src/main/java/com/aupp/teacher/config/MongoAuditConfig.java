package com.aupp.teacher.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@ConditionalOnProperty(name = "spring.data.mongodb.uri")
@EnableMongoAuditing
public class MongoAuditConfig {
}
