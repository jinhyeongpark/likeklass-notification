package com.liveklass.notification.infrastructure.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean("taskExecutor")
    @Override
    @NonNull
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 기본적 스레드 개수
        executor.setCorePoolSize(10);

        // 최대 생성 가능한 스레드 개수
        executor.setMaxPoolSize(20);

        // 작업 큐 크기
        executor.setQueueCapacity(500);

        // 로그에서 구분용 접두사 설정
        executor.setThreadNamePrefix("Notification-Async-");

        // 서버 종료 시 진행 중인 작업 완료 후 종료되도록 설정
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
