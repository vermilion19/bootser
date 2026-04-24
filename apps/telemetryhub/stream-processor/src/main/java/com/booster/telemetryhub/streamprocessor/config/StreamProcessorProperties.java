package com.booster.telemetryhub.streamprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "telemetryhub.stream")
public class StreamProcessorProperties {

    private String applicationId = "telemetryhub-stream-processor";
    private String sourceTopic = "telemetryhub.raw-events";
    private Duration deviceLastSeenWindow = Duration.ofMinutes(5);
    private Duration eventsPerMinuteWindow = Duration.ofMinutes(1);
    private Duration drivingEventCounterWindow = Duration.ofMinutes(1);
    private Duration regionHeatmapWindow = Duration.ofMinutes(1);
    private Duration lateEventGrace = Duration.ofMinutes(2);
    private double heatmapGridSize = 0.01d;
    private int numStreamThreads = 1;
    private String processingGuarantee = "at_least_once";
    private String stateDir = System.getProperty("java.io.tmpdir") + "/telemetryhub-streams";
    private int numStandbyReplicas = 0;

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getSourceTopic() {
        return sourceTopic;
    }

    public void setSourceTopic(String sourceTopic) {
        this.sourceTopic = sourceTopic;
    }

    public Duration getDeviceLastSeenWindow() {
        return deviceLastSeenWindow;
    }

    public void setDeviceLastSeenWindow(Duration deviceLastSeenWindow) {
        this.deviceLastSeenWindow = deviceLastSeenWindow;
    }

    public Duration getEventsPerMinuteWindow() {
        return eventsPerMinuteWindow;
    }

    public void setEventsPerMinuteWindow(Duration eventsPerMinuteWindow) {
        this.eventsPerMinuteWindow = eventsPerMinuteWindow;
    }

    public Duration getDrivingEventCounterWindow() {
        return drivingEventCounterWindow;
    }

    public void setDrivingEventCounterWindow(Duration drivingEventCounterWindow) {
        this.drivingEventCounterWindow = drivingEventCounterWindow;
    }

    public Duration getRegionHeatmapWindow() {
        return regionHeatmapWindow;
    }

    public void setRegionHeatmapWindow(Duration regionHeatmapWindow) {
        this.regionHeatmapWindow = regionHeatmapWindow;
    }

    public Duration getLateEventGrace() {
        return lateEventGrace;
    }

    public void setLateEventGrace(Duration lateEventGrace) {
        this.lateEventGrace = lateEventGrace;
    }

    public double getHeatmapGridSize() {
        return heatmapGridSize;
    }

    public void setHeatmapGridSize(double heatmapGridSize) {
        this.heatmapGridSize = heatmapGridSize;
    }

    public int getNumStreamThreads() {
        return numStreamThreads;
    }

    public void setNumStreamThreads(int numStreamThreads) {
        this.numStreamThreads = numStreamThreads;
    }

    public String getProcessingGuarantee() {
        return processingGuarantee;
    }

    public void setProcessingGuarantee(String processingGuarantee) {
        this.processingGuarantee = processingGuarantee;
    }

    public String getStateDir() {
        return stateDir;
    }

    public void setStateDir(String stateDir) {
        this.stateDir = stateDir;
    }

    public int getNumStandbyReplicas() {
        return numStandbyReplicas;
    }

    public void setNumStandbyReplicas(int numStandbyReplicas) {
        this.numStandbyReplicas = numStandbyReplicas;
    }
}
