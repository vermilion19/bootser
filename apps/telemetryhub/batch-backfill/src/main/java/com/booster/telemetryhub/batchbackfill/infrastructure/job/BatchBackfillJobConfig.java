package com.booster.telemetryhub.batchbackfill.infrastructure.job;

import com.booster.telemetryhub.batchbackfill.application.io.BackfillTargetWriter;
import com.booster.telemetryhub.batchbackfill.application.plan.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.application.plan.BackfillPlanService;
import com.booster.telemetryhub.batchbackfill.infrastructure.source.RoutingBackfillSourceReader;
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

import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class BatchBackfillJobConfig {

    private static final Logger log = LoggerFactory.getLogger(BatchBackfillJobConfig.class);

    @Bean
    public Job telemetryhubBackfillJob(
            JobRepository jobRepository,
            Step prepareBackfillPlanStep,
            Step executeBackfillDryRunStep
    ) {
        return new JobBuilder("telemetryhubBackfillJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(prepareBackfillPlanStep)
                .next(executeBackfillDryRunStep)
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

    @Bean
    public Step executeBackfillDryRunStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            BackfillPlanService backfillPlanService,
            RoutingBackfillSourceReader backfillSourceReader,
            BackfillTargetWriter backfillTargetWriter
    ) {
        return new StepBuilder("executeBackfillDryRunStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    BackfillPlan plan = backfillPlanService.prepareDefaultPlan();
                    AtomicLong totalReadEvents = new AtomicLong();
                    AtomicLong totalWrites = new AtomicLong();

                    backfillSourceReader.readChunks(plan, events -> {
                        totalReadEvents.addAndGet(events.size());
                        long chunkWrites = plan.targets().stream()
                                .mapToLong(target -> backfillTargetWriter.write(target, events, plan))
                                .sum();
                        totalWrites.addAndGet(chunkWrites);
                    });
                    backfillPlanService.recordExecution(plan, totalReadEvents.get(), totalWrites.get());

                    log.info(
                            "Executed backfill flow: jobName={}, readEvents={}, totalWrites={}, dryRun={}",
                            plan.jobName(),
                            totalReadEvents.get(),
                            totalWrites.get(),
                            plan.dryRun()
                    );
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
