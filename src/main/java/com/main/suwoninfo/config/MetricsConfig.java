package com.main.suwoninfo.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    MeterFilter forcePercentiles() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig c) {
                if (id.getName().equals("api.posts.trade")
                        || id.getName().equals("api.posts.trade.db")
                        || id.getName().equals("api.posts.trade.rebuild")) {
                    return DistributionStatisticConfig.builder()
                            .percentiles(0.5, 0.95, 0.99)
                            .percentilesHistogram(false)
                            .build()
                            .merge(c);
                }
                return c;
            }
        };
    }
}