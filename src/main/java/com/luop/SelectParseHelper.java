package com.luop;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SetOperationList;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * SQL解析利用开源的JSQLParser来实现，参考网址：https://github.com/JSQLParser/JSqlParser
 *
 * @Author: luoping
 * @Date: 2019/11/5 09:50
 * @Description:
 *
 */
class SelectParseHelper {
    /**
     * 获取血缘关系结果
     * @param sqltxt 要解析的SQL语句
     * @return
     * @throws Exception
     */
    static Map<String,Set<String>> getBloodRelationResult(String sqltxt) throws Exception{
        //第三方插件解析sql
        Statement stmt = CCJSqlParserUtil.parse(sqltxt);     //报错 说明sql语句错误
        Select selectStatement=(Select)stmt;
        MySelectVisitor mySelectVisitor = new MySelectVisitor();
        SelectBody sBody = selectStatement.getSelectBody();
        Map<String, Set<String>> columnsMappingMap = new HashMap<>();
        if (sBody instanceof SetOperationList) {
            mySelectVisitor.visit((SetOperationList) sBody, columnsMappingMap);
        } else {
            mySelectVisitor.visit((PlainSelect) sBody, columnsMappingMap, false);
        }
        return columnsMappingMap;
    }
}
