package com.chzzkGamble.advertise.domain;

import com.chzzkGamble.advertise.repository.AdvertiseRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.auditing.DateTimeProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AdvertiseMapTest {

    private final Advertise ad1 = new Advertise("ad1","url1", 1000L);
    private final Advertise ad2 = new Advertise("ad2","url2", 3000L);
    private final Advertise ad3 = new Advertise("ad3","url3", 5000L);
    private final Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));

    @Autowired
    AdvertiseRepository advertiseRepository;

    @MockBean
    DateTimeProvider dateTimeProvider;

    @SpyBean
    AuditingHandler auditingHandler;

    @BeforeAll
    void saveAds() {
        advertiseRepository.save(ad1);
        advertiseRepository.save(ad2);
        advertiseRepository.save(ad3);
    }

    @Test
    @DisplayName("광고 Map을 만들 수 있다.")
    void from() {
        // given & when & then
        assertThatCode(() -> AdvertiseMap.from(List.of(ad1, ad2, ad3), clock))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("빈 광고 Map을 만들 수 있다.")
    void from_emptyList() {
        // given & when & then
        assertThatCode(() -> AdvertiseMap.from(Collections.emptyList(), clock))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("광고 확률을 정확히 표시할 수 있다.")
    void getProbabilities() {
        // given
        AdvertiseMap advertiseMap = AdvertiseMap.from(List.of(ad1, ad2, ad3), clock);

        // when
        Map<Advertise, Double> probabilities = advertiseMap.getProbabilities();

        // then
        assertAll(() -> {
            assertThat(Double.compare(probabilities.get(ad1), 1_000/ (double) 9_000)).isEqualTo(0);
            assertThat(Double.compare(probabilities.get(ad2), 3_000/ (double) 9_000)).isEqualTo(0);
            assertThat(Double.compare(probabilities.get(ad3), 5_000/ (double) 9_000)).isEqualTo(0);
        });
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2L, 3L, 4L, 5L})
    @DisplayName("생성 일자로부터 1일이 지날 때마다 10%씩 감소된 cost로 적용된다.")
    void applyCostByCreatedAt(long pastDays) throws Exception {
        // given
        LocalDateTime today = LocalDateTime.now(clock);
        LocalDateTime pastDay = today.minusDays(pastDays);

        AutoCloseable autoCloseable = MockitoAnnotations.openMocks(this);
        auditingHandler.setDateTimeProvider(dateTimeProvider);

        // save past advertise
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(pastDay));

        Advertise pastAd = new Advertise("name", "url", 1000L);
        advertiseRepository.save(pastAd);

        // save today advertise
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(today));

        Advertise todayAd = new Advertise("name", "url", 2000L);
        advertiseRepository.save(todayAd);

        // when
        AdvertiseMap advertiseMap = AdvertiseMap.from(List.of(pastAd, todayAd), clock);
        Map<Advertise, Double> probabilities = advertiseMap.getProbabilities();

        // then
        long expectedCost = pastAd.getCost() * (10 - pastDays) / 10;
        assertThat(probabilities.get(pastAd)).isEqualTo(expectedCost / (double) (expectedCost + 2000));

        autoCloseable.close();
    }

    @RepeatedTest(100)
    @DisplayName("랜덤 광고를 확률에 맞게 획득할 수 있다.")
    void getRandom() {
        // given
        AdvertiseMap advertiseMap = AdvertiseMap.from(List.of(ad1, ad2, ad3), clock);

        // when
        int ad1Count = 0;
        int ad2Count = 0;
        int ad3Count = 0;

        for (int i = 0; i < 9000; i++) {
            Advertise ad = advertiseMap.getRandom();
            if (ad.equals(ad1)) ad1Count++;
            if (ad.equals(ad2)) ad2Count++;
            if (ad.equals(ad3)) ad3Count++;
        }

        // then
        assertThat(ad1Count).isBetween(500, 1500);
        assertThat(ad2Count).isBetween(2500, 3500);
        assertThat(ad3Count).isBetween(4500, 5500);
    }

    @Test
    @DisplayName("빈 Map일 경우, 기본 광고가 나온다")
    void getRandom_empty() {
        // given
        AdvertiseMap advertiseMap = AdvertiseMap.from(Collections.emptyList(), clock);

        // when & then
        assertThat(advertiseMap.getRandom().getName()).isEqualTo("Default Advertise");
    }
}