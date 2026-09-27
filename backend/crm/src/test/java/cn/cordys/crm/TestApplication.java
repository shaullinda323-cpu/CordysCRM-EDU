package cn.cordys.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {
        QuartzAutoConfiguration.class,
        LdapAutoConfiguration.class,
        Neo4jAutoConfiguration.class
})
@ServletComponentScan
@ComponentScan(
        basePackages = {"cn.cordys"}
)
public class TestApplication {

    static {
        // Tests must not require write access to the production /opt directory.
        if (System.getProperty("cordys.files.root") == null) {
            System.setProperty("cordys.files.root", java.nio.file.Path.of("target", "test-files").toAbsolutePath().toString());
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
