package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.dto.AdoptionRequest;
import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.dto.AnimalResponse;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Ticket: ANIMAL-1 (see README).
 * Three tests are already implemented as a worked example (also see
 * AdopterServiceTest for the same pattern) — add the remaining tests yourself.
 */
@ExtendWith(MockitoExtension.class)
class AnimalServiceTest {

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private AnimalService animalService;

    @Captor
    private ArgumentCaptor<Animal> animalCaptor;

    @Captor
    private ArgumentCaptor<List<Long>> idsCaptor;

    @Test
    void create_shouldSaveAnimalWithAvailableStatus() {
        Animal saved = new Animal();
        saved.setId(1L);
        saved.setName("Mukhtar");
        saved.setType(AnimalType.DOG);
        saved.setStatus(AnimalStatus.AVAILABLE);

        when(animalRepository.save(any(Animal.class))).thenReturn(saved);

        AnimalResponse response = animalService.create(
                new AnimalCreateRequest("Mukhtar", AnimalType.DOG, "Labrador", 3, "Friendly", null));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Mukhtar");
        assertThat(response.status()).isEqualTo(AnimalStatus.AVAILABLE);
    }

    @Test
    void findById_shouldThrowWhenAnimalNotFound() {
        when(animalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> animalService.findById(99L))
                .isInstanceOf(AnimalNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void adopt_shouldThrowWhenAnimalAlreadyAdopted() {
        Animal adopted = new Animal();
        adopted.setId(1L);
        adopted.setStatus(AnimalStatus.ADOPTED);

        when(animalRepository.findById(1L)).thenReturn(Optional.of(adopted));

        assertThatThrownBy(() -> animalService.adopt(
                new AdoptionRequest(1L, "Anna", "anna@example.com")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void adopt_shouldMarkAnimalAdoptedAndSendNotification() {
        Animal available = newAnimal(1L, "Mukhtar", AnimalStatus.AVAILABLE);

        when(animalRepository.findById(1L)).thenReturn(Optional.of(available));
        when(animalRepository.save(any(Animal.class))).then(returnsFirstArg());

        AnimalResponse response = animalService.adopt(
                new AdoptionRequest(1L, "Anna", "anna@example.com"));

        verify(animalRepository).save(animalCaptor.capture());
        assertThat(animalCaptor.getValue().getStatus()).isEqualTo(AnimalStatus.ADOPTED);
        assertThat(response.status()).isEqualTo(AnimalStatus.ADOPTED);

        verify(notificationClient).sendAdoptionNotification(1L, "Mukhtar", "anna@example.com");
    }

    @Test
    void reserveMultiple_shouldNotifyWithExactlyTheReservedIds() {
        Animal mukhtar = newAnimal(1L, "Mukhtar", AnimalStatus.AVAILABLE);
        Animal barsik = newAnimal(2L, "Barsik", AnimalStatus.AVAILABLE);

        when(animalRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(mukhtar, barsik));
        when(animalRepository.save(any(Animal.class))).then(returnsFirstArg());

        List<AnimalResponse> responses = animalService.reserveMultiple(List.of(1L, 2L));

        verify(notificationClient).sendBulkStatusNotification(idsCaptor.capture(), eq("RESERVED"));
        assertThat(idsCaptor.getValue()).containsExactly(1L, 2L);
        assertThat(responses).extracting(AnimalResponse::status)
                .containsOnly(AnimalStatus.RESERVED);
    }

    @Test
    void create_shouldRejectDuplicateMicrochipWithoutSaving() {
        when(animalRepository.existsByMicrochipId("CHIP-001")).thenReturn(true);

        assertThatThrownBy(() -> animalService.create(
                new AnimalCreateRequest("Mukhtar", AnimalType.DOG, "Labrador", 3, "Friendly", "CHIP-001")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHIP-001");

        verify(animalRepository, never()).save(any());
        verifyNoInteractions(notificationClient);
    }

    private Animal newAnimal(Long id, String name, AnimalStatus status) {
        Animal animal = new Animal();
        animal.setId(id);
        animal.setName(name);
        animal.setType(AnimalType.DOG);
        animal.setStatus(status);
        return animal;
    }
}
