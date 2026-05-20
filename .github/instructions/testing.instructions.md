---
description: "Use when writing or modifying tests. Covers test frameworks, test types, naming conventions, and assertion patterns for unit, controller, mapper, and integration tests."
applyTo: "**/test/**/*.java, **/integrationTest/**/*.java"
---

# Testing

JUnit 5 + Mockito + AssertJ. All test classes and methods are **package-private**.

- **Controller tests**: `@WebMvcTest` with `MockMvc` and `@MockitoBean`. Assert with result matchers and Hamcrest.
- **Service tests**: `@ExtendWith(MockitoExtension.class)` with `@Mock`/`@InjectMocks`. AssertJ assertions, `verify()` for interactions.
- **Mapper tests**: instantiate mapper impl directly. `private static final` constants for test data.
- **Exception handler tests**: plain instantiation, no Spring context.
- **Integration tests** (`src/integrationTest/`): `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional` with H2.

## Test Naming

- Services/mappers: `should{Action}` (e.g. `shouldGetAllItems`)
- Controllers: `{method}_{expectedResult}` (e.g. `getItems_returnsOkStatusAndAllItems`)
- Negative: `shouldNot{Action}_when{Condition}ThenThrowsException`
- Mock variables prefixed with `mock` (e.g. `mockItemService`)
