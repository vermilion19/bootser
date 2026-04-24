package com.booster.telemetryhub.batchbackfill.application;

import com.booster.telemetryhub.batchbackfill.domain.BackfillTarget;

import java.util.List;

public interface BackfillTargetWriter {

    long write(BackfillTarget target, List<BackfillRawEvent> events, BackfillPlan plan);
}
