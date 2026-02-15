package com.main.suwoninfo.batch;


import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;


@Configuration
public class PaginationCacheBatchConfig {

    private final int CHUNK_SIZE = 1000;

    @Bean
    public Job paginationCacheJob(JobRepository jobRepository, Step cacheStep) {
        return new JobBuilder("PaginationCacheJob", jobRepository)
                .start(cacheStep)
                .build();
    }

    @Bean
    public Step cacheStep(JobRepository jobRepository,
                          PlatformTransactionManager transactionManager,
                          ItemReader<Long> postPagingReader,
                          RedisPaginationWriter redisPaginationWriter) throws Exception {

        return new StepBuilder("cacheStep", jobRepository)
                .<Long, Long>chunk(CHUNK_SIZE, transactionManager)
                .reader(postPagingReader)
                .writer(redisPaginationWriter)
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<Long> postPagingReader(DataSource dataSource,
                                                       @Value("#{jobParameters['post_type']}") String type) throws Exception {
        SqlPagingQueryProviderFactoryBean queryProvider = new SqlPagingQueryProviderFactoryBean();
        queryProvider.setDataSource(dataSource);

        queryProvider.setSelectClause("SELECT post_id");
        queryProvider.setFromClause("FROM post");
        queryProvider.setWhereClause("WHERE post_type = '" + type + "'");

        Map<String, Order> sortKeys = new LinkedHashMap<>();
        sortKeys.put("post_id", Order.DESCENDING);
        queryProvider.setSortKeys(sortKeys);

        return new JdbcPagingItemReaderBuilder<Long>()
                .name("postPagingReader")
                .pageSize(CHUNK_SIZE) // 1000개씩
                .dataSource(dataSource)
                .queryProvider(queryProvider.getObject())
                .rowMapper((rs, rowNum) -> rs.getLong("post_id"))
                .build();
    }
}
