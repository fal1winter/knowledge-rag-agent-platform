package com.liang.bbs.user.service;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.util.Collections;
import java.util.HashMap;

public class CodeGenerator {

    public static void main(String[] args) {
        FastAutoGenerator.create("jdbc:mysql://localhost:3306/ns_bbs?serverTimezone=Asia/Shanghai", "root", "22222222")
                .globalConfig(builder -> {
                    builder.author("jie") // 作者
                           .enableSwagger() // 开启 swagger 注解
                           //.fileOverride() // 覆盖已有文件
                           .outputDir("/home/sun/javabackend/javabackend/bbs-user/bbs-user-service/src/main/java"); // 输出路径
                })
                .packageConfig(builder -> {
                    builder.parent("com.liang.bbs.user") // 父包名
                           .moduleName("service") // 模块名
                           .pathInfo(new HashMap<OutputFile, String>() {{
                            put(OutputFile.entity, "/home/sun/javabackend/javabackend/bbs-user/bbs-user-persistence/src/main/java/com/liang/bbs/user/persistence/entity");
    put(OutputFile.mapper, "/home/sun/javabackend/javabackend/bbs-user/bbs-user-persistence/src/main/java/com/liang/bbs/user/persistence/mapper");
    put(OutputFile.xml, "/home/sun/javabackend/javabackend/bbs-user/bbs-user-persistence/src/main/resources/mybatis/mappers");
    put(OutputFile.service, "/home/sun/javabackend/javabackend/bbs-user/bbs-user-facade/src/main/java/com/liang/bbs/user/facade/server"); // Service 接口路径
    put(OutputFile.serviceImpl, "/home/sun/javabackend/javabackend/bbs-user/bbs-user-service/src/main/java/com/liang/bbs/user/service/impl"); // Service 实现类路径
    put(OutputFile.controller, "/home/sun/javabackend/javabackend/bbs-rest/src/main/java/com/liang/bbs/rest/controller"); // Controller 路径
}}); // XML 路径
                })
                .strategyConfig(builder -> {
                    builder.addInclude("chatroom") // 表名，多个用逗号分隔
                           .addTablePrefix("t_", "sys_") // 过滤表前缀
                           .controllerBuilder()
                               .enableRestStyle() // 生成 @RestController
                           .serviceBuilder()
                               .formatServiceFileName("%sService") // Service 命名方式
                               .formatServiceImplFileName("%sServiceImpl");
                })
                .templateEngine(new FreemarkerTemplateEngine()) // 模板引擎
                .execute();
    }
}
