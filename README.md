LogMiner的监控方式，已经可以使用了。XStream的监控方式，能监测表，但是表内的变动却获取不到，目前该问题还未解决！

# 一、踩坑收藏

环境

1. 操作系统：centos7.9
2. oracle版本：oracle-database-ee-19c-1.0-1.x86_64.rpm
3. zookeeper版本：apache-zookeeper-3.7.0-bin.tar.gz
4. kafka版本：kafka_2.12-2.7.0.tgz

参考文章

1. [Debezium Connector for Oracle :: Debezium Documentation](https://debezium.io/documentation/reference/1.6/connectors/oracle.html)
2. [Apache Kafka](https://kafka.apache.org/documentation/)
3. [实时监视同步数据库变更，这个框架真是神器](https://mp.weixin.qq.com/s/_67XXbPAawegCP08W8FHIQ)
4. [Introduction to Debezium | Baeldung](https://www.baeldung.com/debezium-intro)
5. [debeziumEmbedded: 自己编写的使用 debezium 访问数据库](https://gitee.com/name_hanlin/debezium-embedded)
6. [docker安装oracle19c](https://blog.csdn.net/qq_26018075/article/details/107871687)
7. [oracle 12c的PDB数据库未打开](https://blog.csdn.net/wojiuguowei/article/details/79172701)
8. [oracle的补全日志--Supplemental Logging](https://blog.csdn.net/weixin_41561862/article/details/104495890)
9. [oracle 归档日志模式和非归档日志模式](https://www.cnblogs.com/javaeye235/p/4274344.html)
10. [Oracle数据库的非归档模式迁移到归档模式 ](https://www.cnblogs.com/gaojian/p/3611641.html)
11. [Oracle登录 ORA-01033: ORACLE正在初始化或关闭的解决方法](https://www.cnblogs.com/xnzhao/p/6114469.html)
12. [Debezium 从oracle抓取数据到kafka_](https://blog.csdn.net/weixin_40548182/article/details/117956341)
13. [Kafka Connect](https://kafka.apache.org/documentation/#connect_running)
14. [kafka connect简介以及部署](https://blog.csdn.net/u011687037/article/details/57411790)
15. [关键字: oracle lrm-00109: could not open parameter file '/opt/oracle - adodo1 ](https://www.cnblogs.com/adodo1/archive/2012/07/16/4328020.html)
16. [Kafka使用Debezium实时同步Oracle数据 | BlackC](https://jxeditor.github.io/2021/04/23/Kafka使用Debezium实时同步Oracle数据/)
17. [ORA-00942: 表或视图不存在解决方法](https://blog.csdn.net/paullinjie/article/details/81176477)
18. [oracle - Maven including ocijdbc19 in java.library.path - Stack Overflow](https://stackoverflow.com/questions/61733977/maven-including-ocijdbc19-in-java-library-path)
19. [JDBC驱动oci和thin区别](https://blog.csdn.net/lyc417356935/article/details/64438212)
20. [Error while fetching metadata with correlation id : {LEADER_NOT_AVAILABLE} 正确处理姿势](https://blog.csdn.net/luozhonghua2014/article/details/80369469)

# 二、监控Oracle

Debezium提供了两种监控数据库的方式，对应了oracle的两种连接方式。

* LogMiner：本质是jdbc thin driver，纯Java开发，与平台无关。
* XStream API：本质是jdbc oci driver，通过调用oci客户端c动态库实现。

> 引用官方描述
>
> The JDBC Thin driver is a pure Java, Type IV driver that can be used in applications and applets. It is platform-independent and does not require any additional Oracle software on the client-side. The JDBC Thin driver communicates with the server using SQL*Net to access Oracle Database.
>
> The JDBC Thin driver allows a direct connection to the database by providing an implementation of SQL*Net on top of Java sockets. The driver supports the TCP/IP protocol and requires a TNS listener on the TCP/IP sockets on the database server.
>
> The JDBC OCI driver is a Type II driver used with Java applications. It requires an Oracle client installation and, therefore, is Oracle platform-specific. It supports all installed Oracle Net adapters, including interprocess communication (IPC), named pipes, TCP/IP, and Internetwork Packet Exchange/Sequenced Packet Exchange (IPX/SPX).
>
> The JDBC OCI driver, written in a combination of Java and C, converts JDBC invocations to calls to OCI, using native methods to call C-entry points. These calls communicate with the database using SQL*Net.
>
> The JDBC OCI driver uses the OCI libraries, C-entry points, Oracle Net, core libraries, and other necessary files on the client computer where it is installed.

下面的步骤基于已经安装好oracle 19c，可以参考[Centos8安装Oracle19c](https://meethigher.top/blog/2021/oracle-install/)

## 2.1 LogMiner

切换到用户oracle

```
su - oracle
```

连接oracle，修改sys密码，这是为了跟debezium上的语句对应，可以拿来就用。

```
sqlplus / as sysdba
connect / as sysdba
alter user sys identified by top_secret;
exit;
```

数据库开启归档模式

```
sqlplus / as sysdba
connect sys/top_secret AS SYSDBA
alter system set db_recovery_file_dest_size = 10G;
alter system set db_recovery_file_dest = '/opt/oracle/oradata/recovery_area' scope=spfile;
shutdown immediate
startup mount
alter database archivelog;
alter database open;
-- Should now "Database log mode: Archive Mode"
archive log list

exit;
```

切换到root用户，创建`db_recovery_file_dest`文件夹，并赋予权限后，再切换回oracle用户

```
su root
mkdir /opt/oracle/oradata/recovery_area
chmod 777 /opt/oracle/oradata/recovery_area
su oracle
```

> 7表示r(读)、w(写)、x(执行)权限
>
> 777表示给文件拥有者、同组用户、其他组用户都分配rwx权限

在数据库级别启用最小补充日志记录，并且可以按如下方式配置。

```
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA;
```

如果只是想给某个表（比如stuinfo），开启最小日志记录，参考下面。

> 更改不成功并且表存在时，就先`select * from C##TEST.STUINFO`，如果提示没有表，就换个方式`select * from C##TEST."STUINFO"`

```
ALTER TABLE C##TEST.STUINFO ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
```

创建用户并分配权限

```
sqlplus sys/top_secret@//localhost:1521/ORCLCDB as sysdba
  CREATE TABLESPACE logminer_tbs DATAFILE '/opt/oracle/oradata/ORCLCDB/logminer_tbs.dbf'
    SIZE 25M REUSE AUTOEXTEND ON MAXSIZE UNLIMITED;
  exit;

sqlplus sys/top_secret@//localhost:1521/ORCLPDB1 as sysdba
  CREATE TABLESPACE logminer_tbs DATAFILE '/opt/oracle/oradata/ORCLCDB/ORCLPDB1/logminer_tbs.dbf'
    SIZE 25M REUSE AUTOEXTEND ON MAXSIZE UNLIMITED;
  exit;

sqlplus sys/top_secret@//localhost:1521/ORCLCDB as sysdba

  CREATE USER c##dbzuser IDENTIFIED BY dbz
    DEFAULT TABLESPACE logminer_tbs
    QUOTA UNLIMITED ON logminer_tbs
    CONTAINER=ALL;

  GRANT CREATE SESSION TO c##dbzuser CONTAINER=ALL;
  GRANT SET CONTAINER TO c##dbzuser CONTAINER=ALL;
  GRANT SELECT ON V_$DATABASE to c##dbzuser CONTAINER=ALL;
  GRANT FLASHBACK ANY TABLE TO c##dbzuser CONTAINER=ALL;
  GRANT SELECT ANY TABLE TO c##dbzuser CONTAINER=ALL;
  GRANT SELECT_CATALOG_ROLE TO c##dbzuser CONTAINER=ALL;
  GRANT EXECUTE_CATALOG_ROLE TO c##dbzuser CONTAINER=ALL;
  GRANT SELECT ANY TRANSACTION TO c##dbzuser CONTAINER=ALL;
  GRANT LOGMINING TO c##dbzuser CONTAINER=ALL;

  GRANT CREATE TABLE TO c##dbzuser CONTAINER=ALL;
  GRANT LOCK ANY TABLE TO c##dbzuser CONTAINER=ALL;
  GRANT ALTER ANY TABLE TO c##dbzuser CONTAINER=ALL;
  GRANT CREATE SEQUENCE TO c##dbzuser CONTAINER=ALL;

  GRANT EXECUTE ON DBMS_LOGMNR TO c##dbzuser CONTAINER=ALL;
  GRANT EXECUTE ON DBMS_LOGMNR_D TO c##dbzuser CONTAINER=ALL;

  GRANT SELECT ON V_$LOG TO c##dbzuser CONTAINER=ALL;
  GRANT SELECT ON V_$LOG_HISTORY TO c##dbzuser CONTAINER=ALL;
  GRANT SELECT ON V_$LOGMNR_LOGS TO c##dbzuser CONTAINER=ALL;
  GRANT SELECT ON V_$LOGMNR_CONTENTS TO c##dbzuser CONTAINER=ALL;
  GRANT SELECT ON V_$LOGMNR_PARAMETERS TO c##dbzuser CONTAINER=ALL;
  GRANT SELECT ON V_$LOGFILE TO c##dbzuser CONTAINER=ALL;
  GRANT SELECT ON V_$ARCHIVED_LOG TO c##dbzuser CONTAINER=ALL;
  GRANT SELECT ON V_$ARCHIVE_DEST_STATUS TO c##dbzuser CONTAINER=ALL;

  exit;
```

创建表，并开启最小日志

```
sqlplus / as sysdba
conn c##dbzuser/dbz;
CREATE TABLE STU ( "s_id" INT PRIMARY KEY, "s_name" VARCHAR ( 255 ) );
ALTER TABLE C##DBZUSER.STU ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
exit;
```

经过上面的步骤，接下来，就可以通过java api或者kafka-connector方式来监控数据库。相对来说，直接通过java api会方便许多。

### java API

创建SpringBoot项目

pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.3.1.RELEASE</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>com.example</groupId>
    <artifactId>demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>demo</name>
    <description>Demo project for Spring Boot</description>
    <properties>
        <java.version>1.8</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>io.debezium</groupId>
            <artifactId>debezium-api</artifactId>
            <version>1.6.2.Final</version>
        </dependency>
        <dependency>
            <groupId>io.debezium</groupId>
            <artifactId>debezium-embedded</artifactId>
            <version>1.6.2.Final</version>
        </dependency>

        <dependency>
            <groupId>io.debezium</groupId>
            <artifactId>debezium-connector-mysql</artifactId>
            <version>1.6.2.Final</version>
        </dependency>

        <dependency>
            <groupId>io.debezium</groupId>
            <artifactId>debezium-connector-oracle</artifactId>
            <version>1.6.2.Final</version>
        </dependency>

        <dependency>
            <groupId>com.oracle.ojdbc</groupId>
            <artifactId>ojdbc8</artifactId>
            <version>19.3.0.0</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>

```

resources下面创建logback.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration debug="true">

    <appender name="stdout" class="ch.qos.logback.core.ConsoleAppender">
        <Target>System.out</Target>
        <encoder>
            <pattern>%-5p [%d][%mdc{mdc_userId}] %C:%L - %m %n</pattern>
            <charset>utf-8</charset>
        </encoder>
        <!-- 此日志appender是为开发使用，只配置最底级别，控制台输出的日志级别是大于或等于此级别的日志信息 -->
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>INFO</level>
        </filter>
    </appender>
    <root level="info">
        <!-- 生产环境将请stdout去掉 -->
        <appender-ref ref="stdout"/>
    </root>
</configuration>
```

创建OracleDebezium_19c类

```java
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import io.debezium.relational.history.FileDatabaseHistory;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OracleDebezium_19c {

    public static void main(String[] args) {

        // 1. 生成配置
        Properties props = genProps();

        // 2. 业务处理逻辑部分代码
        DebeziumEngine<ChangeEvent<String, String>> engine = engineBuild(props);

        // 3. 正式运行
        runSoftware(engine);

    }

    // 生成连接 Oracle 的相关配置
    private static Properties genProps() {
        // 配置
        Properties props = new Properties();

        props.setProperty("name", "oracle-engine-0033");
        props.setProperty("connector.class", "io.debezium.connector.oracle.OracleConnector");
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
        // 指定 offset 存储目录
        props.setProperty("offset.storage.file.filename", "D:\\temp\\oracle4.txt");
        // 指定 Topic offset 写入磁盘的间隔时间
        props.setProperty("offset.flush.interval.ms", "6000");
        //设置数据库连接信息
        props.setProperty("database.hostname", "192.168.10.132");
        props.setProperty("database.port", "1521");
        props.setProperty("database.user", "C##DBZUSER");
        props.setProperty("database.password", "dbz");
        props.setProperty("database.server.id", "85701");
        props.setProperty("table.include.list", "C##DBZUSER.STU");
        props.setProperty("database.history", FileDatabaseHistory.class.getCanonicalName());
        props.setProperty("database.history.file.filename", "D:\\temp\\oracle4.txt");
        //每次运行需要对此参数进行修改，因为此参数唯一
        props.setProperty("database.server.name", "my-oracle-connector-0023");
        //指定 CDB 模式的实例名
        props.setProperty("database.dbname", "ORCLCDB");
        //是否输出 schema 信息
        props.setProperty("key.converter.schemas.enable", "false");
        props.setProperty("value.converter.schemas.enable", "false");
        props.setProperty("database.serverTimezone", "UTC"); // 时区
        props.setProperty("database.connection.adapter", "logminer"); // 模式
        // Kafka 连接相关配置
        /*props.setProperty("database.history.kafka.bootstrap.servers", "192.168.131.130:9092");
        props.setProperty("database.history.kafka.topic", "oracle.history");*/

        return props;
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
                DebeziumEngine
                        .create(Json.class)
                        .using(props)
                        .notifying(record -> {
                            // record中会有操作的类型（增、删、改）和具体的数据
                            System.out.println("record.key() = " + record.key());
                            System.out.println("record.value() = " + record.value());
                        })
                        .using((success, message, error) -> {
                            // 强烈建议加上此部分的回调代码，方便查看错误信息
                            if (!success && error != null) {
                                // 报错回调
                                System.out.println("----------error------");
                                System.out.println(message);
                                //System.out.println(error);
                                error.printStackTrace();
                            }
                        })
                        .build();

        return engine;
    }

}
```

启动项目，执行完step六步之后，如果没有报错，说明启动成功。

![](https://meethigher.top/blog/2021/debezium-oracle/1.png)

进入数据库，对进行监控的表进行添加一条数据。

![](https://meethigher.top/blog/2021/debezium-oracle/2.png)

会出现下面的日志。说明监控成功。

![](https://meethigher.top/blog/2021/debezium-oracle/3.png)

### kafka-connector

使用到的java、zookeeper、kafka解压到/opt/module下，java需要配置环境变量

![](https://meethigher.top/blog/2021/debezium-oracle/5.png)

去[Central Repository: io/debezium/debezium-connector-oracle](https://repo1.maven.org/maven2/io/debezium/debezium-connector-oracle/)下载你需要的版本的plugin，如`debezium-connector-oracle-1.6.2.Final-plugin.tar.gz`

创建文件夹，存放kafka-connector-plugin

```
mkdir /opt/kafka-plugin
```

解压下载的plugin，将里面的内容全部拷贝，复制到kafka-plugin一份、kafka的libs一份，如下图

![](https://meethigher.top/blog/2021/debezium-oracle/4.png)

去[Oracle Instant Client Downloads](https://www.oracle.com/database/technologies/instant-client/downloads.html)下载`对应操作系统`的Basic Package (ZIP)。

将其解压，提取其中的`ojdbc8.jar`到`kafka的libs`中。

配置kafka-connector

```
cd /opt/module/kafka_2.12-2.7.0/
vi config/connect-distributed.properties 
```

添加`plugin.path`为刚才配置好的`kafka-plugin`，保存。

```
plugin.path=/opt/kafka-plugin
```

如此，就配置好了。

进入zookeeper路径，复制一份zookeeper配置文件出来，启动zookeeper

```
cp conf/zoo_sample.cfg conf/zoo.conf
bin/
bin/zkServer.sh start
```

进入kafka路径，先启动kafka，启动成功后，再去启动kafka-connect

```
bin/kafka-server-start.sh config/server.properties
bin/connect-distributed.sh config/connect-distributed.properties
```

打开浏览器/postman，get访问8083端口，会出现版本信息

![](https://meethigher.top/blog/2021/debezium-oracle/6.png)

通过post访问，ip:8083/connectors，并且携带配置json，可以注册connector

```json
{
	"name": "stu2",
	"config": {
		"connector.class": "io.debezium.connector.oracle.OracleConnector",
		"tasks.max": "1",
		"database.server.name": "server2",
		"database.hostname": "192.168.10.132",
		"database.port": "1521",
		"database.user": "c##dbzuser",
		"database.password": "dbz",
		"database.dbname": "ORCLCDB",
		"table.include.list": "C##DBZUSER.STU2",
		"database.history.kafka.bootstrap.servers": "192.168.10.132:9092",
		"database.history.kafka.topic": "schema-changes.stu2"
	}
}
```

kafka-connector会自动生成kafka-topic，一般是`server.库名.表名`，不过像`#`符，一般给转成了`_`符，像`server2.C##DBZUSER.STU2`就转成了`server2.C__DBZUSER.STU2`，可以通过注册connector仔细观察日志发现。

![](https://meethigher.top/blog/2021/debezium-oracle/7.png)

进入kafka路径，查看kafka所有的topic

```
bin/kafka-topics.sh --list --zookeeper 192.168.10.132:2181
```

监控当前topic，是否监控到数据库变化

```
bin/kafka-console-consumer.sh --bootstrap-server 192.168.10.132:9092 --topic server2.C__DBZUSER.STU2
```

![](https://meethigher.top/blog/2021/debezium-oracle/8.png)

监控到如上图这样的数据，说明监控成功！

## ~~2.2 XStream API~~

切换到用户oracle

```
su - oracle
```

连接oracle，修改sys密码，这是为了跟debezium上的语句对应，可以拿来就用。

```
sqlplus / as sysdba
connect / as sysdba
alter user sys identified by top_secret;
exit;
```

开启归档模式

```
CONNECT sys/top_secret AS SYSDBA
alter system set db_recovery_file_dest_size = 5G;
alter system set db_recovery_file_dest = '/opt/oracle/oradata/recovery_area' scope=spfile;
alter system set enable_goldengate_replication=true;
shutdown immediate
startup mount
alter database archivelog;
alter database open;
-- Should show "Database log mode: Archive Mode"
archive log list

exit;
```

切换到root用户，创建`db_recovery_file_dest`文件夹，并赋予权限后，再切换回oracle用户

```
su root
mkdir /opt/oracle/oradata/recovery_area
chmod 777 /opt/oracle/oradata/recovery_area
su oracle
```

> 7表示r(读)、w(写)、x(执行)权限
>
> 777表示给文件拥有者、同组用户、其他组用户都分配rwx权限

在数据库级别启用最小补充日志记录，并且可以按如下方式配置。

```
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA;
```

如果只是想给某个表（比如stuinfo），开启最小日志记录，参考下面。

> 更改不成功并且表存在时，就先`select * from C##TEST.STUINFO`，如果提示没有表，就换个方式`select * from C##TEST."STUINFO"`

```
ALTER TABLE C##TEST.STUINFO ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
```

配置XStream admin用户

```
sqlplus sys/top_secret@//localhost:1521/ORCLCDB as sysdba
  CREATE TABLESPACE xstream_adm_tbs DATAFILE '/opt/oracle/oradata/ORCLCDB/xstream_adm_tbs.dbf'
    SIZE 25M REUSE AUTOEXTEND ON MAXSIZE UNLIMITED;
  exit;

sqlplus sys/top_secret@//localhost:1521/ORCLPDB1 as sysdba
  CREATE TABLESPACE xstream_adm_tbs DATAFILE '/opt/oracle/oradata/ORCLCDB/ORCLPDB1/xstream_adm_tbs.dbf'
    SIZE 25M REUSE AUTOEXTEND ON MAXSIZE UNLIMITED;
  exit;

sqlplus sys/top_secret@//localhost:1521/ORCLCDB as sysdba
  CREATE USER c##dbzadmin IDENTIFIED BY dbz
    DEFAULT TABLESPACE xstream_adm_tbs
    QUOTA UNLIMITED ON xstream_adm_tbs
    CONTAINER=ALL;

  GRANT CREATE SESSION, SET CONTAINER TO c##dbzadmin CONTAINER=ALL;

  BEGIN
     DBMS_XSTREAM_AUTH.GRANT_ADMIN_PRIVILEGE(
        grantee                 => 'c##dbzadmin',
        privilege_type          => 'CAPTURE',
        grant_select_privileges => TRUE,
        container               => 'ALL'
     );
  END;
  /

  exit;
```

创建XStream用户

```
sqlplus sys/top_secret@//localhost:1521/ORCLCDB as sysdba
  CREATE TABLESPACE xstream_tbs DATAFILE '/opt/oracle/oradata/ORCLCDB/xstream_tbs.dbf'
    SIZE 25M REUSE AUTOEXTEND ON MAXSIZE UNLIMITED;
  exit;

sqlplus sys/top_secret@//localhost:1521/ORCLPDB1 as sysdba
  CREATE TABLESPACE xstream_tbs DATAFILE '/opt/oracle/oradata/ORCLCDB/ORCLPDB1/xstream_tbs.dbf'
    SIZE 25M REUSE AUTOEXTEND ON MAXSIZE UNLIMITED;
  exit;

sqlplus sys/top_secret@//localhost:1521/ORCLCDB as sysdba
  CREATE USER c##dbzuser IDENTIFIED BY dbz
    DEFAULT TABLESPACE xstream_tbs
    QUOTA UNLIMITED ON xstream_tbs
    CONTAINER=ALL;

  GRANT CREATE SESSION TO c##dbzuser CONTAINER=ALL;
  GRANT SET CONTAINER TO c##dbzuser CONTAINER=ALL;
  GRANT SELECT ON V_$DATABASE to c##dbzuser CONTAINER=ALL;
  GRANT FLASHBACK ANY TABLE TO c##dbzuser CONTAINER=ALL;
  GRANT SELECT_CATALOG_ROLE TO c##dbzuser CONTAINER=ALL;
  GRANT EXECUTE_CATALOG_ROLE TO c##dbzuser CONTAINER=ALL;
  exit;
```

创建XStream出站服务器

```
sqlplus c##dbzadmin/dbz@//localhost:1521/ORCLCDB
DECLARE
  tables  DBMS_UTILITY.UNCL_ARRAY;
  schemas DBMS_UTILITY.UNCL_ARRAY;
BEGIN
    tables(1)  := NULL;
    schemas(1) := 'debezium';
  DBMS_XSTREAM_ADM.CREATE_OUTBOUND(
    server_name     =>  'dbzxout',
    table_names     =>  tables,
    schema_names    =>  schemas);
END;
/
exit;
```

配置 XStream 用户帐户以连接到 XStream 出站服务器

```
sqlplus sys/top_secret@//localhost:1521/ORCLCDB as sysdba
BEGIN
  DBMS_XSTREAM_ADM.ALTER_OUTBOUND(
    server_name  => 'dbzxout',
    connect_user => 'c##dbzuser');
END;
/
exit;
```

创建表，并开启最小日志

```
sqlplus / as sysdba
conn c##dbzuser/dbz;
CREATE TABLE STU ( "s_id" INT PRIMARY KEY, "s_name" VARCHAR ( 255 ) );
ALTER TABLE C##DBZUSER.STU ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
exit;
```

经过上面的步骤，接下来，就可以通过java api或者kafka-connector方式来监控数据库。相对来说，直接通过java api会方便许多。

### ~~java API~~

~~待完成~~

### ~~kafka-connector~~

**玄学文件，监控不到数据，也没有报错**

使用到的java、zookeeper、kafka解压到/opt/module下，java需要配置环境变量

![](https://meethigher.top/blog/2021/debezium-oracle/5.png)

去[Central Repository: io/debezium/debezium-connector-oracle](https://repo1.maven.org/maven2/io/debezium/debezium-connector-oracle/)下载你需要的版本的plugin，如`debezium-connector-oracle-1.6.2.Final-plugin.tar.gz`

创建文件夹，存放kafka-connector-plugin

```
mkdir /opt/kafka-plugin
```

解压下载的plugin，将里面的内容全部拷贝，复制到kafka-plugin一份、kafka的libs一份，如下图

![](https://meethigher.top/blog/2021/debezium-oracle/4.png)

去[Oracle Instant Client Downloads](https://www.oracle.com/database/technologies/instant-client/downloads.html)下载`对应操作系统`的Basic Package (ZIP)。

将其解压，提取其中的`ojdbc8.jar`和`xstream.jar`到`kafka的libs`中。

将解压后的instantClient配置成环境变量

```
vim /etc/profile.d/
```

导出LD_LIBRARY_PATH，并保存

```
export LD_LIBRARY_PATH=/opt/instantclient_19_12
```

刷新环境变量

```
source /etc/profile
```

配置kafka-connector

```
cd /opt/module/kafka_2.12-2.7.0/
vi config/connect-distributed.properties 
```

添加`plugin.path`为刚才配置好的`kafka-plugin`，保存。

```
plugin.path=/opt/kafka-plugin
```


如此，就配置好了。

进入zookeeper路径，复制一份zookeeper配置文件出来，启动zookeeper

```
cp conf/zoo_sample.cfg conf/zoo.conf
bin/
bin/zkServer.sh start
```

进入kafka路径，先启动kafka，启动成功后，再去启动kafka-connect

```
bin/kafka-server-start.sh config/server.properties
bin/connect-distributed.sh config/connect-distributed.properties
```

打开浏览器/postman，get访问8083端口，会出现版本信息

![](https://meethigher.top/blog/2021/debezium-oracle/6.png)

通过post访问，ip:8083/connectors，并且携带配置json，可以注册connector

```json
{
    "name": "stu2",
    "config": {
        "connector.class" : "io.debezium.connector.oracle.OracleConnector",
        "tasks.max" : "1",
        "database.server.name" : "server6",
        "database.hostname" : "192.168.10.131",
        "database.port" : "1521",
        "database.user" : "c##dbzuser",
        "database.password" : "dbz",
        "database.dbname" : "ORCLCDB",
        "table.include.list" : "C##DBZUSER.STU",
        "database.history.kafka.bootstrap.servers" : "192.168.10.131:9092",
        "database.history.kafka.topic": "schema-changes.stu2",
        "database.connection.adapter": "xstream",
        "database.out.server.name" : "dbzxout"
    }
}
```

kafka-connector会自动生成kafka-topic，一般是`server.库名.表名`，不过像`#`符，一般给转成了`_`符，像`server2.C##DBZUSER.STU2`就转成了`server2.C__DBZUSER.STU2`，可以通过注册connector仔细观察日志发现。

![](https://meethigher.top/blog/2021/debezium-oracle/7.png)

进入kafka路径，查看kafka所有的topic

```
bin/kafka-topics.sh --list --zookeeper 192.168.10.132:2181
```

监控当前topic，是否监控到数据库变化

```
bin/kafka-console-consumer.sh --bootstrap-server 192.168.10.132:9092 --topic server2.C__DBZUSER.STU2
```
