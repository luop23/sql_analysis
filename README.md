基于jsqlparser 进行sql语句解析，得到字段间的血缘关系
==

工具说明：
-
    1、本工具只解析select语句
    2、支持子查询和union查询
    3、支持常用数据库的常用函数，如concat、case when、nvl、isnull、cast、ifnull等等
    4、工具可能对某些sql无法解析，有待完善
    
    
注意事项
-   
    1、在调用方法前请确保sql的准确性，本工具只做了简单的判断
    2、当多表查询时，请给表和字段附上相应别名，如若没有，可能会对结果产生影响
    
事例
-
    参考测试类 test()

参考网址
-
    https://github.com/JSQLParser/JSqlParser
    https://gitee.com/zzyijia/jsqlparser


    
    