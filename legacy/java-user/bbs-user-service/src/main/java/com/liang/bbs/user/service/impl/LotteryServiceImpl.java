package com.liang.bbs.user.service.impl;

import com.liang.bbs.user.facade.dto.lottery.*;
import com.liang.bbs.user.facade.server.LotteryService;
import com.liang.bbs.user.persistence.entity.LotteryChancePo;
import com.liang.bbs.user.persistence.entity.LotteryRecordPo;
import com.liang.bbs.user.persistence.entity.UserPo;
import com.liang.bbs.user.persistence.mapper.LotteryChanceMapper;
import com.liang.bbs.user.persistence.mapper.LotteryRecordMapper;
import com.liang.bbs.user.persistence.mapper.UserPoMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.*;

@Slf4j
@Service
public class LotteryServiceImpl implements LotteryService {

    @Autowired
    private LotteryChanceMapper lotteryChanceMapper;

    @Autowired
    private LotteryRecordMapper lotteryRecordMapper;

    @Autowired
    private UserPoMapper userPoMapper;

    private static final String PRIZE_TYPE_PRIZE = "prize";
    private static final String PRIZE_TYPE_LUCK = "luck";

    /** 普惠级：5-20元，总概率15%，奖品数量最多 */
    private static final String TIER_LOW = "TIER_LOW";
    /** 精选级：20-50元，总概率10% */
    private static final String TIER_MID = "TIER_MID";
    /** 高级：50-300元，总概率2% */
    private static final String TIER_HIGH = "TIER_HIGH";
    /** 珍稀级：300-1000元，总概率0.5% */
    private static final String TIER_ELITE = "TIER_ELITE";

    private static final double TIER_LOW_PROB = 0.15;
    private static final double TIER_MID_PROB = 0.10;
    private static final double TIER_HIGH_PROB = 0.02;
    private static final double TIER_ELITE_PROB = 0.005;

    /** 各层级概率分界线 */
    private static final double TIER_LOW_MAX = TIER_LOW_PROB;
    private static final double TIER_MID_MAX = TIER_LOW_MAX + TIER_MID_PROB;
    private static final double TIER_HIGH_MAX = TIER_MID_MAX + TIER_HIGH_PROB;
    private static final double TIER_ELITE_MAX = TIER_HIGH_MAX + TIER_ELITE_PROB;

    private static final List<PrizeConfigDTO> ALL_PRIZES = new ArrayList<>();
    private static final List<PrizeConfigDTO> TIER_LOW_PRIZES = new ArrayList<>();
    private static final List<PrizeConfigDTO> TIER_MID_PRIZES = new ArrayList<>();
    private static final List<PrizeConfigDTO> TIER_HIGH_PRIZES = new ArrayList<>();
    private static final List<PrizeConfigDTO> TIER_ELITE_PRIZES = new ArrayList<>();
    private static final List<String> LUCK_MESSAGES = Arrays.asList(
            "天天开心", "恭喜发财", "万事如意", "心想事成", "福气满满", "好事发生", "好运连连", "顺风顺水",
            "笑口常开", "鸿运当头", "喜气洋洋", "前程似锦", "蒸蒸日上", "龙马精神", "锦上添花"
    );

