package com.luop;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;

import java.util.Map;
import java.util.Set;

/**
 * SQL解析帮助类
 * 约束：
 * 	        仅支持SELECT语句的解析
 *     查询字段不可以为*
 *     表必须要使用别名，查询字段必须使用表的别名(例：select t.id,t.address from userinfo t)
 * 说明：
 *     该方法的使用场景：控制用户不要越权访问表或者表的字段
 *     出于上述使用场景，SQL解析时只关心where之前的部分，包括子查询语句。解析结果返回所有查询涉及到的表和查询的字段。
 *     SQL解析利用开源的JSQLParser来实现，参考网址：https://github.com/JSQLParser/JSqlParser
 *
 * @author "朱云山"
 *
 */
class SelectParseHelper {
    /**
     * 获取SQL语句中用到的所有表和查询字段名称(名称均为大写)
     *
     * @param sqltxt 要解析的SQL语句
     * @return
     * @throws Exception
     */
    public static Map<String,Set<String>> getTableAndColumns(String sqltxt) throws Exception{
        Statement stmt = CCJSqlParserUtil.parse(sqltxt);     //报错 说明sql语句错误
        Select selectStatement=(Select)stmt;

        MySelectVisitor mySelectVisitor=new MySelectVisitor();
        SelectBody sBody=selectStatement.getSelectBody();
        sBody.accept(mySelectVisitor);
        return mySelectVisitor.getSqlParseResult();
    }
}
