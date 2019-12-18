package com.luop;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.OldOracleJoinBinaryExpression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * @Author: luoping
 * @Date: 2019/11/5 09:50
 * @Description:   表之间的关系
 */
public class MySelectVisitor2 implements SelectVisitor {
    //用来存储查询SQL语句涉及到的表和字段
    @Override
    public void visit(PlainSelect pSelect) {
        processFromItem(pSelect, pSelect.getFromItem());
        //子查询处理,joins处理
        List<Join> joins = pSelect.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                processFromItem(pSelect, join.getRightItem());
            }
        }
    }

    private Map<String, String> tableMap = new HashMap<>();
    private Map<String, Set<String>> mappingMap = new HashMap<>();

    private void processFromItem(PlainSelect pSelect, FromItem fromItem) {
        List<Join> joins = pSelect.getJoins();
        if (!CollectionUtils.isEmpty(joins) && joins.size() > 0) {
            putTableMap(fromItem);

            //解析join
            for (Join join : joins) {
                putTableMap(join.getRightItem());
                Expression onExpression = join.getOnExpression();
                analysisCondition(onExpression);
            }
        }
    }

    //解析表间字段映射关系
    private Expression analysisCondition(Expression expression) {
        while (expression instanceof AndExpression) {
            AndExpression andExpression = (AndExpression) expression;
            analysisCondition(andExpression.getRightExpression());
            Expression leftExpression = andExpression.getLeftExpression();
            if (leftExpression instanceof OldOracleJoinBinaryExpression) {
                expression = leftExpression;
            } else {
                expression = analysisCondition(leftExpression);
            }
        }
        if (expression instanceof OldOracleJoinBinaryExpression) {
            String leftStr = ((OldOracleJoinBinaryExpression) expression).getLeftExpression().toString();
            String rightStr = ((OldOracleJoinBinaryExpression) expression).getRightExpression().toString();
            if (mappingMap.containsKey(leftStr)) {
                mappingMap.get(leftStr).add(rightStr);
            } else {
                Set<String> set = new HashSet<>();
                set.add(rightStr);
                mappingMap.put(leftStr, set);
            }
        }
        return expression;
    }


    //表之间的映射关系
    Map<String, Set<String>> tableMapping() {
        Map<String, Set<String>> map = new HashMap<>();
        if (!CollectionUtils.isEmpty(mappingMap)) {
            Set<String> keys = mappingMap.keySet();
            for (String key : keys) {
                Set<String> set = new HashSet<>();
                Set<String> values = mappingMap.get(key);
                if (!CollectionUtils.isEmpty(values)) {
                    for (String value : values) {
                        set.add(replaceAliasName(value));
                    }
                }
                map.put(replaceAliasName(key), set);
            }
        }
        return map;
    }

    //替换表别名
    private String replaceAliasName(String value) {
        if (value.contains(".")) {
            String[] target = value.split("[.]");
            return value.replace(target[0], tableMap.get(target[0]));
        }
        return value;
    }


    //将表按 别名：真实名 存进map中
    private void putTableMap(FromItem fromItem) {
        String tableName = getTableName((Table) fromItem);
        String tableAliasName = fromItem.getAlias().getName();
        tableMap.put(tableAliasName, tableName);
    }

    //获取表名（包含 数据库名，表空间名）
    private String getTableName(Table fromItem) {
        String name = fromItem.getName();  //表名
        String databaseName = fromItem.getDatabase().getDatabaseName();   //数据库名
        String schemaName = fromItem.getSchemaName();    //表空间名
        if (Objects.isNull(databaseName) && !Objects.isNull(schemaName))
            return schemaName + "." + name;
        if (!Objects.isNull(databaseName)) {
            return databaseName + "." + (schemaName == null ? "" : schemaName) + "." + name;
        }
        return name;
    }

    @Override
    public void visit(SetOperationList sOperationList) {
        //union all会用到
        List<PlainSelect> pSelects = sOperationList.getPlainSelects();
        for (PlainSelect pSelect : pSelects) {
            this.visit(pSelect);
        }
    }

    @Override
    public void visit(WithItem wItem) {
//        System.out.println("WithItem=========" + wItem);
    }
}