    @PostConstruct
    public void init() {
        // ===== 普惠级 5-20元 (概率均分) =====
        List<String> lowTier = Arrays.asList(
                "珍珠奶茶", "椰果奶茶", "芋泥波波奶茶", "杨枝甘露", "柠檬水",
                "冰美式咖啡", "燕麦拿铁", "生椰拿铁", "香草冰淇淋", "芒果冰淇淋",
                "薯条小份", "鸡米花", "香辣鸡翅", "蛋挞2个", "老婆饼2个",
                "蛋黄酥1个", "肉松小贝", "手抓饼", "关东煮4串", "烤肠2根",
                "水果拼盘", "养胃粥1份", "红糖姜茶", "蜂蜜柠檬水", "椰青1个"
        );
        for (String name : lowTier) {
            PrizeConfigDTO p = new PrizeConfigDTO(name, TIER_LOW, "普惠级", 1, getEmoji(name), PRIZE_TYPE_PRIZE);
            ALL_PRIZES.add(p);
            TIER_LOW_PRIZES.add(p);
        }

        // ===== 精选级 20-50元 =====
        List<String> midTier = Arrays.asList(
                "呷哺呷哺100元券", "星巴克礼品卡", "必胜客披萨券", "蛋糕券", "甜品自助券",
                "电影票2张", "唱K优惠券", "密室逃脱券", "剧本杀券", "真人CS券",
                "Switch游戏卡带", "Steam游戏充值卡", "爱奇艺年卡", "优酷年卡", "QQ音乐年卡",
                "网易云音乐年卡", "Keep月卡", "Keep年卡", "外卖券包", "滴滴打车券",
                "花店礼品卡", "咖啡机优惠券", "小熊煮蛋器", "小米充电宝", "小米蓝牙耳机"
        );
        for (String name : midTier) {
            PrizeConfigDTO p = new PrizeConfigDTO(name, TIER_MID, "精选级", 2, getEmoji(name), PRIZE_TYPE_PRIZE);
            ALL_PRIZES.add(p);
            TIER_MID_PRIZES.add(p);
        }

        // ===== 高级 50-300元 =====
        List<String> highTier = Arrays.asList(
                "小米手环8", "AirPods耳机", "Switch Lite", "拍立得相机", "Kindle电子书",
                "小米蓝牙音箱", "小米筋膜枪", "戴森吹风机基础款", "眼部按摩仪", "颈椎按摩仪",
                "华为路由器", "小米扫地机器人入门款", "九阳破壁机", "便携投影仪", "机械键盘入门款"
        );
        for (String name : highTier) {
            PrizeConfigDTO p = new PrizeConfigDTO(name, TIER_HIGH, "高级", 3, getEmoji(name), PRIZE_TYPE_PRIZE);
            ALL_PRIZES.add(p);
            TIER_HIGH_PRIZES.add(p);
        }

        // ===== 珍稀级 300-1000元 =====
        List<String> eliteTier = Arrays.asList(
                "AirPods Pro2", "Switch OLED", "PlayStation游戏机", "GoPro运动相机", "戴森吸尘器",
                "Apple Watch SE", "小米无人机入门款", "大疆云台", "iPad mini", "降噪耳机旗舰款"
        );
        for (String name : eliteTier) {
            PrizeConfigDTO p = new PrizeConfigDTO(name, TIER_ELITE, "珍稀级", 4, getEmoji(name), PRIZE_TYPE_PRIZE);
            ALL_PRIZES.add(p);
            TIER_ELITE_PRIZES.add(p);
        }

        log.info("Lottery prize config initialized: LOW={} prizes, MID={} prizes, HIGH={} prizes, ELITE={} prizes",
                TIER_LOW_PRIZES.size(), TIER_MID_PRIZES.size(), TIER_HIGH_PRIZES.size(), TIER_ELITE_PRIZES.size());
    }

