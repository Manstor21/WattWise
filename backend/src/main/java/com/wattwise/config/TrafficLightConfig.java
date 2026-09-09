package com.wattwise.config;

import com.wattwise.util.TrafficLightClassifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Vincula {@code wattwise.traffic-light.*} de application.yml a la configuración del
 * clasificador y expone un bean singleton {@link TrafficLightClassifier}.
 */
@Configuration
@ConfigurationProperties(prefix = "wattwise.traffic-light")
public class TrafficLightConfig {

    private double percentileGreenMax = 0.33;
    private double percentileRedMin = 0.67;
    private double deviationGreenMax = -0.25;
    private double deviationRedMin = 0.25;
    private double absoluteGreenMax = 0.10;
    private double absoluteRedMin = 0.20;
    private double uniformDayCvThreshold = 0.10;
    private boolean smoothingEnabled = true;

    @Bean
    public TrafficLightClassifier trafficLightClassifier() {
        TrafficLightClassifier.Config config = new TrafficLightClassifier.Config()
                .percentileGreenMax(percentileGreenMax)
                .percentileRedMin(percentileRedMin)
                .deviationGreenMax(deviationGreenMax)
                .deviationRedMin(deviationRedMin)
                .absoluteGreenMax(absoluteGreenMax)
                .absoluteRedMin(absoluteRedMin)
                .uniformDayCvThreshold(uniformDayCvThreshold)
                .smoothingEnabled(smoothingEnabled);
        return new TrafficLightClassifier(config);
    }

    public double getPercentileGreenMax() {
        return percentileGreenMax;
    }

    public void setPercentileGreenMax(double percentileGreenMax) {
        this.percentileGreenMax = percentileGreenMax;
    }

    public double getPercentileRedMin() {
        return percentileRedMin;
    }

    public void setPercentileRedMin(double percentileRedMin) {
        this.percentileRedMin = percentileRedMin;
    }

    public double getDeviationGreenMax() {
        return deviationGreenMax;
    }

    public void setDeviationGreenMax(double deviationGreenMax) {
        this.deviationGreenMax = deviationGreenMax;
    }

    public double getDeviationRedMin() {
        return deviationRedMin;
    }

    public void setDeviationRedMin(double deviationRedMin) {
        this.deviationRedMin = deviationRedMin;
    }

    public double getAbsoluteGreenMax() {
        return absoluteGreenMax;
    }

    public void setAbsoluteGreenMax(double absoluteGreenMax) {
        this.absoluteGreenMax = absoluteGreenMax;
    }

    public double getAbsoluteRedMin() {
        return absoluteRedMin;
    }

    public void setAbsoluteRedMin(double absoluteRedMin) {
        this.absoluteRedMin = absoluteRedMin;
    }

    public double getUniformDayCvThreshold() {
        return uniformDayCvThreshold;
    }

    public void setUniformDayCvThreshold(double uniformDayCvThreshold) {
        this.uniformDayCvThreshold = uniformDayCvThreshold;
    }

    public boolean isSmoothingEnabled() {
        return smoothingEnabled;
    }

    public void setSmoothingEnabled(boolean smoothingEnabled) {
        this.smoothingEnabled = smoothingEnabled;
    }
}