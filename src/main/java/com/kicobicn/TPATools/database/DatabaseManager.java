package com.kicobicn.TPATools.database;

import com.kicobicn.TPATools.config.ModConfigs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DatabaseManager {
    private static final Logger LOGGER = LogManager.getLogger("TPAtools");

    /**
     * 服务器生命周期内共享的唯一连接。
     * 注意：任何调用方都不能关闭这个连接（不要用 try-with-resources 包住它），
     * 只允许关闭它创建的 Statement / ResultSet。
     */
    private static Connection connection;

    /** 实际生效的存储后端：mysql / json */
    private static String databaseType = "json";

    // MySQL 配置
    private static String host = "localhost";
    private static int port = 3306;
    private static String database = "tpatools";
    private static String username = "root";
    private static String password = "";

    // ==================== 初始化 ====================
    public static void initialize() {
        // 重复初始化（例如配置重载）时先释放旧连接，避免连接泄漏
        if (connection != null) {
            close();
        }
        databaseType = normalize(ModConfigs.DATABASE_TYPE.get());

        if (!isMysqlEnabled()) {
            connection = null;
            ModConfigs.DebugLog.info("Database backend: json");
            return;
        }

        host = ModConfigs.MYSQL_HOST.get();
        port = ModConfigs.MYSQL_PORT.get();
        database = ModConfigs.MYSQL_DATABASE.get();
        username = ModConfigs.MYSQL_USERNAME.get();
        password = ModConfigs.MYSQL_PASSWORD.get();

        try {
            // 用反射加载驱动，避免 JSON 模式下对 MySQL 驱动产生硬链接
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.error("MySQL driver not found on classpath, fallback to json backend: {}", e.getMessage());
            databaseType = "json";
            connection = null;
            return;
        }

        try {
            connection = openConnection();
            createTables();
            ModConfigs.DebugLog.info("MySQL connection established: {}:{}/{}", host, port, database);
        } catch (SQLException e) {
            LOGGER.error("Failed to connect to MySQL ({}:{}/{}): {}, fallback to json backend",
                    host, port, database, e.getMessage());
            databaseType = "json";
            connection = null;
        }
    }

    private static Connection openConnection() throws SQLException {
        String url = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=utf8",
                host, port, database);
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        return DriverManager.getConnection(url, props);
    }

    // ==================== 建表 ====================
    private static void createTables() throws SQLException {
        String[] ddl = new String[]{
                // 命令权限表
                """
                CREATE TABLE IF NOT EXISTS command_permissions (
                    command VARCHAR(50) PRIMARY KEY,
                    need_op BOOLEAN NOT NULL
                )
                """,
                // 免打扰状态表
                """
                CREATE TABLE IF NOT EXISTS toggle_states (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    enabled BOOLEAN NOT NULL
                )
                """,
                // 锁定玩家表
                """
                CREATE TABLE IF NOT EXISTS locked_players (
                    player_uuid VARCHAR(36) NOT NULL,
                    locked_uuid VARCHAR(36) NOT NULL,
                    PRIMARY KEY (player_uuid, locked_uuid)
                )
                """,
                // 玩家家园表
                """
                CREATE TABLE IF NOT EXISTS player_homes (
                    player_uuid VARCHAR(36) NOT NULL,
                    home_name VARCHAR(50) NOT NULL,
                    dimension VARCHAR(100) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    x_rot FLOAT NOT NULL,
                    y_rot FLOAT NOT NULL,
                    PRIMARY KEY (player_uuid, home_name)
                )
                """,
                // 家园分享表
                """
                CREATE TABLE IF NOT EXISTS home_shares (
                    player_uuid VARCHAR(36) NOT NULL,
                    home_name VARCHAR(50) NOT NULL,
                    shared_with VARCHAR(36) NOT NULL,
                    PRIMARY KEY (player_uuid, home_name, shared_with),
                    FOREIGN KEY (player_uuid, home_name) REFERENCES player_homes(player_uuid, home_name)
                )
                """,
                // 公开家园表
                """
                CREATE TABLE IF NOT EXISTS public_homes (
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(50) NOT NULL,
                    home_name VARCHAR(50) NOT NULL,
                    PRIMARY KEY (player_uuid, home_name),
                    FOREIGN KEY (player_uuid, home_name) REFERENCES player_homes(player_uuid, home_name)
                )
                """,
                // 死亡位置表
                """
                CREATE TABLE IF NOT EXISTS player_graves (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    dimension VARCHAR(100) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    x_rot FLOAT NOT NULL,
                    y_rot FLOAT NOT NULL
                )
                """,
                // 地标点表（此前缺失，导致 MySQL 模式下 /warp 读写全部失败）
                """
                CREATE TABLE IF NOT EXISTS warp_points (
                    name VARCHAR(50) PRIMARY KEY,
                    dimension VARCHAR(100) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    x_rot FLOAT NOT NULL,
                    y_rot FLOAT NOT NULL
                )
                """,
                // /back 返回点表
                """
                CREATE TABLE IF NOT EXISTS player_back (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    dimension VARCHAR(100) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    x_rot FLOAT NOT NULL,
                    y_rot FLOAT NOT NULL
                )
                """,
                // RTP 冷却表
                """
                CREATE TABLE IF NOT EXISTS rtp_cooldowns (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    last_used BIGINT NOT NULL
                )
                """
        };

        try (Statement stmt = connection.createStatement()) {
            for (String sql : ddl) {
                stmt.execute(sql);
            }
        }
    }

    // ==================== 访问接口 ====================
    /**
     * 获取共享连接。MySQL 未启用、驱动缺失、连接断开且重连失败时返回 null，
     * 调用方必须做 null 判断并回退到 JSON 实现。
     */
    public static Connection getConnection() {
        if (!isMysqlEnabled()) {
            return null;
        }
        try {
            if (connection == null || connection.isClosed()) {
                connection = openConnection();
                createTables();
                ModConfigs.DebugLog.info("MySQL connection (re)established");
            }
            return connection;
        } catch (SQLException e) {
            LOGGER.error("Failed to (re)connect MySQL: {}, fallback to json backend", e.getMessage());
            databaseType = "json";
            connection = null;
            return null;
        }
    }

    /** 当前是否真的在使用 MySQL（连接可用） */
    public static boolean isUsingMySQL() {
        if (!isMysqlEnabled() || connection == null) {
            return false;
        }
        try {
            return !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /** 配置里是否选择了 mysql（不代表连接一定可用） */
    public static boolean isMysqlEnabled() {
        return "mysql".equals(databaseType);
    }

    private static String normalize(String type) {
        if (type == null) {
            return "json";
        }
        String trimmed = type.trim().toLowerCase();
        return trimmed.isEmpty() ? "json" : trimmed;
    }

    public static void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
            ModConfigs.DebugLog.info("MySQL connection closed");
        } catch (SQLException e) {
            LOGGER.error("Failed to close MySQL connection: {}", e.getMessage());
        } finally {
            connection = null;
        }
    }
}
