package com.example.demo;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import io.debezium.relational.history.FileDatabaseHistory;
import org.apache.kafka.connect.storage.FileOffsetBackingStore;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author 韩林
 * @version 1.0.0
 * @ClassName Test01.java
 * @Description TODO ：
 * @createTime 2021年07月09日 15:31:00
 */
public class MySQLDebezium {

    public static void main(String[] args) {


        // 1. 生成配置
        Properties props = getProps();

        // 2. 业务处理逻辑部分代码
        DebeziumEngine<ChangeEvent<String, String>> engine = engineBuild(props);

        // 3. 正式运行
        runSoftware(engine);
    }


    // 开始运行程序
    public static void runSoftware(DebeziumEngine<ChangeEvent<String, String>> engine) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(engine);

    }

    // 实现逻辑
    public static DebeziumEngine<ChangeEvent<String, String>> engineBuild(Properties props) {
        // 2. 构建 DebeziumEngine
        // 使用 Json 格式
        DebeziumEngine<ChangeEvent<String, String>> engine =
                DebeziumEngine.create(Json.class)
                        .using(props)
                        .notifying(record -> {
                            // record中会有操作的类型（增、删、改）和具体的数据
                            // key是主键
//                            System.out.println("record = " + record);
                            System.out.println("record.key() = " + record.key());
                            System.out.println("record.value() = " + record.value());

                        })
                        .using((success, message, error) -> {
                            // 强烈建议加上此部分的回调代码，方便查看错误信息
                            if (!success && error != null) {
                                // 报错回调
                                System.out.println("----------error------");
                                System.out.println(message);
                                System.out.println(error);
                                System.out.println(success);

                            }
                        }).build();
        return engine;
    }

    private static Properties getProps() {
        // 配置
        Properties props = new Properties();
/*         在maven处引入其他数据库的连接器，例如debezium-connector-postgres，
         再修改此处的connector.class，即可使用其他数据库的CDC
*/
//        props.setProperty("connector.class", MySqlConnector.class.getCanonicalName());
        props.setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector");
        props.setProperty("database.server.name", "my_server_1"); // 可以任意修改
        props.setProperty("database.hostname", "192.168.110.130"); // IP
        props.setProperty("database.port", String.valueOf(3306)); // 端口
        props.setProperty("database.user", "root"); // 用户
        props.setProperty("database.password", "debezium"); // 密码
        props.setProperty("database.serverTimezone", "UTC"); // 时区
        // 下面两个是数据库和表，注意只能选择一种:
        // 1. 使用database.whitelist，只设置数据库（会通知全库的CDC信息）
        // 2. 使用table.whitelist，设置库名和表名（会通知单个库的单个表的CDC信息）
        props.setProperty("database.dbname","inventory");
        props.setProperty("database.whitelist", "inventory");    // 指定库名称
//        props.setProperty("table.whitelist", "db_inventory_cdc.tb_products_cdc"); // 库.表名

        props.setProperty("name", "engine");
        props.setProperty("offset.storage", FileOffsetBackingStore.class.getCanonicalName());
        props.setProperty("offset.storage.file.filename", "D:\\temp\\bbbbb.txt");
//        props.setProperty("offset.storage.file.filename", "F:\\temp\\temp\\offset.txt");
        props.setProperty("offset.flush.interval.ms", String.valueOf(6000L));
        // 是否输出 schema 信息
        props.setProperty("key.converter.schemas.enable", "false");
        props.setProperty("value.converter.schemas.enable", "false");
        // 指定连接器是否应将数据库模式中的更改发布到与数据库服务器ID同名的Kafka主题
        props.setProperty("include.schema.changes", "false");
//        用于指定连接器是否应包括生成更改事件的原始SQL查询。注意：此选项要求配置MySQL并将binlog_rows_query_log_events选项设置为ON。
        props.setProperty("include.query", "true");
        /*
        二进制日志阅读器使用的前瞻缓冲区的大小。默认设置为0禁用缓冲。
在特定情况下，MySQL binlog 中可能包含一条ROLLBACK语句完成的未提交数据。典型的例子是在单个事务中使用保存点或混合临时和常规表更改。
当检测到交易开始时，Debezium 尝试前滚 binlog 位置并找到COMMIT或ROLLBACK因此它可以确定是否从事务中流式传输更改。binlog 缓冲区的大小定义了 Debezium 在搜索事务边界时可以缓冲的事务中的最大更改数。如果事务的大小大于缓冲区，则 Debezium 必须倒带并重新读取流式传输时未放入缓冲区的事件。
注意：此功能正在孵化中。鼓励反馈。预计这个功能还没有完全完善。
*/
        props.setProperty("binlog.buffer.size", "10000");

        props.setProperty("tombstones.on.delete", "false");
        props.setProperty("database.history", FileDatabaseHistory.class.getCanonicalName());
        props.setProperty("database.history.store.only.monitored.tables.ddl", "true");
        props.setProperty("database.history.file.filename", "D:\\temp\\bbbbb.txt");
        props.setProperty("database.history.instance.name", UUID.randomUUID().toString());
        props.setProperty("database.history.skip.unparseable.ddl", "true");
        return props;
    }
}