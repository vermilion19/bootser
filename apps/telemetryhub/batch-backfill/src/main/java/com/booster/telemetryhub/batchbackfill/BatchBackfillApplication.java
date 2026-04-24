package com.booster.telemetryhub.batchbackfill;

import com.booster.storage.db.config.StorageDbConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ConfigurationPropertiesScan
@Import(StorageDbConfig.class)
public class BatchBackfillApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchBackfillApplication.class, args);
    }
}
