package com.luop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.Set;

@SpringBootTest
class SqlAnalysisApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void test(){
        String sql = "select u.name userName,u.id,d.name depName,d.id depId " +
                "from user u left join department d on u.depId = d.id where u.id = 1";
        try {
            Map<String, Set<String>> map = SelectParseHelper.getBloodRelationResult(sql);
            System.out.println(map);   //{depId=[department.id], userName=[user.name], id=[user.id], depName=[department.name]}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
