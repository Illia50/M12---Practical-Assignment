package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:concurrencydb;DB_CLOSE_DELAY=-1")
class AnimalServiceConcurrencyTest {

    private static final String MICROCHIP = "CHIP-RACE-1";

    @Autowired
    private AnimalService animalService;

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private ExistsCheckHook existsCheckHook;

    @AfterEach
    void cleanUp() {
        existsCheckHook.afterCheck = null;
        animalRepository.deleteAll();
    }

    @Test
    void concurrentCreatesWithSameMicrochip_shouldBothPassServiceCheckButOnlyOneIsStored() throws Exception {
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        Queue<Boolean> observedExists = new ConcurrentLinkedQueue<>();

        existsCheckHook.afterCheck = exists -> {
            observedExists.add(exists);
            try {
                bothChecked.await(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("Second request never reached the check", e);
            }
        };

        Callable<Object> createMukhtar = () -> animalService.create(
                new AnimalCreateRequest("Mukhtar", AnimalType.DOG, "Labrador", 3, null, MICROCHIP));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Object> outcomes = new ArrayList<>();
        try {
            List<Future<Object>> futures = List.of(pool.submit(createMukhtar), pool.submit(createMukhtar));
            for (Future<Object> future : futures) {
                try {
                    outcomes.add(future.get(10, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(observedExists).as("call outcomes: %s", outcomes).containsExactly(false, false);

        assertThat(outcomes).filteredOn(o -> o instanceof Throwable)
                .singleElement()
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(outcomes).filteredOn(o -> !(o instanceof Throwable)).hasSize(1);

        assertThat(animalRepository.findAll())
                .filteredOn(a -> MICROCHIP.equals(a.getMicrochipId()))
                .hasSize(1);
    }

    @TestConfiguration
    static class HookConfig {
        @Bean
        static ExistsCheckHook existsCheckHook() {
            return new ExistsCheckHook();
        }
    }

    static class ExistsCheckHook implements BeanPostProcessor {

        volatile Consumer<Boolean> afterCheck;

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!(bean instanceof AnimalRepository)) {
                return bean;
            }
            return Proxy.newProxyInstance(
                    AnimalRepository.class.getClassLoader(),
                    new Class<?>[]{AnimalRepository.class},
                    (proxy, method, args) -> {
                        Object result;
                        try {
                            result = method.invoke(bean, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                        Consumer<Boolean> hook = afterCheck;
                        if (hook != null && method.getName().equals("existsByMicrochipId")) {
                            hook.accept((Boolean) result);
                        }
                        return result;
                    });
        }
    }
}
