package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.audit.AuditLogger;
import lv.bootcamp.shelter.audit.RejectionReason;
import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.model.Adopter;
import lv.bootcamp.shelter.model.AdoptionResult;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AdopterRepository;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdoptionEligibilityServiceTest {

    private static final Long ADOPTER_ID = 1L;
    private static final Long ANIMAL_ID = 2L;

    @Mock
    private AdopterRepository adopterRepository;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private AdoptionEligibilityService service;

    @Test
    void shouldRejectWhenAdopterNotFound() {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result).isEqualTo(AdoptionResult.rejected(RejectionReasons.ADOPTER_NOT_FOUND));
        verifyNoInteractions(animalRepository, auditLogger, notificationClient);
    }

    @Test
    void shouldRejectWhenAnimalNotFound() {
        givenAdopter(adopter(30));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result).isEqualTo(AdoptionResult.rejected(RejectionReasons.ANIMAL_NOT_FOUND));
        verifyNoInteractions(auditLogger, notificationClient);
    }

    @ParameterizedTest
    @EnumSource(value = AnimalStatus.class, names = {"RESERVED", "ADOPTED"})
    void shouldRejectWhenAnimalNotAvailable(AnimalStatus status) {
        givenAdopter(adopter(30));
        Animal animal = animal(AnimalType.DOG, 3);
        animal.setStatus(status);
        givenAnimal(animal);

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result).isEqualTo(AdoptionResult.rejected(RejectionReasons.ANIMAL_NOT_AVAILABLE));
        verifyNoInteractions(auditLogger, notificationClient);
    }

    @Test
    void shouldRejectAndAuditWhenAdopterIs17() {
        givenAdopter(adopter(17));
        givenAnimal(animal(AnimalType.DOG, 3));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result).isEqualTo(AdoptionResult.rejected(RejectionReasons.UNDERAGE));
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.UNDERAGE);
        verifyNoApproval();
    }

    @Test
    void shouldApproveWhenAdopterIsExactly18() {
        givenAdopter(adopter(18));
        givenAnimal(animal(AnimalType.DOG, 3));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isTrue();
    }

    @ParameterizedTest(name = "largeProperty={0}, currentPets={1} -> approved={2}")
    @CsvSource({
            "false, 2, true",
            "false, 3, false",
            "false, 4, false",
            "true,  3, true",
            "true,  4, true",
            "true,  5, false"
    })
    void shouldApplyPetLimitDependingOnPropertySize(boolean largeProperty, int currentPets, boolean approved) {
        Adopter adopter = adopter(30);
        adopter.setLargeProperty(largeProperty);
        adopter.setCurrentPetCount(currentPets);
        givenAdopter(adopter);
        givenAnimal(animal(AnimalType.DOG, 3));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isEqualTo(approved);
        if (!approved) {
            assertThat(result.reason()).isEqualTo(RejectionReasons.PET_LIMIT_REACHED);
            verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.PET_LIMIT_REACHED);
            verifyNoApproval();
        }
    }

    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"BIRD", "RABBIT"})
    void shouldRejectExoticAnimalWithoutPermit(AnimalType type) {
        givenAdopter(adopter(30));
        givenAnimal(animal(type, 2));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result).isEqualTo(AdoptionResult.rejected(RejectionReasons.EXOTIC_PERMIT_REQUIRED));
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.NO_EXOTIC_PERMIT);
        verifyNoApproval();
    }

    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"BIRD", "RABBIT"})
    void shouldApproveExoticAnimalWithPermit(AnimalType type) {
        Adopter adopter = adopter(30);
        adopter.setExoticPermit(true);
        givenAdopter(adopter);
        givenAnimal(animal(type, 2));

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"DOG", "CAT", "OTHER"})
    void shouldNotRequirePermitForNonExoticAnimals(AnimalType type) {
        givenAdopter(adopter(30));
        givenAnimal(animal(type, 2));

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @Test
    void shouldAuditAndNotifyOnApproval() {
        Adopter adopter = adopter(30);
        adopter.setPreviousAdoptions(1);
        givenAdopter(adopter);
        givenAnimal(animal(AnimalType.DOG, 3));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result).isEqualTo(AdoptionResult.approved(10));
        verify(auditLogger).logApproval(ADOPTER_ID, ANIMAL_ID, 10);
        verify(auditLogger, never()).logRejection(any(), any(), any());
        verify(notificationClient).sendApprovalNotification("anna@example.com", "Mukhtar");
    }

    @ParameterizedTest(name = "prev={0}, large={1}, animalAge={2}, pets={3} -> score {4}")
    @CsvSource(nullValues = "null", value = {
            "0, false, 3,    0, 0",
            "1, false, 3,    0, 10",
            "3, false, 3,    0, 10",
            "4, false, 3,    0, 15",
            "0, true,  3,    0, 15",
            "0, false, 7,    0, 0",
            "0, false, 8,    0, 20",
            "0, false, null, 0, 0",
            "1, false, 3,    1, 8",
            "1, false, 3,    2, 6",
            "0, false, 3,    2, 0",
            "5, true,  10,   4, 42"
    })
    void shouldCalculatePriorityScore(int previousAdoptions, boolean largeProperty,
                                      Integer animalAge, int currentPets, int expectedScore) {
        Adopter adopter = adopter(30);
        adopter.setPreviousAdoptions(previousAdoptions);
        adopter.setLargeProperty(largeProperty);
        adopter.setCurrentPetCount(currentPets);
        givenAdopter(adopter);
        givenAnimal(animal(AnimalType.DOG, animalAge));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isTrue();
        assertThat(result.priorityScore()).isEqualTo(expectedScore);
        verify(auditLogger).logApproval(ADOPTER_ID, ANIMAL_ID, expectedScore);
    }

    private void verifyNoApproval() {
        verify(auditLogger, never()).logApproval(any(), any(), anyInt());
        verify(notificationClient, never()).sendApprovalNotification(anyString(), anyString());
    }

    private void givenAdopter(Adopter adopter) {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(adopter));
    }

    private void givenAnimal(Animal animal) {
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal));
    }

    private Adopter adopter(int age) {
        Adopter adopter = new Adopter();
        adopter.setId(ADOPTER_ID);
        adopter.setName("Anna");
        adopter.setEmail("anna@example.com");
        adopter.setAge(age);
        return adopter;
    }

    private Animal animal(AnimalType type, Integer age) {
        Animal animal = new Animal();
        animal.setId(ANIMAL_ID);
        animal.setName("Mukhtar");
        animal.setType(type);
        animal.setAge(age);
        animal.setStatus(AnimalStatus.AVAILABLE);
        return animal;
    }
}
