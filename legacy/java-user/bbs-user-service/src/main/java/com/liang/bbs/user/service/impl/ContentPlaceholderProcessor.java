package com.liang.bbs.user.service.impl;

import com.liang.bbs.article.facade.dto.PaperDTO;
import com.liang.bbs.article.facade.dto.ScolarDTO;
import com.liang.bbs.user.facade.dto.user.UserDTO;
import com.liang.bbs.user.facade.server.UserService;
import com.liang.bbs.article.facade.server.PaperService;
import com.liang.bbs.article.facade.server.ScolarService;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * 内容占位符处理器
 * 用于将内容中的占位符替换为实际的用户名、学者名、论文标题等信息
 * 
 */
@Component
public class ContentPlaceholderProcessor {

    @Reference
    private UserService userService;

    @Reference
    private ScolarService scolarService;

    @Reference
    private PaperService paperService;

    // 正则表达式匹配占位符
    private static final Pattern USER_PATTERN = Pattern.compile("!user(\\d+)");
    private static final Pattern SCHOLAR_PATTERN = Pattern.compile("!scholar(\\d+)");
    private static final Pattern PAPER_PATTERN = Pattern.compile("!paper(\\d+)");
    
    // 新的数字占位符格式
    private static final Pattern PAPER_NEW_PATTERN = Pattern.compile("0@(\\d+)");
    private static final Pattern SCHOLAR_NEW_PATTERN = Pattern.compile("1@(\\d+)");
    private static final Pattern RATE_NEW_PATTERN = Pattern.compile("2@(\\d+)");
    private static final Pattern USER_NEW_PATTERN = Pattern.compile("3@(\\d+)");

    /**
     * 处理内容中的占位符
     * 
     * @param content 原始内容
     * @return 处理后的内容
     */
    public String processContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }

        // 收集所有需要查询的ID
        Map<String, List<Integer>> idsMap = collectIds(content);
        
        // 获取对应的名称
        Map<Integer, String> userNames = getNames("user", idsMap.get("user"));
        Map<Integer, String> scholarNames = getNames("scholar", idsMap.get("scholar"));
        Map<Integer, String> paperTitles = getNames("paper", idsMap.get("paper"));
        Map<Integer, String> rateTitles = getNames("rate", idsMap.get("rate"));

        // 替换占位符
        String processedContent = content;
        
        // 替换用户占位符（包括新旧格式）
        processedContent = replacePlaceholders(processedContent, USER_PATTERN, userNames, "user");
        processedContent = replacePlaceholders(processedContent, USER_NEW_PATTERN, userNames, "user");
        
        // 替换学者占位符（包括新旧格式）
        processedContent = replacePlaceholders(processedContent, SCHOLAR_PATTERN, scholarNames, "scholar");
        processedContent = replacePlaceholders(processedContent, SCHOLAR_NEW_PATTERN, scholarNames, "scholar");
        
        // 替换论文占位符（包括新旧格式）
        processedContent = replacePlaceholders(processedContent, PAPER_PATTERN, paperTitles, "paper");
        processedContent = replacePlaceholders(processedContent, PAPER_NEW_PATTERN, paperTitles, "paper");
        
        // 替换评分占位符
        processedContent = replacePlaceholders(processedContent, RATE_NEW_PATTERN, rateTitles, "rate");

        return processedContent;
    }

    /**
     * 收集内容中所有的占位符ID
     */
    private Map<String, List<Integer>> collectIds(String content) {
        Map<String, List<Integer>> idsMap = new HashMap<>();
        idsMap.put("user", new ArrayList<>());
        idsMap.put("scholar", new ArrayList<>());
        idsMap.put("paper", new ArrayList<>());
        idsMap.put("rate", new ArrayList<>());

        // 收集用户ID（包括新旧格式）
        Matcher userMatcher = USER_PATTERN.matcher(content);
        while (userMatcher.find()) {
            idsMap.get("user").add(Integer.parseInt(userMatcher.group(1)));
        }
        Matcher userNewMatcher = USER_NEW_PATTERN.matcher(content);
        while (userNewMatcher.find()) {
            idsMap.get("user").add(Integer.parseInt(userNewMatcher.group(1)));
        }

        // 收集学者ID（包括新旧格式）
        Matcher scholarMatcher = SCHOLAR_PATTERN.matcher(content);
        while (scholarMatcher.find()) {
            idsMap.get("scholar").add(Integer.parseInt(scholarMatcher.group(1)));
        }
        Matcher scholarNewMatcher = SCHOLAR_NEW_PATTERN.matcher(content);
        while (scholarNewMatcher.find()) {
            idsMap.get("scholar").add(Integer.parseInt(scholarNewMatcher.group(1)));
        }

        // 收集论文ID（包括新旧格式）
        Matcher paperMatcher = PAPER_PATTERN.matcher(content);
        while (paperMatcher.find()) {
            idsMap.get("paper").add(Integer.parseInt(paperMatcher.group(1)));
        }
        Matcher paperNewMatcher = PAPER_NEW_PATTERN.matcher(content);
        while (paperNewMatcher.find()) {
            idsMap.get("paper").add(Integer.parseInt(paperNewMatcher.group(1)));
        }

        // 收集评分ID
        Matcher rateMatcher = RATE_NEW_PATTERN.matcher(content);
        while (rateMatcher.find()) {
            idsMap.get("rate").add(Integer.parseInt(rateMatcher.group(1)));
        }

        return idsMap;
    }



    /**
     * 根据ID获取对应的名称（通用方法，支持user/scholar/paper/rate）
     */
    private Map<Integer, String> getNames(String type, List<Integer> ids) {
        Map<Integer, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }

        try {
            switch (type) {
                case "user":
                    List<UserDTO> users = userService.getbyIds(ids);
                    for (UserDTO user : users) {
                        if (user != null && user.getId() != null) {
                            names.put(user.getId(), user.getName() != null ? user.getName() : "未知用户");
                        }
                    }
                    break;
                case "scholar":
                    for (Integer scholarId : ids) {
                        ScolarDTO scholar = scolarService.getById(scholarId);
                        if (scholar != null && scholar.getId() != null) {
                            names.put(scholar.getId(), scholar.getName() != null ? scholar.getName() : "未知学者");
                        }
                    }
                    break;
                case "paper":
                    for (Integer paperId : ids) {
                        PaperDTO paper = paperService.getById(paperId);
                        if (paper != null && paper.getId() != null) {
                            names.put(paper.getId(), paper.getTitle() != null ? paper.getTitle() : "未知论文");
                        }
                    }
                    break;
                case "rate":
                    // 评分类型暂时没有服务，使用默认名称
                    break;
            }
        } catch (Exception e) {
            // 异常处理，使用默认名称
        }

        // 填充缺失的ID
        for (Integer id : ids) {
            if (!names.containsKey(id)) {
                names.put(id, getDefaultName(type, id));
            }
        }

        return names;
    }

    /**
     * 替换占位符
     */
    private String replacePlaceholders(String content, Pattern pattern, Map<Integer, String> replacements, String type) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            Integer id = Integer.parseInt(matcher.group(1));
            String replacement = replacements.getOrDefault(id, getDefaultName(type, id));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }

    /**
     * 获取默认名称
     */
    private String getDefaultName(String type, Integer id) {
        switch (type) {
            case "user":
                return "用户" + id;
            case "scholar":
                return "学者" + id;
            case "paper":
                return "论文" + id;
            case "rate":
                return "评分" + id;
            default:
                return "未知" + id;
        }
    }
}