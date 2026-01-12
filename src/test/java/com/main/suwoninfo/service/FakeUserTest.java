package com.main.suwoninfo.service;

import com.main.suwoninfo.dto.UserDto;
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

@SpringBootTest
public class FakeUserTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void fakeInsertTest() {
        Faker faker = new Faker(new Locale("ko"));

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        final int TOTAL_COUNT = 1_000_000;
        final int BATCH_SIZE = 1000;

        String sql = "INSERT INTO user (email, password, name, nickname, student_number, created_time, modified_time) VALUES (?, ?, ?, ?, ?, ?, ?)";

        for (int i = 0; i < TOTAL_COUNT; i+=  BATCH_SIZE) {
            List<UserDto> batchList = new ArrayList<>();
            for (int j = 0; j < BATCH_SIZE; j++) {
                batchList.add(new UserDto(
                        faker.internet().emailAddress(),
                        faker.internet().password(),
                        faker.name().fullName(),
                        faker.lorem().word(),
                        (long) faker.number().numberBetween(1000000, 9999999)
                ));
            }
            jdbcTemplate.batchUpdate(sql,
                    batchList,
                    BATCH_SIZE,
                    (PreparedStatement ps, UserDto user) -> {
                        ps.setString(1, user.getEmail());
                        ps.setString(2, user.getPassword());
                        ps.setString(3, user.getName());
                        ps.setString(4, user.getNickname());
                        ps.setLong(5, user.getStudentNumber());
                        ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
                        ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
                    });

            System.out.println("Inserted " + (i + BATCH_SIZE) + " records...");
        }
        stopWatch.stop();
        System.out.println("총 소요 시간: " + stopWatch.getTotalTimeSeconds() + "초");
    }
}
