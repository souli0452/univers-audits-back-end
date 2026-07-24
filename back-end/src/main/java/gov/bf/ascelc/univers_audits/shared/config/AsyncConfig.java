package gov.bf.ascelc.univers_audits.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * @EnableAsync (UniversAuditsApplication) utilise par défaut un
 * SimpleAsyncTaskExecutor — un thread neuf et non réutilisé par tâche, sans
 * limite. AuditService/EmailService/SmsService en dépendent ; un exécuteur
 * borné évite une création de threads non contrôlée sous charge.
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("asce-async-");
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) ->
                log.error("Exception non interceptée dans une tâche @Async — méthode: {} : {}",
                        method.getName(), ex.getMessage(), ex);
    }
}
