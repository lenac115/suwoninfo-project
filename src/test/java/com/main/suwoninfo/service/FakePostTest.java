package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.Post;
import net.datafaker.Faker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StopWatch;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@SpringBootTest
public class FakePostTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void PostBulkTest() {

        Faker faker = new Faker(new Locale("ko"));
        Random random = new Random();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        final int TOTAL_COUNT = 1_000_000;
        final int BATCH_SIZE = 1000;

        Post.PostType[] postType = Post.PostType.values();
        Post.TradeStatus[] tradeStatus = Post.TradeStatus.values();

        String sql = "INSERT INTO post (user_id, post_type, price, trade_status, title, content, created_time, modified_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        for (int i = 0; i < TOTAL_COUNT; i += BATCH_SIZE) {
            List<BulkPostDto> batchList = new ArrayList<>();

            for (int j = 0; j < BATCH_SIZE; j++) {
                long randomUserId = random.nextInt(1_000_000) + 1;

                batchList.add(new BulkPostDto(
                        randomUserId,
                        faker.book().title(),
                        faker.lorem().sentence(10),
                        random.nextInt(1_000_000),
                        random.nextInt(2),
                        random.nextInt(3),
                        LocalDateTime.now(),
                        LocalDateTime.now()
                ));
            }

            jdbcTemplate.batchUpdate(sql,
                    batchList,
                    BATCH_SIZE,
                    (PreparedStatement ps, BulkPostDto post) -> {
                        ps.setLong(1, post.getUserId());
                        ps.setInt(2, post.getPostType());
                        ps.setInt(3, post.getPrice());
                        ps.setInt(4, post.getTradeStatus());
                        ps.setString(5, post.getTitle());
                        ps.setString(6, post.getContent());
                        ps.setTimestamp(7, Timestamp.valueOf(post.getCreatedTime()));
                        ps.setTimestamp(8, Timestamp.valueOf(post.getCreatedTime()));
                    });

            System.out.println("Inserted " + (i + BATCH_SIZE) + " records...");
        }

        stopWatch.stop();
        System.out.println("완료! 소요 시간: " + stopWatch.getTotalTimeSeconds() + "초");
    }

    static class BulkPostDto {
        long userId;
        String title;
        String content;
        int price;
        int postType;
        int tradeStatus;
        LocalDateTime createdTime;
        LocalDateTime modifiedTime;

        public BulkPostDto(long userId, String title, String content, int price,
                           Integer postType, Integer tradeStatus, LocalDateTime createdTime, LocalDateTime modifiedTime) {
            this.userId = userId;
            this.title = title;
            this.content = content;
            this.price = price;
            this.postType = postType;
            this.tradeStatus = tradeStatus;
            this.createdTime = createdTime;
            this.modifiedTime = modifiedTime;
        }

        public long getUserId() {
            return this.userId;
        }

        public String getTitle() {
            return this.title;
        }

        public String getContent() {
            return this.content;
        }

        public int getPrice() {
            return this.price;
        }

        public Integer getPostType() {
            return this.postType;
        }

        public Integer getTradeStatus() {
            return this.tradeStatus;
        }

        public LocalDateTime getCreatedTime() {
            return this.createdTime;
        }

        public LocalDateTime getModifiedTime() {
            return this.modifiedTime;
        }
    }
}
