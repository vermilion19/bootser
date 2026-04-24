package com.booster.telemetryhub.batchbackfill.infrastructure;

import com.booster.telemetryhub.batchbackfill.application.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.application.BackfillPlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchBackfillJobConfig {

    private static final Logger log = LoggerFactory.getLogger(BatchBackfillJobConfig.class);

    @Bean
    public Job telemetryhubBackfillJob(
            JobRepository jobRepository,
            Step prepareBackfillPlanStep
    ) {
        return new JobBuilder("telemetryhubBackfillJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(prepareBackfillPlanStep)
                .build();
    }

    @Bean
    public Step prepareBackfillPlanStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            BackfillPlanService backfillPlanService
    ) {
        return new StepBuilder("prepareBackfillPlanStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    BackfillPlan plan = backfillPlanService.prepareDefaultPlan();
                    log.info(
                            "Prepared backfill plan: jobName={}, sourceType={}, targets={}, from={}, to={}, dryRun={}, overwriteMode={}, chunkSize={}",
                            plan.jobName(),
                            plan.sourceType(),
                            plan.targets(),
                            plan.from(),
                            plan.to(),
                            plan.dryRun(),
                            plan.overwriteMode(),
                            plan.chunkSize()
                    );
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
