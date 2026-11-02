## Technology choices

Do no add maven dependencies withouth explaining the purpose and asking confirmation

# Java Coding Guidelines

Java formatting is configured in `.editorconfig` and enforced by Spotless in
`pom.xml`.
Use italian languiage for class, method and variable names.

## Naming Conventions

Identifiers must clearly communicate intent using specific letter casing depending on the element type.

| Element | Casing Style | Example | Notes |
| :--- | :--- | :--- | :--- |
| **Classes & Interfaces** | `UpperCamelCase` | `UrlHttpRequest` | Must be nouns or noun phrases. |
| **Methods** | `lowerCamelCase` | `findPostById()` | Must be verbs or verb phrases. |
| **Variables & Parameters** | `lowerCamelCase` | `username` | Avoid single-character names except for loop counters. |
| **Constants** | `UPPER_CASE` | `MAX_RETRY_LIMIT` | Must be `static final` and truly immutable. |

*Note: Acronyms should be treated as words (e.g., `XmlHttpRequest` instead of `XMLHTTPRequest`).*

---

## Coding Practices

* **Annotations:** Always use the `@Override` annotation when overriding a method or implementing an interface method.
* **Variable Declarations:** Prefer **one variable declaration per line**. Do not write `String username, email;`.
* **Modifiers:** Access level modifiers (`public`, `protected`, `private`) should be explicitly defined for classes, methods, and member variables.
* **Static Members:** Always qualify static members using the class name, never through an object instance (e.g., Use `Math.sqrt()`, not `myMathInstance.sqrt()`).
* **Long Literals:** Always use an uppercase `L` suffix for long literals (e.g., `3000000000L`) to prevent confusion with the digit `1`.

---

## Documentation Comments (Javadoc)

Starting with **Java 23**, Java natively supports CommonMark-based **Markdown documentation comments** using triple slashes (`///`) as defined in [JEP 467](https://openjdk.org "JEP 467"). This replaces traditional HTML tags.

* **Formatting:** Use `///` for documentation blocks instead of `/** ... */`.
* **Hierarchy:** Use a single hashtag (`#`) for the class description and double hashtags (`##`) for method sections.
* **Code blocks:** Use triple backticks (```java) for syntax-highlighted code examples inside your documentation.
* **Tags:** Standard Javadoc tags like `@param`, `@return`, and `@throws` are still fully supported alongside Markdown syntax.

```java
/// # UserService
/// Provides core authentication and user management facilities.
///
/// ## Usage Example
/// ```java
/// UserService service = new UserService();
/// User user = service.findPostById("USR-102");
/// ```
public class UserService {

    /// Finds a user profile using their unique identifier string.
    ///
    /// @param urlId The unique system ID.
    /// @return The corresponding [User] object.
    /// @throws UserNotFoundException if the ID is missing.
    public User findPostById(String urlId) throws UserNotFoundException {
        // Implementation here
    }
}
```

## Proposed package structure

Use the existing base package `com.epicode.magazzino` and organize the new implementation by responsibility:

Comment code
Document APIs

Non alterare lo stack tecnologico descritto dal pom.xml senza esplicito permesso dell'utente.

## Dettagli sui tipi
I valori numerici con decimali vanno rappresentati con la classe BigDecimal


- Non superare 3 nuove classi per richiesta senza autorizzazione esplicita.
- Non dichiarare test eseguiti se non lo sono.
- Quando esistono indicazioni alternative in un `AGENTS.md` locale, applicale; per indicazioni esplicite dell'utente chiedi conferma.
- Non usare `LocalDateTime`: usa `ZonedDateTime` o `Instant` per tenere conto dei fusi orari tra aeroporti.
- La business logic deve stare nei service: Controller chiama Service che chiama Repository. 
- Usa DTO Request/Response dedicati, mappati sulle entità, e limita le risposte ai dati necessari, senza dati sensibili.
- Non usare `@Autowired`: preferisci l'iniezione via costruttore. Segui la struttura standard Maven/Spring Boot.
- Mantieni sicurezza, guardrail ed error handling centralizzato.
- motiva con un commento ogni metodo ed ogni classe. Se il punto è critico, aggiungere commenti nel testo
- usare nomi di variabili e metodi significativi
