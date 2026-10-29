package lv.bootcamp.shelter;

import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.dto.AdoptionRequest;
import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.dto.AnimalResponse;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AnimalRepository;
import lv.bootcamp.shelter.service.AnimalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class AdoptionIntegrationTest {

    @Autowired
    private AnimalService animalService;

    @Autowired
    private AnimalRepository animalRepository;

    @MockitoBean
    private NotificationClient notificationClient;

    @Test
    void createThenAdopt_shouldPersistAdoptedStatusAndNotify() {
        AnimalResponse created = animalService.create(
                new AnimalCreateRequest("Mukhtar", AnimalType.DOG, "Labrador", 3, "Friendly", "CHIP-INT-1"));

        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(AnimalStatus.AVAILABLE);

        AnimalResponse adopted = animalService.adopt(
                new AdoptionRequest(created.id(), "Anna", "anna@example.com"));

        assertThat(adopted.status()).isEqualTo(AnimalStatus.ADOPTED);
        assertThat(animalRepository.findById(created.id()))
                .get()
                .extracting(a -> a.getStatus())
                .isEqualTo(AnimalStatus.ADOPTED);
        verify(notificationClient).sendAdoptionNotification(created.id(), "Mukhtar", "anna@example.com");
    }

    @Test
    void adoptingTwice_shouldFailAndNotifyOnlyOnce() {
        AnimalResponse created = animalService.create(
                new AnimalCreateRequest("Barsik", AnimalType.CAT, "Siamese", 2, "Calm", null));
        animalService.adopt(new AdoptionRequest(created.id(), "Anna", "anna@example.com"));

        assertThatThrownBy(() -> animalService.adopt(
                new AdoptionRequest(created.id(), "John", "john@example.com")))
                .isInstanceOf(IllegalStateException.class);

        verify(notificationClient, times(1)).sendAdoptionNotification(any(), anyString(), anyString());
    }

    @Test
    void creatingDuplicateMicrochip_shouldBeRejectedByService() {
        animalService.create(new AnimalCreateRequest("Mukhtar", AnimalType.DOG, null, 3, null, "CHIP-INT-2"));

        assertThatThrownBy(() -> animalService.create(
                new AnimalCreateRequest("Sirko", AnimalType.DOG, null, 4, null, "CHIP-INT-2")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(animalRepository.count()).isEqualTo(1);
    }
}
