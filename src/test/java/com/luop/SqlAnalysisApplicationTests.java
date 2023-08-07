package com.luop;


import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

class SqlAnalysisApplicationTests {

    @Test
    public void testPlainSql(){
        String sql = "select u.name userName,u.id,d.name depName,d.id depId " +
                "from user u left join department d on u.depId = d.id where u.id = 1";
        try {
            Map<String, Set<String>> map = SelectParseHelper.getBloodRelationResult(sql);
            System.out.println(map);   //{depId=[department.id], userName=[user.name], id=[user.id], depName=[department.name]}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testSubQuerySql(){
        String sql = "       SELECT t1.customer_id, t1.total_spent, t2.total_orders, date() as curdate " +
                "        FROM ( " +
                "                 SELECT customer_id, SUM(order_total) AS total_spent " +
                "                 FROM customer_summary " +
                "                 GROUP BY customer_id " +
                "                 HAVING total_spent > 1000 " +
                "             ) AS t1 " +
                "                 JOIN ( " +
                "            SELECT customer_id, COUNT(DISTINCT order_id) AS total_orders " +
                "            FROM customer_summary " +
                "            GROUP BY customer_id " +
                " " +
                "        ) AS t2 ON t1.customer_id = t2.customer_id;";
        try {
            Map<String, Set<String>> map = SelectParseHelper.getBloodRelationResult(sql);
            System.out.println(map);   //{total_spent=[customer_summary.order_total], customer_id=[customer_summary.customer_id], total_orders=[customer_summary.order_id]}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testUnionSql(){
        String sql = "SELECT t1.customer_id as id, t1.order_total as money FROM customer1 t1 UNION ALL SELECT t2.customer_id as id, t2.order_total as money FROM customer2 t2";
        try {
            Map<String, Set<String>> map = SelectParseHelper.getBloodRelationResult(sql);
            System.out.println(map);   //{money=[customer1.order_total, customer2.order_total], id=[customer1.customer_id, customer2.customer_id]}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
