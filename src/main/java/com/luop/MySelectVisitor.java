package com.luop;

import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * @Author: luoping
 * @Date: 2019/11/5 09:50
 * @Description:
 */
public class MySelectVisitor implements SelectVisitor {
    //用来存储查询SQL语句涉及到的表和字段
    @Override
    public void visit(PlainSelect pSelect) {
        processFromItem(pSelect, pSelect.getFromItem());
        //子查询处理,joins处理
        List<Join> joins = pSelect.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                tableAliasName = "";   //重置表别名
                processFromItem(pSelect, join.getRightItem());
            }
        }
    }

    /**
     * 处理fromItem，并将处理结果保存起来
     *
     * @param pSelect  当前from信息对应的select信息
     * @param fromItem from信息
     */
    private Map<String, String> columnMappingMap = new HashMap<>();  //别名：真实字段名
    private Map<String, Set<String>> map = new HashMap<>();   //装查询字段
    private boolean state = true;
    private String tableAliasName;

    //处理查询字段里的函数
    private Expression analysisFunction(Expression expression, Map<String, Set<String>> map, String aliasName) {
        while (judgeExpression(expression)) {
            while (expression instanceof CaseExpression) {   //case when 函数
                Expression elseExpression = ((CaseExpression) expression).getElseExpression();   //获取else表达式
                if (judgeExpression(elseExpression))
                    expression = elseExpression;
                else if (!Objects.isNull(elseExpression) && judgeValueExpression(elseExpression))
                    putSelectMap(elseExpression, map, aliasName);

                for (Expression whenClause : ((CaseExpression) expression).getWhenClauses()) {    //when条件
                    WhenClause clause = (WhenClause) whenClause;
                    expression = analysisFunction(clause.getThenExpression(), map, aliasName);   //获取then表达式
                }
            }
            while (expression instanceof Function) {    //函数
                Function function = (Function) expression;
                List<Expression> expressions = function.getParameters().getExpressions();
                for (Expression ex : expressions) {
                    if (judgeExpression(ex)) {
                        expression = analysisFunction(ex, map, aliasName);
                    } else {
                        if (judgeValueExpression(ex))
                            expression = analysisFunction(ex, map, aliasName);
                    }
                }
            }
            while (expression instanceof CastExpression) {    //cast函数
                Expression leftExpression = ((CastExpression) expression).getLeftExpression();
                if (judgeExpression(leftExpression))
                    expression = leftExpression;
            }
            while (expression instanceof BinaryExpression) {   //concat函数
                Expression leftExpression = ((BinaryExpression) expression).getLeftExpression();    //左侧表达式;
                if (judgeExpression(leftExpression)) {
                    expression = leftExpression;
                } else {
                    if (judgeValueExpression(leftExpression)) {
                        putSelectMap(leftExpression, map, aliasName);
                    }
                    expression = ((BinaryExpression) expression).getRightExpression();     // 右侧表达式
                }
            }
        }
        if (judgeValueExpression(expression))
            putSelectMap(expression, map, aliasName);
        return expression;
    }

    //保存查询字段
    private void putSelectMap(Expression expression, Map<String, Set<String>> map, String aliasName) {
        Set<String> set = new HashSet<>();
        if (!map.containsKey(aliasName)) {
            set.add(expression.toString());
        } else {
            set = map.get(aliasName);
            set.add(expression.toString());
        }
        map.put(aliasName, set);
    }

    private void processFromItem(PlainSelect pSelect, FromItem fromItem) {
        if (state) {   //将查询字段按 字段名：别名 封装到map中
            List<SelectItem> selectColumn = pSelect.getSelectItems();
            for (SelectItem selectItem : selectColumn) {
                SelectExpressionItem expressionItem = (SelectExpressionItem) selectItem;
                String aliasName;//字段别名
                if (expressionItem.getAlias() == null) {    //没有别名
                    aliasName = ((Column) expressionItem.getExpression()).getColumnName();
                } else {   //有别名
                    aliasName = expressionItem.getAlias().getName();
                }
                Expression expression = expressionItem.getExpression();
                analysisFunction(expression, map, aliasName);
            }
        }
        if (!state && fromItem.getAlias() != null) {
            if (!StringUtils.isEmpty(tableAliasName))
                columnMappingMap.put(tableAliasName, fromItem.getAlias().getName());   //外层子查询别名：子查询别名
        }
        if (fromItem instanceof Table) {  //表
            String tableName = getTableName((Table) fromItem);
            if (fromItem.getAlias() != null) {
                tableAliasName = fromItem.getAlias().getName();
            }
            columnMappingMap.put(tableAliasName, tableName);    //表别名：表真实名
            tableAliasName = Objects.isNull(tableAliasName) ? tableName : tableAliasName;
        } else if (fromItem instanceof SubSelect) {  //子查询
            state = false;
            tableAliasName = Objects.requireNonNull(fromItem.getAlias()).getName();   //表别名
            ((SubSelect) fromItem).getSelectBody().accept(this);
        }
    }

    //获取表名（包含 数据库名，表空间名）
    private String getTableName(Table fromItem) {
        String name = fromItem.getName();  //表名
        String databaseName = fromItem.getDatabase().getDatabaseName();   //数据库名
        String schemaName = fromItem.getSchemaName();    //表空间名
        if (!Objects.isNull(databaseName) && !Objects.isNull(schemaName))
            return databaseName + "." + schemaName + "." + name;
        if (Objects.isNull(databaseName) && !Objects.isNull(schemaName))
            return schemaName + "." + name;
        return name;
    }

    @Override
    public void visit(SetOperationList sOperationList) {
        //union all会用到
//        List<PlainSelect> pSelects = sOperationList.getPlainSelects();
//        for (PlainSelect pSelect : pSelects) {
//            this.visit(pSelect);
//        }
    }

    @Override
    public void visit(WithItem wItem) {
//        System.out.println("WithItem=========" + wItem);
    }

    /**
     * 返回SQL解析的最终结果
     * 最终结果为血缘关系（别名字段来源真实表字段）
     *
     * @return
     */
    Map<String, Set<String>> getSqlParseResult() {
//        System.out.println("columnMappingMap: " + columnMappingMap);
//        System.out.println("map:" + map);
        return getColumnMapping();
    }

    //封装血缘关系
    private Map<String, Set<String>> getColumnMapping() {
        Map<String, Set<String>> map2 = new HashMap<>();
        Set<String> keySet = map.keySet();
        for (String str : keySet) {
            Set<String> columnSet = new HashSet<>();
            Set<String> set = map.get(str);
            for (String s : set) {
                if (s.contains(".")) {
                    String[] split = s.split("[.]");
                    s = getMap(split[0]) + "." + split[1];
                }
                columnSet.add(s);
                map2.put(str, columnSet);
            }
        }
        return map2;
    }

    //循环查询直到找到真实表名
    private String getMap(String str) {
        while (columnMappingMap.containsKey(str)) {
            str = columnMappingMap.get(str);
        }
        return str;
    }

    //判断查询字段中是否包含函数
    private boolean judgeExpression(Expression expression) {
        if (!Objects.isNull(expression))
            return expression instanceof Function || expression instanceof CastExpression || expression instanceof CaseExpression || expression instanceof BinaryExpression;
        else
            return false;
    }

    //判断expression是否为值类型
    private boolean judgeValueExpression(Expression expression) {
        return !(expression instanceof StringValue) && !(expression instanceof DoubleValue) && !(expression instanceof LongValue);
    }

    /*
     * Function: isnull,ifnull,wm_concat,replace
     * CastExpression: cast,
     * CaseExpression: case when
     * StringValue: ""
     * LongValue:  11
     * DoubleValue: 2.35
     * */
}
