package com.pocsigmet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@ComponentScan(
    basePackages = "com.pocsigmet",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.pocsigmet\\.(PocSigmetApplication|grib2\\.TestWindBarbRunner|test\\.CleanupTestRunner|TestAerodromoDatabase)"
    )
)
@EnableScheduling
public class SpringEntryPoint {
    public static void main(String[] args) {
        SpringApplication.run(SpringEntryPoint.class, args);
    }
}
