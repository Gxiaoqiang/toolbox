package com.toolbox.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通用线程池配置（项目全局共用）
 *
 * @author toolbox
 * @since 2026-07-14
 */
@Configuration
public class ThreadPoolConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPoolConfig.class);

    /** 核心线程数 */
    private static final int CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    /** 最大线程数 */
    private static final int MAX_POOL_SIZE = CORE_POOL_SIZE * 2;

    /** 空闲线程存活时间（秒） */
    private static final long KEEP_ALIVE_SECONDS = 60L;

    /** 任务队列容量 */
    private static final int QUEUE_CAPACITY = 3000;

    @Bean("toolboxExecutor")
    public ThreadPoolExecutor toolboxExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                new ToolboxThreadFactory("toolbox-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        LOGGER.info("[ThreadPoolConfig] toolboxExecutor created: core={}, max={}, queue={}",
                CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY);
        return executor;
    }

    /**
     * 自定义线程工厂，输出有意义的线程名便于排查问题
     */
    private static class ToolboxThreadFactory implements java.util.concurrent.ThreadFactory {

        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        ToolboxThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