    /** 根据奖品名称返回emoji */
    private String getEmoji(String name) {
        if (name.contains("奶茶") || name.contains("咖啡") || name.contains("茶") || name.contains("饮品")) return "☕";
        if (name.contains("冰淇淋") || name.contains("冰") || name.contains("雪糕")) return "🍦";
        if (name.contains("电影")) return "🎬";
        if (name.contains("游戏") || name.contains("Steam") || name.contains("Switch") || name.contains("PlayStation")) return "🎮";
        if (name.contains("音乐") || name.contains("卡带") || name.contains("耳机") || name.contains("AirPods")) return "🎧";
        if (name.contains("K") || name.contains("密室") || name.contains("剧本") || name.contains("CS")) return "🎯";
        if (name.contains("星巴克")) return "☕";
        if (name.contains("Pizza") || name.contains("披萨") || name.contains("必胜客")) return "🍕";
        if (name.contains("蛋糕") || name.contains("甜品") || name.contains("蛋挞")) return "🍰";
        if (name.contains("年卡") || name.contains("月卡") || name.contains("会员")) return "📱";
        if (name.contains("Watch") || name.contains("手环")) return "⌚";
        if (name.contains("iPad") || name.contains("Kindle")) return "💻";
        if (name.contains("相机") || name.contains("GoPro")) return "📷";
        if (name.contains("无人机")) return "🚁";
        if (name.contains("云台")) return "📹";
        if (name.contains("吸尘") || name.contains("戴森")) return "🌀";
        if (name.contains("筋膜") || name.contains("按摩")) return "💆";
        if (name.contains("投影")) return "🎥";
        if (name.contains("机械键盘")) return "⌨️";
        if (name.contains("音箱") || name.contains("音响")) return "🔊";
        if (name.contains("薯条") || name.contains("鸡翅") || name.contains("鸡米花")) return "🍗";
        if (name.contains("水果")) return "🍇";
        if (name.contains("吹风机")) return "💨";
        if (name.contains("路由器")) return "📡";
        if (name.contains("充电宝")) return "🔋";
        if (name.contains("花店") || name.contains("花")) return "🌸";
        if (name.contains("火锅") || name.contains("呷哺")) return "🍲";
        if (name.contains("破壁")) return "🍹";
        return "🎁";
    }

    @Override
    public LotteryChanceDTO getUserChances(Integer userId) {
        LotteryChanceDTO dto = new LotteryChanceDTO();
        dto.setUserId(Long.valueOf(userId));
        dto.setChances(getChancesInternal(userId));
        return dto;
    }

    private int getChancesInternal(Integer userId) {
        Integer chances = lotteryChanceMapper.getChances(Long.valueOf(userId));
        return chances != null ? chances : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LotteryDrawResultDTO draw(Integer userId) {
        int currentChances = getChancesInternal(userId);
        if (currentChances <= 0) {
            throw new RuntimeException("没有抽奖次数了");
        }

        int updated = lotteryChanceMapper.decrementChance(Long.valueOf(userId));
        if (updated == 0) {
            throw new RuntimeException("没有抽奖次数了");
        }

        String prize = doDraw();
        String prizeType = LUCK_MESSAGES.contains(prize) ? PRIZE_TYPE_LUCK : PRIZE_TYPE_PRIZE;

        LotteryRecordPo record = new LotteryRecordPo();
        record.setUserId(Long.valueOf(userId));
        record.setPrize(prize);
        record.setPrizeType(prizeType);
        lotteryRecordMapper.insert(record);

        int remaining = getChancesInternal(userId);
        log.info("draw: userId={}, prize={}, remaining={}", userId, prize, remaining);

        LotteryDrawResultDTO result = new LotteryDrawResultDTO();
        result.setPrize(prize);
        result.setPrizeType(prizeType);
        result.setRemainingChances(remaining);
        return result;
    }

    private String doDraw() {
        double rand = Math.random();
        double cumulative = 0.0;

        // 先判断抽中哪个层级
        cumulative += TIER_LOW_PROB;
        if (rand < cumulative) {
            return pickPrizeFromTier(TIER_LOW_PRIZES);
        }
        cumulative += TIER_MID_PROB;
        if (rand < cumulative) {
            return pickPrizeFromTier(TIER_MID_PRIZES);
        }
        cumulative += TIER_HIGH_PROB;
        if (rand < cumulative) {
            return pickPrizeFromTier(TIER_HIGH_PRIZES);
        }
        cumulative += TIER_ELITE_PROB;
        if (rand < cumulative) {
            return pickPrizeFromTier(TIER_ELITE_PRIZES);
        }

        // 72.5% 概率获得安慰语
        return LUCK_MESSAGES.get((int) (Math.random() * LUCK_MESSAGES.size()));
    }

    private String pickPrizeFromTier(List<PrizeConfigDTO> tierPrizes) {
        int size = tierPrizes.size();
        if (size == 0) return LUCK_MESSAGES.get(0);
        return tierPrizes.get((int) (Math.random() * size)).getName();
    }

    @Override
    public List<LotteryRecordDTO> getUserRecords(Integer userId, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 20;
        }
        List<LotteryRecordPo> poList = lotteryRecordMapper.selectByUserId(Long.valueOf(userId));
        List<LotteryRecordDTO> dtoList = new ArrayList<>();
        int count = 0;
        for (LotteryRecordPo po : poList) {
            if (count >= limit) break;
            LotteryRecordDTO dto = new LotteryRecordDTO();
            BeanUtils.copyProperties(po, dto);
            dtoList.add(dto);
            count++;
        }
        return dtoList;
    }

