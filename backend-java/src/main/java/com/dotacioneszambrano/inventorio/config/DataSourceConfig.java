package com.dotacioneszambrano.inventorio.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Builds the MySQL DataSource from MYSQL_URL or the external secrets file.
 *
 * Resolution order: MYSQL_URL, then spring.datasource.url.
 */
@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    public DataSource dataSource(Environment env) {
        String mysqlUrl = env.getProperty("MYSQL_URL");
        String url;
        String user;
        String password;

        if (mysqlUrl != null && !mysqlUrl.isBlank()) {
            try {
                URI uri = new URI(mysqlUrl);
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 3306;
                String database = uri.getPath() != null && uri.getPath().startsWith("/")
                        ? uri.getPath().substring(1)
                        : uri.getPath();
                String userInfo = uri.getUserInfo();
                if (userInfo == null) {
                    throw new IllegalStateException("MYSQL_URL no contiene credenciales");
                }
                String[] up = userInfo.split(":", 2);
                user = up[0];
                password = up.length > 1 ? up[1] : "";
                // Require TLS while retaining compatibility with providers without a local CA bundle.
                url = "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=true&requireSSL=true&verifyServerCertificate=false"
                        + "&connectTimeout=5000&serverTimezone=UTC";
            } catch (URISyntaxException e) {
                throw new IllegalStateException("MYSQL_URL invalida: " + e.getMessage(), e);
            }
        } else {
            url = env.getProperty("spring.datasource.url");
            user = env.getProperty("spring.datasource.username");
            password = env.getProperty("spring.datasource.password");
            if (url == null || url.isBlank()) {
                log.warn("No hay MYSQL_URL ni spring.datasource.url configurados. " +
                        "El backend arrancara pero fallara al conectar a la base de datos.");
                // DataSource inservible pero valido para permitir el arranque en dev.
                HikariDataSource ds = new HikariDataSource();
                ds.setJdbcUrl("jdbc:mysql://localhost:3306/none");
                ds.setUsername("none");
                ds.setPassword("none");
                ds.setMaximumPoolSize(1);
                ds.setInitializationFailTimeout(-1);
                return ds;
            }
        }

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setMaximumPoolSize(5);
        ds.setConnectionTimeout(5000);
        ds.setConnectionInitSql("SET time_zone = '-05:00'");
        ds.setPoolName("InventorioPool");
        return ds;
    }
}
