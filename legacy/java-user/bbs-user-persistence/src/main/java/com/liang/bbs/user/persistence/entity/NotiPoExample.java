package com.liang.bbs.user.persistence.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class NotiPoExample {
    protected String orderByClause;

    protected boolean distinct;

    protected List<Criteria> oredCriteria;

    public NotiPoExample() {
        oredCriteria = new ArrayList<Criteria>();
    }

    public void setOrderByClause(String orderByClause) {
        this.orderByClause = orderByClause;
    }

    public String getOrderByClause() {
        return orderByClause;
    }

    public void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }

    public boolean isDistinct() {
        return distinct;
    }

    public List<Criteria> getOredCriteria() {
        return oredCriteria;
    }

    public void or(Criteria criteria) {
        oredCriteria.add(criteria);
    }

    public Criteria or() {
        Criteria criteria = createCriteriaInternal();
        oredCriteria.add(criteria);
        return criteria;
    }

    public NotiPoExample orderBy(String orderByClause) {
        this.setOrderByClause(orderByClause);
        return this;
    }

    public NotiPoExample orderBy(String ... orderByClauses) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < orderByClauses.length; i++) {
            sb.append(orderByClauses[i]);
            if (i < orderByClauses.length - 1) {
                sb.append(" , ");
            }
        }
        this.setOrderByClause(sb.toString());
        return this;
    }

    public Criteria createCriteria() {
        Criteria criteria = createCriteriaInternal();
        if (oredCriteria.size() == 0) {
            oredCriteria.add(criteria);
        }
        return criteria;
    }

    protected Criteria createCriteriaInternal() {
        Criteria criteria = new Criteria(this);
        return criteria;
    }

    public void clear() {
        oredCriteria.clear();
        orderByClause = null;
        distinct = false;
    }

    public static Criteria newAndCreateCriteria() {
        NotiPoExample example = new NotiPoExample();
        return example.createCriteria();
    }

    public NotiPoExample when(boolean condition, IExampleWhen then) {
        if (condition) {
            then.example(this);
        }
        return this;
    }

    public NotiPoExample when(boolean condition, IExampleWhen then, IExampleWhen otherwise) {
        if (condition) {
            then.example(this);
        } else {
            otherwise.example(this);
        }
        return this;
    }

    public NotiPoExample distinct(boolean distinct) {
        this.setDistinct(distinct);
        return this;
    }

    protected abstract static class GeneratedCriteria {
        protected List<Criterion> criteria;

        protected GeneratedCriteria() {
            super();
            criteria = new ArrayList<Criterion>();
        }

        public boolean isValid() {
            return criteria.size() > 0;
        }

        public List<Criterion> getAllCriteria() {
            return criteria;
        }

        public List<Criterion> getCriteria() {
            return criteria;
        }

        protected void addCriterion(String condition) {
            if (condition == null) {
                throw new RuntimeException("Value for condition cannot be null");
            }
            criteria.add(new Criterion(condition));
        }

        protected void addCriterion(String condition, Object value, String property) {
            if (value == null) {
                throw new RuntimeException("Value for " + property + " cannot be null");
            }
            criteria.add(new Criterion(condition, value));
        }

        protected void addCriterion(String condition, Object value1, Object value2, String property) {
            if (value1 == null || value2 == null) {
                throw new RuntimeException("Between values for " + property + " cannot be null");
            }
            criteria.add(new Criterion(condition, value1, value2));
        }

        public Criteria andIdIsNull() {
            addCriterion("id is null");
            return (Criteria) this;
        }

        public Criteria andIdIsNotNull() {
            addCriterion("id is not null");
            return (Criteria) this;
        }

        public Criteria andIdEqualTo(Integer value) {
            addCriterion("id =", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotEqualTo(Integer value) {
            addCriterion("id <>", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdGreaterThan(Integer value) {
            addCriterion("id >", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdGreaterThanOrEqualTo(Integer value) {
            addCriterion("id >=", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLessThan(Integer value) {
            addCriterion("id <", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLessThanOrEqualTo(Integer value) {
            addCriterion("id <=", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdIn(List<Integer> values) {
            addCriterion("id in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotIn(List<Integer> values) {
            addCriterion("id not in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdBetween(Integer value1, Integer value2) {
            addCriterion("id between", value1, value2, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotBetween(Integer value1, Integer value2) {
            addCriterion("id not between", value1, value2, "id");
            return (Criteria) this;
        }

        public Criteria andUseridIsNull() {
            addCriterion("userid is null");
            return (Criteria) this;
        }

        public Criteria andUseridIsNotNull() {
            addCriterion("userid is not null");
            return (Criteria) this;
        }

        public Criteria andUseridEqualTo(Integer value) {
            addCriterion("userid =", value, "userid");
            return (Criteria) this;
        }

        public Criteria andUseridNotEqualTo(Integer value) {
            addCriterion("userid <>", value, "userid");
            return (Criteria) this;
        }

        public Criteria andUseridGreaterThan(Integer value) {
            addCriterion("userid >", value, "userid");
            return (Criteria) this;
        }

        public Criteria andUseridGreaterThanOrEqualTo(Integer value) {
            addCriterion("userid >=", value, "userid");
            return (Criteria) this;
        }

        public Criteria andUseridLessThan(Integer value) {
            addCriterion("userid <", value, "userid");
            return (Criteria) this;
        }

        public Criteria andUseridLessThanOrEqualTo(Integer value) {
            addCriterion("userid <=", value, "userid");
            return (Criteria) this;
        }

        public Criteria andUseridIn(List<Integer> values) {
            addCriterion("userid in", values, "userid");
            return (Criteria) this;
        }

        public Criteria andUseridNotIn(List<Integer> values) {
            addCriterion("userid not in", values, "userid");
            return (Criteria) this;
        }

        public Criteria andUseridBetween(Integer value1, Integer value2) {
            addCriterion("userid between", value1, value2, "userid");
            return (Criteria) this;
        }

        public Criteria andUseridNotBetween(Integer value1, Integer value2) {
            addCriterion("userid not between", value1, value2, "userid");
            return (Criteria) this;
        }

        public Criteria andTypeIsNull() {
            addCriterion("`type` is null");
            return (Criteria) this;
        }

        public Criteria andTypeIsNotNull() {
            addCriterion("`type` is not null");
            return (Criteria) this;
        }

        public Criteria andTypeEqualTo(String value) {
            addCriterion("`type` =", value, "type");
            return (Criteria) this;
        }

        public Criteria andTypeNotEqualTo(String value) {
            addCriterion("`type` <>", value, "type");
            return (Criteria) this;
        }

        public Criteria andTypeGreaterThan(String value) {
            addCriterion("`type` >", value, "type");
            return (Criteria) this;
        }

        public Criteria andTypeGreaterThanOrEqualTo(String value) {
            addCriterion("`type` >=", value, "type");
            return (Criteria) this;
        }

        public Criteria andTypeLessThan(String value) {
            addCriterion("`type` <", value, "type");
            return (Criteria) this;
        }

        public Criteria andTypeLessThanOrEqualTo(String value) {
            addCriterion("`type` <=", value, "type");
            return (Criteria) this;
        }

        public Criteria andTypeLike(String value) {
            addCriterion("`type` like", value, "type");
            return (Criteria) this;
        }

        public Criteria andTypeNotLike(String value) {
            addCriterion("`type` not like", value, "type");
            return (Criteria) this;
        }

        public Criteria andTypeIn(List<String> values) {
            addCriterion("`type` in", values, "type");
            return (Criteria) this;
        }

        public Criteria andTypeNotIn(List<String> values) {
            addCriterion("`type` not in", values, "type");
            return (Criteria) this;
        }

        public Criteria andTypeBetween(String value1, String value2) {
            addCriterion("`type` between", value1, value2, "type");
            return (Criteria) this;
        }

        public Criteria andTypeNotBetween(String value1, String value2) {
            addCriterion("`type` not between", value1, value2, "type");
            return (Criteria) this;
        }

        public Criteria andContentIsNull() {
            addCriterion("content is null");
            return (Criteria) this;
        }

        public Criteria andContentIsNotNull() {
            addCriterion("content is not null");
            return (Criteria) this;
        }

        public Criteria andContentEqualTo(String value) {
            addCriterion("content =", value, "content");
            return (Criteria) this;
        }

        public Criteria andContentNotEqualTo(String value) {
            addCriterion("content <>", value, "content");
            return (Criteria) this;
        }

        public Criteria andContentGreaterThan(String value) {
            addCriterion("content >", value, "content");
            return (Criteria) this;
        }

        public Criteria andContentGreaterThanOrEqualTo(String value) {
            addCriterion("content >=", value, "content");
            return (Criteria) this;
        }

        public Criteria andContentLessThan(String value) {
            addCriterion("content <", value, "content");
            return (Criteria) this;
        }

        public Criteria andContentLessThanOrEqualTo(String value) {
            addCriterion("content <=", value, "content");
            return (Criteria) this;
        }

        public Criteria andContentLike(String value) {
            addCriterion("content like", value, "content");
            return (Criteria) this;
        }

        public Criteria andContentNotLike(String value) {
            addCriterion("content not like", value, "content");
            return (Criteria) this;
        }

        public Criteria andContentIn(List<String> values) {
            addCriterion("content in", values, "content");
            return (Criteria) this;
        }

        public Criteria andContentNotIn(List<String> values) {
            addCriterion("content not in", values, "content");
            return (Criteria) this;
        }

        public Criteria andContentBetween(String value1, String value2) {
            addCriterion("content between", value1, value2, "content");
            return (Criteria) this;
        }

        public Criteria andContentNotBetween(String value1, String value2) {
            addCriterion("content not between", value1, value2, "content");
            return (Criteria) this;
        }

        public Criteria andTimeIsNull() {
            addCriterion("`time` is null");
            return (Criteria) this;
        }

        public Criteria andTimeIsNotNull() {
            addCriterion("`time` is not null");
            return (Criteria) this;
        }

        public Criteria andTimeEqualTo(LocalDateTime value) {
            addCriterion("`time` =", value, "time");
            return (Criteria) this;
        }

        public Criteria andTimeNotEqualTo(LocalDateTime value) {
            addCriterion("`time` <>", value, "time");
            return (Criteria) this;
        }

        public Criteria andTimeGreaterThan(LocalDateTime value) {
            addCriterion("`time` >", value, "time");
            return (Criteria) this;
        }

        public Criteria andTimeGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("`time` >=", value, "time");
            return (Criteria) this;
        }

        public Criteria andTimeLessThan(LocalDateTime value) {
            addCriterion("`time` <", value, "time");
            return (Criteria) this;
        }

        public Criteria andTimeLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("`time` <=", value, "time");
            return (Criteria) this;
        }

        public Criteria andTimeIn(List<LocalDateTime> values) {
            addCriterion("`time` in", values, "time");
            return (Criteria) this;
        }

        public Criteria andTimeNotIn(List<LocalDateTime> values) {
            addCriterion("`time` not in", values, "time");
            return (Criteria) this;
        }

        public Criteria andTimeBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("`time` between", value1, value2, "time");
            return (Criteria) this;
        }

        public Criteria andTimeNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("`time` not between", value1, value2, "time");
            return (Criteria) this;
        }

        public Criteria andStatusIsNull() {
            addCriterion("`status` is null");
            return (Criteria) this;
        }

        public Criteria andStatusIsNotNull() {
            addCriterion("`status` is not null");
            return (Criteria) this;
        }

        public Criteria andStatusEqualTo(String value) {
            addCriterion("`status` =", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotEqualTo(String value) {
            addCriterion("`status` <>", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusGreaterThan(String value) {
            addCriterion("`status` >", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusGreaterThanOrEqualTo(String value) {
            addCriterion("`status` >=", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusLessThan(String value) {
            addCriterion("`status` <", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusLessThanOrEqualTo(String value) {
            addCriterion("`status` <=", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusLike(String value) {
            addCriterion("`status` like", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotLike(String value) {
            addCriterion("`status` not like", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusIn(List<String> values) {
            addCriterion("`status` in", values, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotIn(List<String> values) {
            addCriterion("`status` not in", values, "status");
            return (Criteria) this;
        }

        public Criteria andStatusBetween(String value1, String value2) {
            addCriterion("`status` between", value1, value2, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotBetween(String value1, String value2) {
            addCriterion("`status` not between", value1, value2, "status");
            return (Criteria) this;
        }

        public Criteria andExpiretimeIsNull() {
            addCriterion("expiretime is null");
            return (Criteria) this;
        }

        public Criteria andExpiretimeIsNotNull() {
            addCriterion("expiretime is not null");
            return (Criteria) this;
        }

        public Criteria andExpiretimeEqualTo(LocalDateTime value) {
            addCriterion("expiretime =", value, "expiretime");
            return (Criteria) this;
        }

        public Criteria andExpiretimeNotEqualTo(LocalDateTime value) {
            addCriterion("expiretime <>", value, "expiretime");
            return (Criteria) this;
        }

        public Criteria andExpiretimeGreaterThan(LocalDateTime value) {
            addCriterion("expiretime >", value, "expiretime");
            return (Criteria) this;
        }

        public Criteria andExpiretimeGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("expiretime >=", value, "expiretime");
            return (Criteria) this;
        }

        public Criteria andExpiretimeLessThan(LocalDateTime value) {
            addCriterion("expiretime <", value, "expiretime");
            return (Criteria) this;
        }

        public Criteria andExpiretimeLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("expiretime <=", value, "expiretime");
            return (Criteria) this;
        }

        public Criteria andExpiretimeIn(List<LocalDateTime> values) {
            addCriterion("expiretime in", values, "expiretime");
            return (Criteria) this;
        }

        public Criteria andExpiretimeNotIn(List<LocalDateTime> values) {
            addCriterion("expiretime not in", values, "expiretime");
            return (Criteria) this;
        }

        public Criteria andExpiretimeBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("expiretime between", value1, value2, "expiretime");
            return (Criteria) this;
        }

        public Criteria andExpiretimeNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("expiretime not between", value1, value2, "expiretime");
            return (Criteria) this;
        }

        public Criteria andSenderidIsNull() {
            addCriterion("senderid is null");
            return (Criteria) this;
        }

        public Criteria andSenderidIsNotNull() {
            addCriterion("senderid is not null");
            return (Criteria) this;
        }

        public Criteria andSenderidEqualTo(Integer value) {
            addCriterion("senderid =", value, "senderid");
            return (Criteria) this;
        }

        public Criteria andSenderidNotEqualTo(Integer value) {
            addCriterion("senderid <>", value, "senderid");
            return (Criteria) this;
        }

        public Criteria andSenderidGreaterThan(Integer value) {
            addCriterion("senderid >", value, "senderid");
            return (Criteria) this;
        }

        public Criteria andSenderidGreaterThanOrEqualTo(Integer value) {
            addCriterion("senderid >=", value, "senderid");
            return (Criteria) this;
        }

        public Criteria andSenderidLessThan(Integer value) {
            addCriterion("senderid <", value, "senderid");
            return (Criteria) this;
        }

        public Criteria andSenderidLessThanOrEqualTo(Integer value) {
            addCriterion("senderid <=", value, "senderid");
            return (Criteria) this;
        }

        public Criteria andSenderidIn(List<Integer> values) {
            addCriterion("senderid in", values, "senderid");
            return (Criteria) this;
        }

        public Criteria andSenderidNotIn(List<Integer> values) {
            addCriterion("senderid not in", values, "senderid");
            return (Criteria) this;
        }

        public Criteria andSenderidBetween(Integer value1, Integer value2) {
            addCriterion("senderid between", value1, value2, "senderid");
            return (Criteria) this;
        }

        public Criteria andSenderidNotBetween(Integer value1, Integer value2) {
            addCriterion("senderid not between", value1, value2, "senderid");
            return (Criteria) this;
        }

        public Criteria andIsReadIsNull() {
            addCriterion("is_read is null");
            return (Criteria) this;
        }

        public Criteria andIsReadIsNotNull() {
            addCriterion("is_read is not null");
            return (Criteria) this;
        }

        public Criteria andIsReadEqualTo(String value) {
            addCriterion("is_read =", value, "isRead");
            return (Criteria) this;
        }

        public Criteria andIsReadNotEqualTo(String value) {
            addCriterion("is_read <>", value, "isRead");
            return (Criteria) this;
        }

        public Criteria andIsReadGreaterThan(String value) {
            addCriterion("is_read >", value, "isRead");
            return (Criteria) this;
        }

        public Criteria andIsReadGreaterThanOrEqualTo(String value) {
            addCriterion("is_read >=", value, "isRead");
            return (Criteria) this;
        }

        public Criteria andIsReadLessThan(String value) {
            addCriterion("is_read <", value, "isRead");
            return (Criteria) this;
        }

        public Criteria andIsReadLessThanOrEqualTo(String value) {
            addCriterion("is_read <=", value, "isRead");
            return (Criteria) this;
        }

        public Criteria andIsReadLike(String value) {
            addCriterion("is_read like", value, "isRead");
            return (Criteria) this;
        }

        public Criteria andIsReadNotLike(String value) {
            addCriterion("is_read not like", value, "isRead");
            return (Criteria) this;
        }

        public Criteria andIsReadIn(List<String> values) {
            addCriterion("is_read in", values, "isRead");
            return (Criteria) this;
        }

        public Criteria andIsReadNotIn(List<String> values) {
            addCriterion("is_read not in", values, "isRead");
            return (Criteria) this;
        }

        public Criteria andIsReadBetween(String value1, String value2) {
            addCriterion("is_read between", value1, value2, "isRead");
            return (Criteria) this;
        }

        public Criteria andIsReadNotBetween(String value1, String value2) {
            addCriterion("is_read not between", value1, value2, "isRead");
            return (Criteria) this;
        }
    }

    public static class Criteria extends GeneratedCriteria {
        private NotiPoExample example;

        protected Criteria(NotiPoExample example) {
            super();
            this.example = example;
        }

        public NotiPoExample example() {
            return this.example;
        }

        @Deprecated
        public Criteria andIf(boolean ifAdd, ICriteriaAdd add) {
            if (ifAdd) {
                add.add(this);
            }
            return this;
        }

        public Criteria when(boolean condition, ICriteriaWhen then) {
            if (condition) {
                then.criteria(this);
            }
            return this;
        }

        public Criteria when(boolean condition, ICriteriaWhen then, ICriteriaWhen otherwise) {
            if (condition) {
                then.criteria(this);
            } else {
                otherwise.criteria(this);
            }
            return this;
        }

        @Deprecated
        public interface ICriteriaAdd {
            Criteria add(Criteria add);
        }
    }

    public static class Criterion {
        private String condition;

        private Object value;

        private Object secondValue;

        private boolean noValue;

        private boolean singleValue;

        private boolean betweenValue;

        private boolean listValue;

        private String typeHandler;

        public String getCondition() {
            return condition;
        }

        public Object getValue() {
            return value;
        }

        public Object getSecondValue() {
            return secondValue;
        }

        public boolean isNoValue() {
            return noValue;
        }

        public boolean isSingleValue() {
            return singleValue;
        }

        public boolean isBetweenValue() {
            return betweenValue;
        }

        public boolean isListValue() {
            return listValue;
        }

        public String getTypeHandler() {
            return typeHandler;
        }

        protected Criterion(String condition) {
            super();
            this.condition = condition;
            this.typeHandler = null;
            this.noValue = true;
        }

        protected Criterion(String condition, Object value, String typeHandler) {
            super();
            this.condition = condition;
            this.value = value;
            this.typeHandler = typeHandler;
            if (value instanceof List<?>) {
                this.listValue = true;
            } else {
                this.singleValue = true;
            }
        }

        protected Criterion(String condition, Object value) {
            this(condition, value, null);
        }

        protected Criterion(String condition, Object value, Object secondValue, String typeHandler) {
            super();
            this.condition = condition;
            this.value = value;
            this.secondValue = secondValue;
            this.typeHandler = typeHandler;
            this.betweenValue = true;
        }

        protected Criterion(String condition, Object value, Object secondValue) {
            this(condition, value, secondValue, null);
        }
    }

    public interface ICriteriaWhen {
        void criteria(Criteria criteria);
    }

    public interface IExampleWhen {
        void example(com.liang.bbs.user.persistence.entity.NotiPoExample example);
    }
}