    @Override
    public List<LotteryRecordDTO> getRecentRecords(Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 20;
        }
        List<LotteryRecordPo> poList = lotteryRecordMapper.selectRecent(limit);
        List<LotteryRecordDTO> dtoList = new ArrayList<>();
        for (LotteryRecordPo po : poList) {
            LotteryRecordDTO dto = new LotteryRecordDTO();
            BeanUtils.copyProperties(po, dto);
            dtoList.add(dto);
        }
        return dtoList;
    }

    @Override
    public List<PrizeConfigDTO> getPrizeConfig() {
        // 返回各层级的代表奖品（前5个），概率不暴露给前端
        List<PrizeConfigDTO> showcase = new ArrayList<>();
        showcase.addAll(TIER_LOW_PRIZES.subList(0, Math.min(5, TIER_LOW_PRIZES.size())));
        showcase.addAll(TIER_MID_PRIZES.subList(0, Math.min(5, TIER_MID_PRIZES.size())));
        showcase.addAll(TIER_HIGH_PRIZES.subList(0, Math.min(5, TIER_HIGH_PRIZES.size())));
        showcase.addAll(TIER_ELITE_PRIZES.subList(0, Math.min(5, TIER_ELITE_PRIZES.size())));
        return showcase;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void distributeChances(Integer userId, Integer chances) {
        if (userId == null || chances == null || chances < 0) {
            throw new RuntimeException("参数错误");
        }
        if (chances == 0) {
            return;
        }
        // 先查当前次数
        Integer current = lotteryChanceMapper.getChances(Long.valueOf(userId));
        int newChances;
        if (current != null) {
            newChances = current + chances;
        } else {
            newChances = chances;
        }
        // 再upsert（单条SQL，ON DUPLICATE KEY UPDATE保证原子）
        lotteryChanceMapper.insertOrUpdate(Long.valueOf(userId), newChances);
        log.info("distributeChances: userId={}, added={}, total={}", userId, chances, newChances);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDistributeChances(List<Integer> userIds, Integer chances) {
        if (userIds == null || userIds.isEmpty() || chances == null || chances < 0) {
            return 0;
        }
        int success = 0;
        for (Integer userId : userIds) {
            try {
                distributeChances(userId, chances);
                success++;
            } catch (Exception e) {
                log.warn("batchDistributeChances failed for userId={}: {}", userId, e.getMessage());
            }
        }
        return success;
    }

    @Override
    public List<UserChanceDTO> listAllUserChances() {
        List<UserChanceDTO> result = new ArrayList<>();
        List<LotteryChancePo> all = lotteryChanceMapper.selectAll();
        for (LotteryChancePo po : all) {
            UserChanceDTO dto = new UserChanceDTO();
            dto.setUserId(po.getUserId());
            dto.setChances(po.getChances());
            UserPo user = userPoMapper.selectByPrimaryKey(po.getUserId().intValue());
            dto.setUserName(user != null ? user.getName() : "Unknown");
            result.add(dto);
        }
        return result;
    }

    @Override
    public List<UserChanceDTO> listAllUsersWithChances() {
        return listAllUserChances();
    }
}