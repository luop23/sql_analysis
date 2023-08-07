package com.luop;

/**
 * @author wxn
 * @date 2023/8/7
 */
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;


/**
 * @Author: wxn
 * @Date: 2023/05/25
 * 用递归来解决多层嵌套子查询的问题
 */
public class MySelectVisitor {
    /**
     * 递归visit，通过对子查询visit并合并columnsMapping实现整体语句的解析
     * @param setList
     * @param columnsMapping
     */
    public void visit(SetOperationList setList, Map<String, Set<String>> columnsMapping) {
        List<SelectBody> setSelectBodies =  setList.getSelects();
        List<Map<String, Set<String>>> subSubQueryColumnsMappings = new ArrayList<>(setSelectBodies.size());
        for (SelectBody subSubselectBody : setSelectBodies){
            Map<String, Set<String>> map = new HashMap<>();
            this.visit((PlainSelect) subSubselectBody, map, false);
            subSubQueryColumnsMappings.add(map);
        }
        mergeSetQueryColumnsMappings(subSubQueryColumnsMappings, columnsMapping);
    }

    public void visit(PlainSelect pSelect, Map<String, Set<String>> columnsMapping, boolean isSubQuery) {
        processFromItem(pSelect, pSelect.getFromItem(), columnsMapping, isSubQuery);
        List<Join> joins = pSelect.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                //将join的语句当作from语句处理（都可能是table或者subQuery)
                FromItem rightItem = join.getRightItem();
                visitFromItem(columnsMapping, rightItem);
            }
        }
    }

    /**
     * 递归visit，通过对子查询visit并合并columnsMapping实现整体语句的解析
     * @param pSelect
     * @param columnsMapping
     */
    private void processFromItem(PlainSelect pSelect, FromItem fromItem, Map<String, Set<String>> columnsMapping, boolean isSubQuery) {
        //解析select字段
        List<SelectItem> selectColumn = pSelect.getSelectItems();
        for (SelectItem selectItem : selectColumn) {
            SelectExpressionItem expressionItem = (SelectExpressionItem) selectItem;
            String aliasName;
            Expression expression = expressionItem.getExpression();
            if (isSubQuery){
                if (expressionItem.getAlias() == null){
                    aliasName = expression.toString();
                }else {
                    if (expression.toString().contains(".")){
                        aliasName = expression.toString().split("[.]")[0] + "." + expressionItem.getAlias().getName();
                    }else {
                        aliasName = expressionItem.getAlias().getName();
                    }
                }

            }else {
                if (expressionItem.getAlias() == null) {
                    //类似select count(0) from table的情况
                    if (expression instanceof Function){
                        aliasName = expression.toString();
                    }else {
                        aliasName = ((Column) expressionItem.getExpression()).getColumnName();
                    }
                } else {   //有别名
                    aliasName = expressionItem.getAlias().getName();
                }
            }
            analysisFunction(expression, columnsMapping, aliasName);
        }
        if (columnsMapping.isEmpty()){
            return;
        }

        //解析from语句
        visitFromItem(columnsMapping, fromItem);
    }

    /**
     * 用在join的右子句或者from子句，都可能出现表或者子查询
     *
     * @param columnsMapping
     * @param fromItem
     */
    private void visitFromItem(Map<String, Set<String>> columnsMapping, FromItem fromItem) {
        String tableAliasName = null;
        if (fromItem instanceof Table) {
            String tableName = getTableName((Table) fromItem);
            if (fromItem.getAlias() != null) {
                tableAliasName = fromItem.getAlias().getName();
            }
            tableAliasName = Objects.isNull(tableAliasName) ? tableName : tableAliasName;
            setColumnMappingMap(columnsMapping, tableName, tableAliasName);
        } else if (fromItem instanceof SubSelect) {
            Map<String, Set<String>> subQueryColumnsMapping = new HashMap<>();
            // 如果是UNION则需要进一步处理
            SelectBody subSelectBody = ((SubSelect) fromItem).getSelectBody();
            if (subSelectBody instanceof SetOperationList){
                tableAliasName = Objects.requireNonNull(fromItem.getAlias()).getName();
                List<SelectBody> setSelectBodies = ((SetOperationList) subSelectBody).getSelects();
                List<Map<String, Set<String>>> subSubQueryColumnsMappings = new ArrayList<>(setSelectBodies.size());
                for (SelectBody subSubselectBody : setSelectBodies){
                    Map<String, Set<String>> map = new HashMap<>();
                    this.visit((PlainSelect) subSubselectBody, map, true);
                    updateSubQueryMappingWithSubQueryAliasName(map, tableAliasName);
                    subSubQueryColumnsMappings.add(map);
                }
                mergeSetQueryColumnsMappings(subSubQueryColumnsMappings, subQueryColumnsMapping);
            }else {
                tableAliasName = Objects.requireNonNull(fromItem.getAlias()).getName();
                this.visit((PlainSelect) subSelectBody, subQueryColumnsMapping, true);
                //先处理子查询的结果
                updateSubQueryMappingWithSubQueryAliasName(subQueryColumnsMapping, tableAliasName);
            }
            //解析结束，开始合并并替换别名，生成最后的columnsMapping
            mergeSubQueryColumnMapIntoColumnMapping(columnsMapping, subQueryColumnsMapping);
        }
    }

    private  void mergeSetQueryColumnsMappings(List<Map<String, Set<String>>> maps, Map<String, Set<String>> resultMap){
        for (Map<String, Set<String>>map : maps){
            for (String key: map.keySet()){
                if (resultMap.containsKey(key)){
                    Set<String> columns = resultMap.get(key);
                    columns.addAll(map.get(key));
                    resultMap.put(key, columns);
                }else {
                    resultMap.put(key, map.get(key));
                }
            }
        }
    }

    //处理查询字段里的函数
    private Expression analysisFunction(Expression expression, Map<String, Set<String>> map, String aliasName) {
        while (judgeExpression(expression)) {

            while (expression instanceof CaseExpression) {
                //case when 函数
                Expression elseExpression = ((CaseExpression) expression).getElseExpression();
                //获取else表达式
                if (judgeExpression(elseExpression)) {
                    expression = elseExpression;
                } else if (!Objects.isNull(elseExpression) && judgeValueExpression(elseExpression)) {
                    putSelectMap(elseExpression, map, aliasName);
                }
                for (Expression whenClause : ((CaseExpression) expression).getWhenClauses()) {
                    //when条件
                    WhenClause clause = (WhenClause) whenClause;
                    //获取then表达式
                    expression = analysisFunction(clause.getThenExpression(), map, aliasName);
                }
            }
            while (expression instanceof Function) {
                //函数
                Function function = (Function) expression;
                //没有参数的函数直接返回，没有分析价值
                if (function.getParameters() == null){
                    return expression;
                }
                List<Expression> expressions = function.getParameters().getExpressions();
                for (Expression ex : expressions) {
                    if (judgeExpression(ex)) {
                        expression = analysisFunction(ex, map, aliasName);
                    } else {
                        if (judgeValueExpression(ex)) {
                            expression = analysisFunction(ex, map, aliasName);
                        }else {
                            //函数的参数是常量的直接返回，没有分析价值
                            return expression;
                        }
                    }
                }
            }
            while (expression instanceof CastExpression) {
                //cast函数
                Expression leftExpression = ((CastExpression) expression).getLeftExpression();
                if (judgeExpression(leftExpression)) {
                    expression = leftExpression;
                }
            }
            while (expression instanceof BinaryExpression) {
                //concat函数
                //左侧表达式;
                Expression leftExpression = ((BinaryExpression) expression).getLeftExpression();
                if (judgeExpression(leftExpression)) {
                    expression = leftExpression;
                } else {
                    if (judgeValueExpression(leftExpression)) {
                        putSelectMap(leftExpression, map, aliasName);
                    }
                    // 右侧表达式
                    expression = ((BinaryExpression) expression).getRightExpression();
                }
            }
        }
        if (judgeValueExpression(expression)) {
            putSelectMap(expression, map, aliasName);
        }
        return expression;
    }

    //保存查询字段
    private void putSelectMap(Expression expression, Map<String, Set<String>> map, String aliasName) {
        Set<String> set = new HashSet<>();
        if (!map.containsKey(aliasName)) {
            set.add(clearString(expression.toString()));
        } else {
            set = map.get(aliasName);
            set.add(clearString(expression.toString()));
        }
        map.put(clearString(aliasName), set);
    }


    /**
     * 将子查询中的字段映射关系 合并到select解析结果中
     * 如：select t1.field1 from (select field1 from table1) as t1，
     * 提取的select结果为：                   field1 -> {t1.field1}
     * 经过子查询别名更新后的子查询字段映射关系为： t1.field1 -> {table1.field1}
     * 合并后的最终结果为：                    field1 -> {table1.field1}
     *
     * @param columnsMapping
     * @param subQueryMapping
     */
    private void mergeSubQueryColumnMapIntoColumnMapping(Map<String, Set<String>> columnsMapping, Map<String, Set<String>> subQueryMapping) {
        for (String str : columnsMapping.keySet()) {
            Set<String> newSet = new HashSet<>();
            Set<String> set = columnsMapping.get(str);
            for (String s : set) {
                newSet.addAll(subQueryMapping.containsKey(s) ? subQueryMapping.get(s) : Collections.singleton(s));
            }
            columnsMapping.put(str, newSet);
        }
    }

    /**
     * 用子查询的别名来更新子查询中的字段映射关系
     * 如：(select field1 from table1) as t1
     * 提取的子查询字段映射关系为：          field1->{table1.field1}
     * 经过子查询别名映射后，更新为：        t1.field1->{table1.field1}
     *
     * @param columnsMapping
     * @param subQueryAliasName
     */
    private void updateSubQueryMappingWithSubQueryAliasName(Map<String, Set<String>> columnsMapping, String subQueryAliasName) {
        Map<String, Set<String>> map2 = new HashMap<>();
        for (String str : columnsMapping.keySet()) {
            if (str.contains(".")) {
                String[] split = str.split("[.]");
                map2.put(subQueryAliasName + "." + split[1], columnsMapping.get(str));
            } else {
                map2.put(subQueryAliasName + "." + str, columnsMapping.get(str));
            }
        }
        columnsMapping.clear();
        columnsMapping.putAll(map2);
    }

    private void setColumnMappingMap(Map<String, Set<String>> columnsMapping, String tableName, String tableAliasName) {
        for (String str : columnsMapping.keySet()) {
            Set<String> columnSet = new HashSet<>();
            Set<String> set = columnsMapping.get(str);
            for (String s : set) {
                if (s.contains(".")) {
                    String[] split = s.split("[.]");
                    s = (tableAliasName.equalsIgnoreCase(split[0])) ? tableName + "." + split[1] : s;
                } else {
                    s = tableName + "." + s;
                }
                columnSet.add(s);
            }
            columnsMapping.put(str, columnSet);
        }
    }

    //获取表名（包含 数据库名，表空间名）
    //TODO: 暂时不处理跨库，仅对表名进行限制
    private String getTableName(Table fromItem) {
        //表名
        String name = fromItem.getName();
        //数据库名
        String databaseName = fromItem.getDatabase().getDatabaseName();
        //表空间名
        String schemaName = fromItem.getSchemaName();
        if (Objects.isNull(databaseName) && !Objects.isNull(schemaName)) {
            return schemaName + "." + name;
        }
        if (!Objects.isNull(databaseName)) {
            return databaseName + "." + (schemaName == null ? "" : schemaName) + "." + name;
        }
        return name;
    }


    //判断查询字段中是否包含函数
    private boolean judgeExpression(Expression expression) {
        if (!Objects.isNull(expression)) {
            return expression instanceof Function || expression instanceof CastExpression || expression instanceof CaseExpression || expression instanceof BinaryExpression;
        }
        return false;
    }

    //判断expression是否为值类型
    private boolean judgeValueExpression(Expression expression) {
        return !(expression instanceof StringValue) && !(expression instanceof DoubleValue) && !(expression instanceof LongValue);
    }

    private String clearString(String s){
        return s.replace("`","");
    }
}
