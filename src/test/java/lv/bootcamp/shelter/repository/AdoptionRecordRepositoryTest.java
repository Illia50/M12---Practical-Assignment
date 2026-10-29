package lv.bootcamp.shelter.repository;

import lv.bootcamp.shelter.model.AdoptionRecord;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AdoptionRecordRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 12, 0);
    private static final LocalDateTime SINCE = NOW.minusDays(30);

    @Autowired
    private AdoptionRecordRepository adoptionRecordRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Animal mukhtar;
    private Animal sirko;
    private Animal barsik;

    @BeforeEach
    void setUp() {
        mukhtar = entityManager.persist(newAnimal("Mukhtar", AnimalType.DOG));
        sirko = entityManager.persist(newAnimal("Sirko", AnimalType.DOG));
        barsik = entityManager.persist(newAnimal("Barsik", AnimalType.CAT));
    }

    @Test
    void findRecentAdoptionsByAnimalType_shouldFilterByTypeAndDate() {
        AdoptionRecord recentDog = entityManager.persist(newRecord(mukhtar, "anna@example.com", NOW.minusDays(5)));
        entityManager.persist(newRecord(sirko, "john@example.com", NOW.minusDays(90)));
        entityManager.persist(newRecord(barsik, "kate@example.com", NOW.minusDays(2)));
        entityManager.flush();
        entityManager.clear();

        var result = adoptionRecordRepository.findRecentAdoptionsByAnimalType(AnimalType.DOG, SINCE);

        assertThat(adoptionRecordRepository.count()).isEqualTo(3);
        assertThat(result).extracting(AdoptionRecord::getId).containsExactly(recentDog.getId());
        assertThat(result.getFirst().getAnimal().getName()).isEqualTo("Mukhtar");
    }

    @Test
    void findRecentAdoptionsByAnimalType_shouldIncludeRecordExactlyAtSinceBoundary() {
        entityManager.persist(newRecord(mukhtar, "anna@example.com", SINCE));
        entityManager.persist(newRecord(sirko, "john@example.com", SINCE.minusSeconds(1)));
        entityManager.flush();

        var result = adoptionRecordRepository.findRecentAdoptionsByAnimalType(AnimalType.DOG, SINCE);

        assertThat(result).extracting(AdoptionRecord::getAdopterEmail).containsExactly("anna@example.com");
    }

    @Test
    void findRecentAdoptionsByAnimalType_shouldReturnEmptyWhenNothingMatches() {
        entityManager.persist(newRecord(barsik, "kate@example.com", NOW.minusDays(1)));
        entityManager.flush();

        assertThat(adoptionRecordRepository.findRecentAdoptionsByAnimalType(AnimalType.BIRD, SINCE)).isEmpty();
    }

    @Test
    void findByAdopterEmailEndingWith_shouldFilterByEmailDomain() {
        entityManager.persist(newRecord(mukhtar, "anna@shelter.lv", NOW.minusDays(1)));
        entityManager.persist(newRecord(barsik, "KATE@SHELTER.LV", NOW.minusDays(100)));
        entityManager.persist(newRecord(sirko, "john@example.com", NOW.minusDays(1)));
        entityManager.flush();

        var result = adoptionRecordRepository.findByAdopterEmailEndingWithIgnoreCase("@shelter.lv");

        assertThat(result).extracting(AdoptionRecord::getAdopterEmail)
                .containsExactlyInAnyOrder("anna@shelter.lv", "KATE@SHELTER.LV");
    }

    private Animal newAnimal(String name, AnimalType type) {
        Animal animal = new Animal();
        animal.setName(name);
        animal.setType(type);
        animal.setStatus(AnimalStatus.ADOPTED);
        return animal;
    }

    private AdoptionRecord newRecord(Animal animal, String adopterEmail, LocalDateTime adoptedAt) {
        AdoptionRecord record = new AdoptionRecord();
        record.setAnimal(animal);
        record.setAdopterEmail(adopterEmail);
        record.setAdoptedAt(adoptedAt);
        return record;
    }
}
