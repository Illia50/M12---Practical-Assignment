package lv.bootcamp.shelter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lv.bootcamp.shelter.config.SecurityConfig;
import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.dto.AnimalResponse;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.service.AnimalNotFoundException;
import lv.bootcamp.shelter.service.AnimalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ticket: ANIMAL-2 (see README).
 * findById_shouldReturn404WhenNotFound is already implemented as a worked
 * example (also see AdopterControllerTest for the same pattern) — add the
 * remaining tests yourself.
 * <p>
 * @Import(SecurityConfig.class) is required: without it, this slice falls
 * back to Spring Boot's default "require auth for everything" security
 * instead of this project's actual (permissive, for these endpoints) rules.
 */
@WebMvcTest(AnimalController.class)
@Import(SecurityConfig.class)
class AnimalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AnimalService animalService;

    @Test
    void findById_shouldReturn404WhenNotFound() throws Exception {
        when(animalService.findById(99L)).thenThrow(new AnimalNotFoundException(99L));

        mockMvc.perform(get("/api/animals/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void findAll_shouldReturnAnimalsAsJson() throws Exception {
        when(animalService.findAll()).thenReturn(List.of(
                new AnimalResponse(1L, "Mukhtar", AnimalType.DOG, "Labrador", 3, "Friendly", AnimalStatus.AVAILABLE),
                new AnimalResponse(2L, "Barsik", AnimalType.CAT, "Siamese", 5, "Calm", AnimalStatus.RESERVED)));

        mockMvc.perform(get("/api/animals"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Mukhtar"))
                .andExpect(jsonPath("$[0].type").value("DOG"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[1].name").value("Barsik"))
                .andExpect(jsonPath("$[1].status").value("RESERVED"));
    }

    @Test
    void create_shouldReturn400AndNotCallServiceWhenBodyIsInvalid() throws Exception {
        String invalidBody = """
                {"name": "", "type": null, "age": -1}
                """;

        mockMvc.perform(post("/api/animals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors", hasItems(
                        startsWith("name:"),
                        startsWith("type:"),
                        startsWith("age:"))));

        verifyNoInteractions(animalService);
    }

    @Test
    void create_shouldReturn201WithCreatedAnimal() throws Exception {
        when(animalService.create(any())).thenReturn(
                new AnimalResponse(1L, "Mukhtar", AnimalType.DOG, "Labrador", 3, "Friendly", AnimalStatus.AVAILABLE));

        mockMvc.perform(post("/api/animals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnimalCreateRequest(
                                "Mukhtar", AnimalType.DOG, "Labrador", 3, "Friendly", "CHIP-001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Mukhtar"))
                .andExpect(jsonPath("$.type").value("DOG"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }
}
