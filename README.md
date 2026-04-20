# shortcut-hibernate
Shortcut Hibernate test project

Repo: https://github.com/alexei-28/shortcut-hibernate

Mentor platform - Shortcut: https://shortcut.education/

Mentor repo (sql-course): https://github.com/LeksyIT/sql-course/tree/main

---
* **Environment**
    * Java 21
    * Gradle: 8.14
    * Spring Boot: 3.5.7
    * Docker
        * Create PostgreSQL database by postgres-docker-compose.yml
    * Test tools:
        * JUnit 5
        * Mockito
        * Testcontainers

---
* **Install application environment**
    * docker-compose -f postgresql-docker-compose.yml up -d
    * Run application: ./gradlew bootRun
    * Validate is application is up: http://localhost:8082/api/v1/actuator/health
    * Swagger UI: http://localhost:8082/api/v1/swagger-ui/index.html


# Hibernate - Практика

## Содержание

1. [Mapping и связи](#mapping-и-связи) (Задачи 1-2)
2. [Жизненный цикл и Dirty Checking](#жизненный-цикл-и-dirty-checking) (Задачи 3-4)
3. [N+1 и EntityGraph](#n1-и-entitygraph) (Задачи 5-6)
4. [Блокировки](#блокировки) (Задачи 7-8)
5. [Продвинутые возможности](#продвинутые-возможности) (Задачи 9-12)
6. [Кэширование](#кэширование) (Задачи 13-14)
7. [HQL, Criteria и Native Query](#hql-criteria-и-native-query) (Задачи 15-16)
8. [Производительность и мониторинг](#производительность-и-мониторинг) (Задачи 17-18)

---

## Mapping и связи

### Задача 1: Правильно настройте двунаправленную связь @OneToMany / @ManyToOne

У вас есть сущности `Post` и `Comment`. Один пост — много комментариев. Нужно:
- Сделать связь двунаправленной
- FK должен лежать в `comments`
- Удаление поста должно удалять все комментарии
- Не должно быть N+1 при загрузке списка постов (без комментариев)
- Добавление комментария через `post.addComment(c)` должно работать корректно

Напишите обе сущности и объясните каждое решение.

>[!note]- Решение
>```java
>@Entity
>@Table(name = "posts")
>@Getter @Setter
>@NoArgsConstructor
>public class Post {
>    @Id
>    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "posts_seq")
>    @SequenceGenerator(name = "posts_seq", sequenceName = "posts_seq", allocationSize = 50)
>    private Long id;
>
>    @Column(nullable = false)
>    private String title;
>
>    @OneToMany(
>        mappedBy = "post",                    // Обратная сторона — владеет Comment.post
>        cascade = CascadeType.ALL,            // persist/remove каскадируются
>        orphanRemoval = true,                 // Удаление из коллекции → DELETE
>        fetch = FetchType.LAZY                // Не грузить при SELECT Post
>    )
>    private List<Comment> comments = new ArrayList<>();
>
>    // Helper-методы: синхронизация обеих сторон критична,
>    // иначе в 1-м кэше Hibernate будет неконсистентное состояние
>    public void addComment(Comment c) {
>        comments.add(c);
>        c.setPost(this);
>    }
>
>    public void removeComment(Comment c) {
>        comments.remove(c);
>        c.setPost(null);
>    }
>
>    // equals/hashCode по id (с защитой от null для transient)
>    @Override
>    public boolean equals(Object o) {
>        if (this == o) return true;
>        if (!(o instanceof Post p)) return false;
>        return id != null && id.equals(p.id);
>    }
>
>    @Override
>    public int hashCode() { return getClass().hashCode(); }
>}
>
>@Entity
>@Table(name = "comments")
>@Getter @Setter
>@NoArgsConstructor
>public class Comment {
>    @Id
>    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "comments_seq")
>    @SequenceGenerator(name = "comments_seq", sequenceName = "comments_seq", allocationSize = 50)
>    private Long id;
>
>    @Column(nullable = false, length = 2000)
>    private String body;
>
>    @ManyToOne(fetch = FetchType.LAZY)       // Явно LAZY! Иначе — EAGER по умолчанию
>    @JoinColumn(name = "post_id", nullable = false)
>    private Post post;
>}
>```
>
>**Ключевые решения и почему:**
>
>| Решение | Причина |
>|---------|---------|
>| `mappedBy = "post"` | Владельцем связи делаем `Comment.post` — там лежит FK `post_id`. Без `mappedBy` Hibernate создал бы лишнюю промежуточную таблицу |
>| `cascade = CascadeType.ALL` | Нужно удалить комментарии вместе с постом (composition) |
>| `orphanRemoval = true` | `post.getComments().remove(c)` должно удалять комментарий из БД |
>| `fetch = FetchType.LAZY` на `@OneToMany` | Уже дефолт, но явно — безопаснее |
>| `fetch = FetchType.LAZY` на `@ManyToOne` | По умолчанию `EAGER` — главный источник скрытых SELECT'ов. **Всегда** переопределяйте |
>| `addComment()` helper | Без синхронизации обеих сторон в рамках одной транзакции Hibernate увидит пост без нового комментария в 1-м кэше |
>| `equals` по id + `hashCode` на классе | Классическая рекомендация для JPA-сущностей: см. Vlad Mihalcea's JPA guide |
>| `allocationSize = 50` у sequence | Снижает round-trips к БД при массовой вставке |

### Задача 2: Выберите стратегию наследования и реализуйте иерархию

Есть сущности: `PaymentMethod` (абстрактная), `CreditCard`, `BankAccount`, `DigitalWallet`. Каждая имеет свои специфичные поля. Есть частые запросы типа `"найти все методы оплаты пользователя"`.

Выберите стратегию наследования, аргументируйте, реализуйте.

>[!note]- Решение
>**Выбор: SINGLE_TABLE.**
>
>Причины:
>- Полиморфный запрос "все методы пользователя" — горячий сценарий. `SINGLE_TABLE` → один SELECT без JOIN'ов
>- Наследников немного (3), поля умеренные — таблица не разрастётся до сотен колонок
>- `JOINED` дал бы LEFT JOIN к 3 таблицам при каждой загрузке — для часто читаемых данных перебор
>- `TABLE_PER_CLASS` плох полиморфными запросами (`UNION ALL` всех таблиц) + нельзя `IDENTITY`
>
>Компромисс `SINGLE_TABLE`: все специфичные колонки должны быть `nullable` в БД → валидация через `@NotNull` на уровне Java + проверочные constraint'ы в миграции (`CHECK (type <> 'CREDIT_CARD' OR card_number IS NOT NULL)`).
>
>```java
>@Entity
>@Table(name = "payment_methods")
>@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
>@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
>@Getter @Setter
>public abstract class PaymentMethod {
>    @Id
>    @GeneratedValue(strategy = GenerationType.SEQUENCE)
>    private Long id;
>
>    @ManyToOne(fetch = FetchType.LAZY, optional = false)
>    @JoinColumn(name = "user_id")
>    private User user;
>
>    @Column(nullable = false)
>    private boolean active = true;
>
>    @Column(name = "created_at", nullable = false, updatable = false)
>    private Instant createdAt = Instant.now();
>
>    public abstract String getDisplayName();
>}
>
>@Entity
>@DiscriminatorValue("CREDIT_CARD")
>@Getter @Setter
>public class CreditCard extends PaymentMethod {
>    @Column(name = "card_number_masked", length = 19)
>    private String cardNumberMasked;   // "**** **** **** 1234"
>
>    @Column(name = "card_token")
>    private String cardToken;          // Токен из платёжного шлюза
>
>    @Column(name = "expires_at")
>    private YearMonth expiresAt;
>
>    @Override
>    public String getDisplayName() {
>        return "Card " + cardNumberMasked;
>    }
>}
>
>@Entity
>@DiscriminatorValue("BANK_ACCOUNT")
>@Getter @Setter
>public class BankAccount extends PaymentMethod {
>    @Column(name = "iban", length = 34)
>    private String iban;
>
>    @Column(name = "bic", length = 11)
>    private String bic;
>
>    @Override
>    public String getDisplayName() {
>        return "Bank " + iban.substring(iban.length() - 4);
>    }
>}
>
>@Entity
>@DiscriminatorValue("DIGITAL_WALLET")
>@Getter @Setter
>public class DigitalWallet extends PaymentMethod {
>    @Enumerated(EnumType.STRING)
>    @Column(name = "wallet_provider")
>    private WalletProvider provider;   // PAYPAL, APPLE_PAY, GOOGLE_PAY
>
>    @Column(name = "wallet_id")
>    private String walletId;
>
>    @Override
>    public String getDisplayName() {
>        return provider.name() + " " + walletId;
>    }
>}
>```
>
>**Миграция с CHECK constraints:**
>```sql
>CREATE TABLE payment_methods (
>    id BIGINT PRIMARY KEY,
>    type VARCHAR(20) NOT NULL,
>    user_id BIGINT NOT NULL REFERENCES users(id),
>    active BOOLEAN NOT NULL DEFAULT TRUE,
>    created_at TIMESTAMP NOT NULL,
>
>    card_number_masked VARCHAR(19),
>    card_token VARCHAR(255),
>    expires_at DATE,
>
>    iban VARCHAR(34),
>    bic VARCHAR(11),
>
>    wallet_provider VARCHAR(20),
>    wallet_id VARCHAR(255),
>
>    CONSTRAINT pm_type_ck CHECK (type IN ('CREDIT_CARD', 'BANK_ACCOUNT', 'DIGITAL_WALLET')),
>    CONSTRAINT pm_cc_ck CHECK (type <> 'CREDIT_CARD' OR card_token IS NOT NULL),
>    CONSTRAINT pm_ba_ck CHECK (type <> 'BANK_ACCOUNT' OR iban IS NOT NULL),
>    CONSTRAINT pm_dw_ck CHECK (type <> 'DIGITAL_WALLET' OR wallet_provider IS NOT NULL)
>);
>
>CREATE INDEX idx_pm_user_active ON payment_methods(user_id) WHERE active = TRUE;
>```
>
>**Репозиторий и полиморфный запрос:**
>```java
>public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
>    // Полиморфный: вернёт все наследники
>    List<PaymentMethod> findByUserIdAndActiveTrue(Long userId);
>
>    // Запрос только одного типа
>    @Query("SELECT c FROM CreditCard c WHERE c.user.id = :userId AND c.active = true")
>    List<CreditCard> findActiveCardsByUser(@Param("userId") Long userId);
>}
>```

---

## Жизненный цикл и Dirty Checking

### Задача 3: Найдите баг — изменения не сохраняются

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repo;
    private final EntityManager em;

    public void updateUserName(Long id, String newName) {
        User user = repo.findById(id).orElseThrow();
        em.detach(user);

        user.setName(newName);
        user.setUpdatedAt(Instant.now());

        repo.save(user);
    }
}
```

Вызов `updateUserName(1L, "Alice")` приводит к необъяснимому поведению: иногда имя меняется, иногда нет. Объясните, что происходит, и исправьте.

>[!note]- Решение
>**Что происходит:**
>
>1. `repo.findById(id)` — объект `user` в состоянии **PERSISTENT**
>2. `em.detach(user)` — переводим в **DETACHED**
>3. `user.setName(...)` — объект detached, dirty checking не работает, изменения "в воздухе"
>4. `repo.save(user)` — для `DETACHED` объекта Spring Data вызывает `em.merge(user)`
>
>**Почему `merge` работает нестабильно:**
>- `merge(detached)` создаёт **новый** PERSISTENT объект `managed`, копируя в него состояние `detached`
>- Если это первый вызов в транзакции — `managed` попадёт в 1-й кэш, всё ок
>- Но **внутри одной транзакции** после `detach` + `merge` могут возникать гонки с другими операциями в той же сессии, особенно если параллельно `findById` вернул снова кэшированную версию
>- Главная проблема: результат `merge` **не присваивается обратно** — код продолжает держать `detached` объект и может работать с ним дальше (в реальном коде это приводит к потере изменений после этой точки)
>
>**Настоящий баг в коде:** `em.detach()` вообще не нужен. Это паттерн "я хочу merge" — но используется по незнанию, как будто `detach` помогает.
>
>**Исправление — минимальное:**
>```java
>@Transactional
>public void updateUserName(Long id, String newName) {
>    User user = repo.findById(id).orElseThrow();
>    user.setName(newName);
>    user.setUpdatedAt(Instant.now());
>    // Никакого save() не нужно — dirty checking сделает UPDATE при commit'е
>}
>```
>
>**Почему так правильно:**
>- `@Transactional` — загруженный `user` остаётся **PERSISTENT** до конца метода
>- Dirty checking автоматически обнаружит изменения и сгенерирует UPDATE при flush'е перед commit'ом
>- `repo.save()` избыточен — это не создаёт лишний запрос (Spring Data видит, что это merge, а объект уже managed), но загромождает код
>
>**Если очень хочется `save()` (например, чтобы получить возвращённый объект для дальнейшей работы):**
>```java
>@Transactional
>public User updateUserName(Long id, String newName) {
>    User user = repo.findById(id).orElseThrow();
>    user.setName(newName);
>    user.setUpdatedAt(Instant.now());
>    return user;  // Всё равно managed, save() не нужен
>}
>```
>
>**Урок:** если объект PERSISTENT и вы в транзакции — просто изменяйте поля. `save`/`merge`/`detach` — сигналы, что вы не понимаете жизненный цикл сущности.

### Задача 4: Оптимизируйте массовое обновление с flush/clear

Вам нужно обновить флаг `verified = true` у 500 000 пользователей, прошедших KYC. Наивный код:

```java
@Transactional
public void markVerified(List<Long> userIds) {
    for (Long id : userIds) {
        User u = repo.findById(id).orElseThrow();
        u.setVerified(true);
    }
}
```

Этот код съедает всю heap и падает с OOM. Предложите 2-3 варианта оптимизации с разными tradeoff'ами.

>[!note]- Решение
>**Почему код падает:**
>- Каждый загруженный `User` остаётся в 1-м кэше EntityManager'а до конца транзакции
>- 500k объектов × snapshot для dirty checking × связи → несколько гигабайт в heap
>- Flush при commit'е попытается сравнить snapshots всех 500k объектов одновременно
>
>**Вариант 1 — Bulk UPDATE через JPQL (лучший для простого случая):**
>```java
>@Modifying(clearAutomatically = true, flushAutomatically = true)
>@Query("UPDATE User u SET u.verified = true WHERE u.id IN :ids")
>int markVerified(@Param("ids") List<Long> ids);
>```
>
>Плюсы: один SQL, никаких объектов в памяти, мгновенно.
>Минусы:
>- Не триггерит `@PreUpdate` / EntityListeners
>- Обходит dirty checking → не инкрементирует `@Version` (обязательно `clearAutomatically = true`, чтобы 1-й кэш не содержал устаревших копий)
>- `IN` с огромным списком плох — разбивайте на чанки по 1000-5000 ID
>
>**Вариант 2 — batch + flush + clear:**
>```java
>@PersistenceContext
>private EntityManager em;
>
>@Transactional
>public void markVerified(List<Long> userIds) {
>    int batchSize = 50;  // Должен совпадать с hibernate.jdbc.batch_size
>    for (int i = 0; i < userIds.size(); i++) {
>        User u = em.find(User.class, userIds.get(i));
>        u.setVerified(true);
>        if (i % batchSize == 0 && i > 0) {
>            em.flush();   // Отправить UPDATE'ы в БД
>            em.clear();   // Очистить 1-й кэш
>        }
>    }
>    em.flush();
>    em.clear();
>}
>```
>
>Нужны настройки:
>```yaml
>spring.jpa.properties.hibernate:
>  jdbc.batch_size: 50
>  order_inserts: true
>  order_updates: true
>  jdbc.batch_versioned_data: true   # Для @Version
>```
>
>Плюсы: работают `@PreUpdate` и EntityListeners, инкрементируется `@Version`.
>Минусы: всё равно N+1 на чтение, медленнее bulk UPDATE в 10-100 раз.
>
>**Вариант 3 — StatelessSession для реально больших объёмов:**
>```java
>@Autowired
>private EntityManagerFactory emf;
>
>public void markVerified(List<Long> userIds) {
>    SessionFactory sf = emf.unwrap(SessionFactory.class);
>    try (StatelessSession ss = sf.openStatelessSession()) {
>        Transaction tx = ss.beginTransaction();
>        for (Long id : userIds) {
>            User u = ss.get(User.class, id);
>            u.setVerified(true);
>            ss.update(u);
>        }
>        tx.commit();
>    }
>}
>```
>
>Плюсы: нет 1-го кэша → константный расход памяти.
>Минусы: не работают каскады, lazy loading, EntityListeners.
>
>**Выбор:**
>| Сценарий | Выбор |
>|----------|-------|
>| Простое обновление флага без аудита | **Bulk JPQL** |
>| Нужен аудит (`@PreUpdate`, `@Version`, Envers) | **batch + flush + clear** |
>| Миллионы записей, нужны hooks, экстремальная производительность | **StatelessSession** |
>| Совсем огромные объёмы (десятки миллионов) | **JdbcTemplate + batchUpdate** или `COPY` |

---

## N+1 и EntityGraph

### Задача 5: Устраните N+1 тремя разными способами

Код репозитория и сервиса:

```java
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatus(OrderStatus status);
}

@Transactional(readOnly = true)
public List<OrderDTO> getActiveOrders() {
    return repo.findByStatus(OrderStatus.ACTIVE).stream()
        .map(o -> new OrderDTO(
            o.getId(),
            o.getUser().getName(),              // N+1 #1
            o.getItems().stream()               // N+1 #2
                .map(i -> i.getProduct().getName())  // N+1 #3
                .toList()
        ))
        .toList();
}
```

Модель: `Order` ← `@ManyToOne User`, `Order` → `@OneToMany List<OrderItem>`, `OrderItem` ← `@ManyToOne Product`.

Решите проблему **тремя способами** и покажите, какой SQL будет сгенерирован в каждом.

>[!note]- Решение
>**Способ 1 — JPQL JOIN FETCH:**
>```java
>public interface OrderRepository extends JpaRepository<Order, Long> {
>
>    @Query("""
>        SELECT DISTINCT o FROM Order o
>        LEFT JOIN FETCH o.user
>        LEFT JOIN FETCH o.items i
>        LEFT JOIN FETCH i.product
>        WHERE o.status = :status
>    """)
>    List<Order> findByStatusWithAll(@Param("status") OrderStatus status);
>}
>```
>
>SQL: **1 запрос**:
>```sql
>SELECT DISTINCT o.*, u.*, i.*, p.*
>FROM orders o
>LEFT JOIN users u ON u.id = o.user_id
>LEFT JOIN order_items i ON i.order_id = o.id
>LEFT JOIN products p ON p.id = i.product_id
>WHERE o.status = ?
>```
>
>**Ограничение:** можно FETCH только **одну** коллекцию. Здесь коллекция одна (`items`), всё ок. Если бы было две (`items` + `tags`) — декартово произведение, нужен другой подход.
>
>`DISTINCT` нужен, потому что JOIN по коллекции размножает строки родителя. В Hibernate 6+ можно добавить hint `hibernate.query.passDistinctThrough=false`, чтобы `DISTINCT` работал только на уровне Java, не попадая в SQL.
>
>**Способ 2 — @EntityGraph:**
>```java
>public interface OrderRepository extends JpaRepository<Order, Long> {
>
>    @EntityGraph(attributePaths = {"user", "items", "items.product"})
>    List<Order> findByStatus(OrderStatus status);
>}
>```
>
>SQL — аналогичный Способу 1, но без ручного JPQL. Работает с production-запросами (`findByStatus`, `findByStatusAndUserId` и т.п.) — не нужно дублировать каждый метод.
>
>**Способ 3 — @BatchSize / `default_batch_fetch_size`:**
>```java
>@Entity
>public class Order {
>    @OneToMany(mappedBy = "order")
>    @BatchSize(size = 50)
>    private List<OrderItem> items;
>}
>
>@Entity
>public class OrderItem {
>    @ManyToOne(fetch = FetchType.LAZY)
>    @BatchSize(size = 50)     // Или глобальный hibernate.default_batch_fetch_size
>    private Product product;
>}
>```
>
>SQL: **~4 запроса** (вместо 1 + N*M):
>```sql
>SELECT * FROM orders WHERE status = ?;                        -- 1
>SELECT * FROM users WHERE id IN (?, ?, ?, ..., ?);            -- 2
>SELECT * FROM order_items WHERE order_id IN (?, ?, ..., ?);   -- 3
>SELECT * FROM products WHERE id IN (?, ?, ..., ?);            -- 4
>```
>
>Плюсы: не нужно трогать JPQL, работает автоматически для всех запросов. Масштабируется: 1000 заказов = по-прежнему ~4 запроса.
>
>**Какой способ выбрать:**
>| Ситуация | Выбор |
>|----------|-------|
>| Один конкретный метод, одна коллекция | **JOIN FETCH** |
>| Нужно переиспользовать с разными фильтрами | **@EntityGraph** |
>| Несколько коллекций | **BatchSize** или разбиение на несколько запросов |
>| Глобальное поведение для всего приложения | `hibernate.default_batch_fetch_size=50` |
>
>**Для этого конкретного кейса лучший вариант — DTO-проекция, а не Entity:**
>```java
>@Query("""
>    SELECT new com.x.OrderDTO(o.id, u.name, p.name)
>    FROM Order o
>    JOIN o.user u
>    JOIN o.items i
>    JOIN i.product p
>    WHERE o.status = :status
>""")
>List<OrderDTO> findActiveOrderDTOs(@Param("status") OrderStatus status);
>```
>1 запрос, никаких Entity в памяти, никаких рисков N+1.

### Задача 6: MultipleBagFetchException

Код:
```java
@Query("""
    SELECT DISTINCT u FROM User u
    LEFT JOIN FETCH u.orders
    LEFT JOIN FETCH u.reviews
    WHERE u.id = :id
""")
Optional<User> findFullProfile(@Param("id") Long id);
```

В User: `List<Order> orders`, `List<Review> reviews`. При запуске получаем `MultipleBagFetchException: cannot simultaneously fetch multiple bags`. Объясните причину и исправьте **двумя способами**.

>[!note]- Решение
>**Причина:**
>
>`List` в Hibernate — это "bag" (мешок без порядка и гарантий уникальности). Одновременный FETCH двух bag'ов создаёт **неограниченное декартово произведение**: если у юзера 10 orders и 20 reviews, SQL вернёт 200 строк только чтобы собрать одного пользователя. Hibernate отказывается это делать, потому что:
>1. Результат квадратичен по размеру коллекций
>2. Невозможно однозначно "распаковать" строки обратно в две независимые коллекции
>
>Для **одного** bag — всё ок. Для двух `Set` — тоже работает (Set = уникальные элементы, дубли отбрасываются). Для двух `List` — MultipleBagFetchException.
>
>**Способ 1 — заменить List на Set:**
>```java
>@Entity
>public class User {
>    @OneToMany(mappedBy = "user")
>    private Set<Order> orders = new HashSet<>();
>
>    @OneToMany(mappedBy = "user")
>    private Set<Review> reviews = new HashSet<>();
>}
>```
>
>Теперь JPQL с двумя `JOIN FETCH` работает. SQL всё равно делает декартово произведение, но Hibernate дедуплицирует.
>
>**Цена:** декартово произведение. Если у юзера 100 orders и 50 reviews → 5000 строк с одного запроса, большая часть — дубли. На больших коллекциях это хуже, чем два раздельных запроса.
>
>**Требование:** нужен корректный `equals/hashCode` на `Order` и `Review` (по ID с защитой от null), иначе `HashSet` сломается.
>
>**Способ 2 — два запроса (правильно для больших коллекций):**
>```java
>public interface UserRepository extends JpaRepository<User, Long> {
>
>    @Query("SELECT u FROM User u LEFT JOIN FETCH u.orders WHERE u.id = :id")
>    Optional<User> findWithOrders(@Param("id") Long id);
>
>    @Query("SELECT u FROM User u LEFT JOIN FETCH u.reviews WHERE u.id = :id")
>    Optional<User> findWithReviews(@Param("id") Long id);
>}
>
>@Transactional(readOnly = true)
>public User getFullProfile(Long id) {
>    User user = userRepo.findWithOrders(id).orElseThrow();
>    userRepo.findWithReviews(id);  // Второй запрос — коллекция reviews добавится в ТОТ ЖЕ объект user в 1-м кэше
>    return user;
>}
>```
>
>Почему это работает: у `user` тот же ID → Hibernate видит его в 1-м кэше и добавляет инициализированную коллекцию `reviews` к уже существующему managed-объекту.
>
>SQL:
>```sql
>SELECT u.*, o.* FROM users u LEFT JOIN orders o ON o.user_id = u.id WHERE u.id = ?;
>SELECT u.*, r.* FROM users u LEFT JOIN reviews r ON r.user_id = u.id WHERE u.id = ?;
>```
>2 запроса по `orders_count + reviews_count` строк (не произведение).
>
>**Способ 3 — Hibernate 6+ (bonus):**
>Hibernate 6 поддерживает hint `hibernate.query.multi_bag.fetch` — но официальная рекомендация всё та же: либо `Set`, либо разбивайте на несколько запросов. Для больших коллекций разбиение всегда выигрывает.

---

## Блокировки

### Задача 7: Реализуйте списание средств с оптимистичной и пессимистичной блокировкой

Реализуйте метод `transfer(fromId, toId, amount)` — перевод между счетами. Два варианта:
- С `@Version` (оптимистичная)
- С `PESSIMISTIC_WRITE` (пессимистичная)

Обсудите, когда какой выбрать.

>[!note]- Решение
>**Сущность Account:**
>```java
>@Entity
>@Table(name = "accounts")
>@Getter @Setter
>public class Account {
>    @Id
>    @GeneratedValue(strategy = GenerationType.SEQUENCE)
>    private Long id;
>
>    @Column(nullable = false, precision = 19, scale = 2)
>    private BigDecimal balance;
>
>    @Version
>    private Long version;
>}
>```
>
>**Вариант 1 — Оптимистичная (@Version):**
>```java
>public interface AccountRepository extends JpaRepository<Account, Long> {}
>
>@Service
>@RequiredArgsConstructor
>public class TransferService {
>    private final AccountRepository repo;
>
>    @Retryable(
>        value = ObjectOptimisticLockingFailureException.class,
>        maxAttempts = 3,
>        backoff = @Backoff(delay = 50, multiplier = 2)
>    )
>    @Transactional
>    public void transfer(Long fromId, Long toId, BigDecimal amount) {
>        if (amount.signum() <= 0) {
>            throw new IllegalArgumentException("Amount must be positive");
>        }
>        // ВАЖНО: всегда в одном и том же порядке — защита от deadlock'а
>        Long firstId = Math.min(fromId, toId);
>        Long secondId = Math.max(fromId, toId);
>        Account first = repo.findById(firstId).orElseThrow();
>        Account second = repo.findById(secondId).orElseThrow();
>
>        Account from = fromId.equals(firstId) ? first : second;
>        Account to = fromId.equals(firstId) ? second : first;
>
>        if (from.getBalance().compareTo(amount) < 0) {
>            throw new InsufficientFundsException();
>        }
>        from.setBalance(from.getBalance().subtract(amount));
>        to.setBalance(to.getBalance().add(amount));
>        // При commit'е Hibernate сгенерирует:
>        // UPDATE accounts SET balance=?, version=? WHERE id=? AND version=?
>        // Если конкурент обновил — 0 rows affected → OptimisticLockException → @Retryable повторит
>    }
>}
>```
>
>**Вариант 2 — Пессимистичная (FOR UPDATE):**
>```java
>public interface AccountRepository extends JpaRepository<Account, Long> {
>
>    @Lock(LockModeType.PESSIMISTIC_WRITE)
>    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
>    @Query("SELECT a FROM Account a WHERE a.id = :id")
>    Optional<Account> findByIdForUpdate(@Param("id") Long id);
>}
>
>@Service
>@RequiredArgsConstructor
>public class TransferService {
>    private final AccountRepository repo;
>
>    @Transactional
>    public void transfer(Long fromId, Long toId, BigDecimal amount) {
>        if (amount.signum() <= 0) throw new IllegalArgumentException();
>
>        Long firstId = Math.min(fromId, toId);
>        Long secondId = Math.max(fromId, toId);
>        Account first = repo.findByIdForUpdate(firstId).orElseThrow();
>        Account second = repo.findByIdForUpdate(secondId).orElseThrow();
>
>        Account from = fromId.equals(firstId) ? first : second;
>        Account to = fromId.equals(firstId) ? second : first;
>
>        if (from.getBalance().compareTo(amount) < 0) {
>            throw new InsufficientFundsException();
>        }
>        from.setBalance(from.getBalance().subtract(amount));
>        to.setBalance(to.getBalance().add(amount));
>    }
>}
>```
>SQL:
>```sql
>SELECT * FROM accounts WHERE id = ? FOR UPDATE;   -- блокирует строку до commit'а
>```
>
>**Критичный момент — порядок блокировки:**
>
>Без сортировки по ID транзакция A блокирует 1→2, транзакция B блокирует 2→1 → **deadlock**. БД убивает одну, но на проде это значит потерю транзакции и алёрт. Всегда блокируйте в одном и том же порядке (по ID, по имени ресурса — главное, детерминированно).
>
>**Сравнение:**
>
>| Аспект | Оптимистичная | Пессимистичная |
>|--------|---------------|----------------|
>| Параллельность | Высокая (нет блокировок в БД) | Низкая (строки заблокированы) |
>| Накладные расходы | Минимальные | Row locks + контекст на серверной стороне БД |
>| Обработка конфликта | Retry всей транзакции | Ожидание на lock |
>| Risk of deadlock | Нет | Есть — нужна сортировка |
>| Работает в распределённой архитектуре | Да (версия едет с объектом) | Только в рамках одной БД |
>| Подходит для длинных операций | Плохо (конфликт в конце → всё заново) | Плохо (блокировка на всё время) |
>
>**Когда что:**
>- **Оптимистичная:** 99% веб-сценариев. Конфликты редкие, лучше сделать retry, чем платить блокировкой.
>- **Пессимистичная:** частые конкурирующие обновления одной и той же строки (hot spot): резерв товара на складе, списание с популярного кошелька, генерация уникальных номеров.
>
>**Для финансовых операций** типичный выбор — **пессимистичная + таймаут + sorted locking**. Дополнительная защита — idempotency key на уровне API, чтобы повторный клик по "перевести" не выполнил операцию дважды, даже если пользователь обновил страницу.

### Задача 8: Deadlock при параллельных обновлениях

Два сервиса одновременно вызывают:
- Сервис A: `updateOrderAndUser(orderId=1, userId=10)`
- Сервис B: `updateUserAndOrder(userId=10, orderId=1)`

```java
@Transactional
public void updateOrderAndUser(Long orderId, Long userId) {
    Order o = orderRepo.findByIdForUpdate(orderId);
    Thread.sleep(100);  // Имитация работы
    User u = userRepo.findByIdForUpdate(userId);
    o.setStatus(COMPLETED);
    u.incrementCompletedOrders();
}

@Transactional
public void updateUserAndOrder(Long userId, Long orderId) {
    User u = userRepo.findByIdForUpdate(userId);
    Thread.sleep(100);
    Order o = orderRepo.findByIdForUpdate(orderId);
    u.incrementCompletedOrders();
    o.setStatus(COMPLETED);
}
```

Через 100 мс получаем `CannotAcquireLockException`. Объясните причину, предложите 2 решения.

>[!note]- Решение
>**Причина — классический deadlock:**
>```
>t=0ms    A: FOR UPDATE orders WHERE id=1  → acquired  (lock на order 1)
>t=0ms    B: FOR UPDATE users  WHERE id=10 → acquired  (lock на user  10)
>t=100ms  A: FOR UPDATE users  WHERE id=10 → WAIT      (держит order 1, ждёт user 10)
>t=100ms  B: FOR UPDATE orders WHERE id=1  → WAIT      (держит user 10, ждёт order 1)
>```
>Каждая транзакция держит один ресурс и ждёт второй, который держит конкурент. БД детектит цикл и убивает одну из транзакций с `deadlock detected`.
>
>**Решение 1 — детерминированный порядок блокировок:**
>
>Всегда блокировать ресурсы в одном и том же порядке — например, по типу сущности (сначала User, потом Order) или по глобальному ключу (`entityType:id`).
>
>```java
>@Transactional
>public void update(Long userId, Long orderId) {
>    // Всегда: сначала User, потом Order
>    User u = userRepo.findByIdForUpdate(userId);
>    Order o = orderRepo.findByIdForUpdate(orderId);
>    o.setStatus(COMPLETED);
>    u.incrementCompletedOrders();
>}
>```
>
>Обе точки входа (`updateOrderAndUser` и `updateUserAndOrder`) объединяются в один метод, либо оба вызывают общий helper, который всегда берёт локи в фиксированном порядке.
>
>Для блокировки **списка** объектов — сортируйте по ID перед блокировкой:
>```java
>List<Long> sorted = ids.stream().sorted().toList();
>for (Long id : sorted) {
>    repo.findByIdForUpdate(id);
>}
>```
>
>**Решение 2 — оптимистичная блокировка + retry:**
>
>Полностью убрать `FOR UPDATE`, использовать `@Version` и повторять транзакцию при конфликте.
>
>```java
>@Entity
>public class Order {
>    @Version Long version;
>}
>
>@Retryable(
>    value = {ObjectOptimisticLockingFailureException.class, CannotAcquireLockException.class},
>    maxAttempts = 5,
>    backoff = @Backoff(delay = 50, multiplier = 2, random = true)
>)
>@Transactional
>public void update(Long userId, Long orderId) {
>    Order o = orderRepo.findById(orderId).orElseThrow();
>    User u = userRepo.findById(userId).orElseThrow();
>    o.setStatus(COMPLETED);
>    u.incrementCompletedOrders();
>}
>```
>
>Конфликт обнаружится при commit'е, а не во время блокировки. `@Retryable` с jitter (`random = true`) предотвращает одновременный retry обеими транзакциями.
>
>**Решение 3 — lock timeout + fail fast:**
>
>Если deadlock'ов мало, можно просто поставить короткий lock timeout, чтобы ждать максимум N мс и сразу падать:
>```java
>@Lock(LockModeType.PESSIMISTIC_WRITE)
>@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "500"))
>Optional<Order> findByIdForUpdate(Long id);
>```
>В PostgreSQL — глобально: `SET lock_timeout = '500ms'` на уровне сессии.
>
>**Плохое "решение":** `@Transactional(timeout = 1)` — это таймаут всей транзакции, он не предотвращает deadlock, а только делает провал чуть раньше. Используйте lock_timeout.
>
>**Правило:** если вы ставите `FOR UPDATE`, всегда задокументируйте порядок захвата локов в именовании helper-методов или комментариях к классу.

---

## Продвинутые возможности

### Задача 9: Реализуйте Soft Delete через @SQLRestriction / @SQLDelete

Нужно:
- Не удалять `User` физически, а ставить `deleted_at`
- Все стандартные SELECT'ы должны игнорировать удалённых
- Уникальность email должна работать: можно зарегистрировать нового пользователя с email удалённого
- Админка должна уметь видеть удалённых

>[!note]- Решение
>**Сущность:**
>```java
>@Entity
>@Table(name = "users")
>@Getter @Setter
>@SQLRestriction("deleted_at IS NULL")
>@SQLDelete(sql = "UPDATE users SET deleted_at = now() WHERE id = ?")
>public class User {
>    @Id
>    @GeneratedValue(strategy = GenerationType.SEQUENCE)
>    private Long id;
>
>    @Column(nullable = false)
>    private String email;
>
>    @Column(name = "deleted_at")
>    private Instant deletedAt;
>}
>```
>
>**Миграция (Flyway):**
>```sql
>CREATE TABLE users (
>    id BIGINT PRIMARY KEY,
>    email VARCHAR(255) NOT NULL,
>    deleted_at TIMESTAMP
>);
>
>-- Частичный уникальный индекс — ключ к тому, чтобы можно было
>-- "удалить" юзера с email=alice@x.com и зарегистрировать нового
>CREATE UNIQUE INDEX idx_users_email_active
>    ON users(email)
>    WHERE deleted_at IS NULL;
>
>-- Для быстрого запроса активных
>CREATE INDEX idx_users_active
>    ON users(id)
>    WHERE deleted_at IS NULL;
>```
>
>**Обычное использование:**
>```java
>userRepo.findAll();                    // SELECT ... WHERE deleted_at IS NULL
>userRepo.findByEmail("alice@x.com");   // То же
>userRepo.delete(user);                 // UPDATE users SET deleted_at = now() WHERE id = ?
>```
>
>**Админка — увидеть всех, включая удалённых:**
>
>`@SQLRestriction` нельзя "отключить" через JPA — он зашит в метадату. Варианты:
>
>**Вариант A — native запросы:**
>```java
>public interface UserAdminRepository extends JpaRepository<User, Long> {
>    @Query(value = "SELECT * FROM users WHERE email = :email", nativeQuery = true)
>    List<User> findAllByEmailIncludingDeleted(@Param("email") String email);
>}
>```
>Native SQL не подчиняется `@SQLRestriction`, но результат всё равно будет содержать `deleted_at`.
>
>**Вариант B — использовать `@Filter` вместо `@SQLRestriction`:**
>```java
>@Entity
>@Table(name = "users")
>@FilterDef(name = "activeOnly")
>@Filter(name = "activeOnly", condition = "deleted_at IS NULL")
>public class User { ... }
>
>@Aspect
>@Component
>public class SoftDeleteAspect {
>    @PersistenceContext private EntityManager em;
>
>    @Before("@annotation(ActiveOnly) || @within(ActiveOnly)")
>    public void enable() {
>        em.unwrap(Session.class).enableFilter("activeOnly");
>    }
>}
>```
>Фильтр можно включать/выключать per-session — админка его просто не включает.
>
>**Подводный камень 1 — связи:**
>
>```java
>@OneToOne
>private Profile profile;  // ← profile.deleted_at не проверится автоматически!
>```
>
>При `LEFT JOIN` на связанную сущность `@SQLRestriction` не применяется к связанной таблице в native запросах. Hibernate 6 лучше это поддерживает, но не идеально. Ставьте `@SQLRestriction` на обе сущности — они будут фильтроваться независимо при загрузке.
>
>**Подводный камень 2 — каскадное "удаление":**
>
>`@SQLDelete` применится к родителю. Но дочерние сущности Hibernate попытается удалить нормальным DELETE — если они тоже soft-delete, нужны `@SQLDelete` на каждой.
>
>**Подводный камень 3 — `orphanRemoval`:**
>
>`orphanRemoval = true` срабатывает через обычный DELETE, bypass `@SQLDelete`. Проверяйте генерируемый SQL в логах.
>
>**Альтернатива для больших систем** — отдельная архивная таблица `users_archive` + триггер вместо soft-delete. Это избавляет от половины описанных проблем.

### Задача 10: Добавьте аудит сущности через Spring Data + Envers

Нужно для сущности `Document`:
- Автоматически заполнять `createdAt`, `createdBy`, `updatedAt`, `updatedBy`
- Хранить полную историю изменений с возможностью посмотреть состояние на любой момент
- `createdBy` / `updatedBy` — текущий пользователь из `SecurityContext`

>[!note]- Решение
>**Уровень 1 — базовые поля через Spring Data JPA Auditing:**
>
>```java
>@Configuration
>@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
>public class JpaAuditingConfig {
>
>    @Bean
>    public AuditorAware<String> auditorProvider() {
>        return () -> Optional.ofNullable(SecurityContextHolder.getContext())
>            .map(SecurityContext::getAuthentication)
>            .filter(Authentication::isAuthenticated)
>            .map(Authentication::getName);
>    }
>}
>
>@MappedSuperclass
>@EntityListeners(AuditingEntityListener.class)
>@Getter @Setter
>public abstract class Auditable {
>
>    @CreatedDate
>    @Column(name = "created_at", nullable = false, updatable = false)
>    private Instant createdAt;
>
>    @CreatedBy
>    @Column(name = "created_by", nullable = false, updatable = false)
>    private String createdBy;
>
>    @LastModifiedDate
>    @Column(name = "updated_at", nullable = false)
>    private Instant updatedAt;
>
>    @LastModifiedBy
>    @Column(name = "updated_by", nullable = false)
>    private String updatedBy;
>}
>
>@Entity
>@Table(name = "documents")
>@Audited                                // Envers → полная история
>@Getter @Setter
>public class Document extends Auditable {
>    @Id
>    @GeneratedValue(strategy = GenerationType.SEQUENCE)
>    private Long id;
>
>    @Column(nullable = false)
>    private String title;
>
>    @Column(columnDefinition = "text")
>    private String content;
>
>    @Version
>    private Long version;
>}
>```
>
>**Уровень 2 — история через Envers:**
>
>```xml
><dependency>
>  <groupId>org.hibernate.orm</groupId>
>  <artifactId>hibernate-envers</artifactId>
></dependency>
>```
>
>Настройки:
>```yaml
>spring.jpa.properties.org.hibernate.envers:
>  audit_table_suffix: _aud          # documents_aud
>  revision_field_name: rev
>  revision_type_field_name: revtype
>  store_data_at_delete: true        # Хранить снимок при удалении
>```
>
>Envers автоматически создаст таблицы `documents_aud` и `revinfo`. В `documents_aud` будет одна строка на каждую ревизию сущности с колонкой `revtype` (0 = INSERT, 1 = UPDATE, 2 = DELETE).
>
>**Кастомная ревизия — хранить, кто сделал изменение, на уровне всей транзакции:**
>
>```java
>@Entity
>@RevisionEntity(AuditRevisionListener.class)
>@Table(name = "revinfo")
>@Getter @Setter
>public class AuditRevision {
>    @Id
>    @GeneratedValue(strategy = GenerationType.SEQUENCE)
>    @RevisionNumber
>    private Integer id;
>
>    @RevisionTimestamp
>    private long timestamp;
>
>    private String username;
>
>    @Enumerated(EnumType.STRING)
>    private ChangeSource source;       // API, IMPORT, MIGRATION, ADMIN
>}
>
>public class AuditRevisionListener implements RevisionListener {
>    @Override
>    public void newRevision(Object revisionEntity) {
>        AuditRevision rev = (AuditRevision) revisionEntity;
>        rev.setUsername(SecurityContextHolder.getContext()
>            .getAuthentication().getName());
>        rev.setSource(RequestContextHolder.currentChangeSource());
>    }
>}
>```
>
>**Использование — читать историю:**
>
>```java
>@Service
>@RequiredArgsConstructor
>public class DocumentHistoryService {
>    private final EntityManager em;
>
>    public List<DocumentRevisionDTO> getHistory(Long documentId) {
>        AuditReader reader = AuditReaderFactory.get(em);
>
>        return reader.createQuery()
>            .forRevisionsOfEntity(Document.class, false, true)
>            .add(AuditEntity.id().eq(documentId))
>            .addOrder(AuditEntity.revisionNumber().asc())
>            .getResultList()
>            .stream()
>            .map(row -> {
>                Object[] arr = (Object[]) row;
>                Document doc = (Document) arr[0];
>                AuditRevision rev = (AuditRevision) arr[1];
>                RevisionType type = (RevisionType) arr[2];
>                return new DocumentRevisionDTO(
>                    doc, rev.getId(), rev.getUsername(),
>                    Instant.ofEpochMilli(rev.getTimestamp()), type
>                );
>            })
>            .toList();
>    }
>
>    public Document getAtRevision(Long documentId, int revision) {
>        return AuditReaderFactory.get(em)
>            .find(Document.class, documentId, revision);
>    }
>
>    public Document getAtTime(Long documentId, Instant time) {
>        AuditReader reader = AuditReaderFactory.get(em);
>        Number rev = reader.getRevisionNumberForDate(Date.from(time));
>        return reader.find(Document.class, documentId, rev);
>    }
>}
>```
>
>**Что учесть на проде:**
>- `documents_aud` растёт бесконечно — нужна retention policy (архивация ревизий старше года)
>- Каждый UPDATE — два INSERT'а (в `_aud` и `revinfo`), ~30% оверхед на записи
>- Envers плохо дружит с bulk UPDATE (`@Modifying`) — они обходят listener'ы → нет записи в `_aud`. Для bulk-операций делайте audit вручную или не используйте bulk
>- `@NotAudited` на полях, которые не должны попадать в историю (`password`, `passwordHash`)

### Задача 11: Добавьте вычисляемые поля через @Formula

В `Order` нужны поля:
- `itemCount` — количество OrderItem
- `totalAmount` — сумма `price * quantity`
- `hasDiscount` — boolean, есть ли хотя бы один item со скидкой

Без денормализации, без изменения схемы. Напишите и обсудите трейдоффы.

>[!note]- Решение
>```java
>@Entity
>@Table(name = "orders")
>@Getter @Setter
>public class Order {
>    @Id
>    @GeneratedValue(strategy = GenerationType.SEQUENCE)
>    private Long id;
>
>    @Formula("(SELECT COUNT(*) FROM order_items i WHERE i.order_id = id)")
>    private int itemCount;
>
>    @Formula("""
>        (SELECT COALESCE(SUM(i.price * i.quantity), 0)
>         FROM order_items i
>         WHERE i.order_id = id)
>    """)
>    private BigDecimal totalAmount;
>
>    @Formula("""
>        (SELECT CASE WHEN EXISTS (
>            SELECT 1 FROM order_items i
>            WHERE i.order_id = id AND i.discount_percent > 0
>         ) THEN true ELSE false END)
>    """)
>    private boolean hasDiscount;
>
>    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
>    private List<OrderItem> items;
>}
>```
>
>**Как это работает:**
>
>`@Formula` — это Hibernate-only аннотация. При каждом SELECT Order Hibernate встраивает подзапрос в сгенерированный SQL:
>```sql
>SELECT o.id,
>       (SELECT COUNT(*) FROM order_items i WHERE i.order_id = o.id) AS item_count,
>       (SELECT COALESCE(SUM(i.price * i.quantity), 0) FROM order_items i WHERE i.order_id = o.id) AS total_amount,
>       ...
>FROM orders o
>WHERE o.id = ?
>```
>
>**Ключевые моменты:**
>1. Поле **только на чтение** — `@Formula` не маппится в INSERT/UPDATE
>2. Нельзя ссылаться на другие `@Formula` — только на колонки
>3. Внутри выражения — сырой SQL (не JPQL!) → завязка на конкретную БД
>4. `id` внутри подзапроса — колонка текущей сущности, Hibernate сам подставит alias
>
>**Tradeoffs:**
>
>| Плюсы | Минусы |
>|-------|--------|
>| Не меняем схему | Подзапрос при **каждом** SELECT — медленно на больших объёмах |
>| Не нужно поддерживать денормализованные колонки | Native SQL → привязка к диалекту |
>| Всегда актуальные значения | Нельзя использовать в WHERE без native запроса |
>| Нет N+1 | `@Formula` грузится всегда (как EAGER) — нельзя сделать LAZY |
>
>**Когда использовать `@Formula`:**
>- Справочные/редко читаемые сущности, где важнее простота, чем микросекунды
>- Поля нужны в detail-view, где и так SELECT по одному id
>
>**Когда НЕ использовать:**
>- Горячие списочные запросы (`findAll` на большой таблице) → подзапрос * N записей = катастрофа
>- Поля нужны для сортировки/фильтрации → лучше денормализация
>
>**Альтернатива — денормализованные колонки + обновление в триггере/приложенческом коде:**
>```java
>@Entity
>public class Order {
>    @Column(name = "item_count")
>    private int itemCount;
>
>    @Column(name = "total_amount")
>    private BigDecimal totalAmount;
>
>    public void addItem(OrderItem item) {
>        items.add(item);
>        item.setOrder(this);
>        itemCount++;
>        totalAmount = totalAmount.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
>    }
>}
>```
>Дороже в поддержке, но константное время на SELECT и работает в WHERE/ORDER BY.
>
>**Компромисс — Materialized View:**
>```sql
>CREATE MATERIALIZED VIEW order_stats AS
>SELECT o.id, COUNT(i.*) AS item_count, COALESCE(SUM(i.price * i.quantity), 0) AS total_amount
>FROM orders o LEFT JOIN order_items i ON i.order_id = o.id
>GROUP BY o.id;
>
>REFRESH MATERIALIZED VIEW CONCURRENTLY order_stats;
>```
>Маппится как отдельная read-only сущность, свежесть контролируется расписанием `REFRESH`.

### Задача 12: Bulk-вставка 1 млн записей через StatelessSession

Нужно импортировать 1 000 000 `Product` из CSV. Требования:
- Не должно падать с OOM
- Должно завершиться за разумное время (<5 минут на типичном сервере)
- Если запись невалидна — пропустить, продолжить

Реализуйте три варианта, сравните.

>[!note]- Решение
>**Вариант 1 — наивный (падает с OOM):**
>```java
>@Transactional
>public void importProducts(Path csvPath) throws IOException {
>    try (var lines = Files.lines(csvPath)) {
>        lines.skip(1).forEach(line -> {
>            Product p = parse(line);
>            repo.save(p);
>        });
>    }
>}
>```
>Все 1 млн объектов копятся в 1-м кэше → OOM на ~300-500k. Даже если не упадёт — каждый `save()` триггерит dirty check всего кэша, скорость деградирует квадратично.
>
>**Вариант 2 — batch insert с flush/clear:**
>```java
>@PersistenceContext
>private EntityManager em;
>
>@Transactional
>public void importProducts(Path csvPath) throws IOException {
>    int batchSize = 50;
>    int i = 0;
>    try (var lines = Files.lines(csvPath)) {
>        for (var it = lines.skip(1).iterator(); it.hasNext(); ) {
>            String line = it.next();
>            try {
>                Product p = parse(line);
>                em.persist(p);
>                if (++i % batchSize == 0) {
>                    em.flush();
>                    em.clear();
>                }
>            } catch (Exception e) {
>                log.warn("Skipping line {}: {}", i, e.getMessage());
>            }
>        }
>        em.flush();
>        em.clear();
>    }
>}
>```
>
>Настройки в `application.yml`:
>```yaml
>spring.jpa.properties.hibernate:
>  jdbc.batch_size: 50             # Должен совпадать с batchSize в коде
>  order_inserts: true             # Группировать INSERT'ы по таблицам
>  order_updates: true
>  jdbc.batch_versioned_data: true # Для @Version
>
>spring.datasource.hikari:
>  # Важно для PostgreSQL: rewriteBatchedInserts → один INSERT со всеми VALUES
>  data-source-properties:
>    reWriteBatchedInserts: true
>```
>
>**Важные моменты:**
>- `batchSize` в коде и `hibernate.jdbc.batch_size` **должны совпадать** — иначе flush отправит несколько batch'ей
>- Для `GenerationType.IDENTITY` **batch insert отключается** Hibernate'ом! Используйте `SEQUENCE` с `allocationSize = 50`
>- `try/catch` внутри цикла ловит ошибки парсинга, но если упадёт сам `em.persist` — нужно откатывать только текущий batch, что сложно в одной транзакции → лучше разделить на много мелких транзакций (см. Вариант 3)
>
>**Скорость:** ~1-2 минуты на 1 млн записей на обычном ноуте. Память константная (~200 МБ).
>
>**Вариант 3 — StatelessSession + chunked transactions:**
>```java
>@Autowired
>private EntityManagerFactory emf;
>
>public void importProducts(Path csvPath) throws IOException {
>    SessionFactory sf = emf.unwrap(SessionFactory.class);
>    int chunkSize = 10_000;
>    List<Product> chunk = new ArrayList<>(chunkSize);
>
>    try (var lines = Files.lines(csvPath)) {
>        for (var it = lines.skip(1).iterator(); it.hasNext(); ) {
>            try {
>                chunk.add(parse(it.next()));
>            } catch (Exception e) {
>                log.warn("Skip: {}", e.getMessage());
>            }
>            if (chunk.size() >= chunkSize) {
>                insertChunk(sf, chunk);
>                chunk.clear();
>            }
>        }
>        if (!chunk.isEmpty()) {
>            insertChunk(sf, chunk);
>        }
>    }
>}
>
>private void insertChunk(SessionFactory sf, List<Product> chunk) {
>    try (StatelessSession ss = sf.openStatelessSession()) {
>        Transaction tx = ss.beginTransaction();
>        for (Product p : chunk) {
>            ss.insert(p);
>        }
>        tx.commit();
>    }
>}
>```
>
>**Плюсы StatelessSession:**
>- Нет 1-го кэша, нет dirty checking → минимальный оверхед
>- Маленькие чанковые транзакции → ошибка в одном чанке не убивает весь импорт
>- Можно параллелить разные чанки по разным потокам
>
>**Минусы:**
>- Не работают `@PrePersist`, Envers, каскады
>- Нет автоинкремента Spring Data Auditing → `createdAt` надо ставить вручную
>- Связи должны быть заранее резолвлены (можно хранить только FK-ID)
>
>**Вариант 4 (бонус) — PostgreSQL `COPY`:**
>Для реально больших импортов (десятки миллионов):
>```java
>@Autowired DataSource ds;
>
>public void bulkCopy(Path csvPath) throws Exception {
>    try (Connection conn = ds.getConnection().unwrap(PgConnection.class);
>         Reader reader = Files.newBufferedReader(csvPath)) {
>        CopyManager cm = conn.getCopyAPI();
>        cm.copyIn("COPY products (sku, name, price) FROM STDIN WITH CSV HEADER", reader);
>    }
>}
>```
>**Скорость:** десятки миллионов строк в минуту. Но: никакой бизнес-логики, прямая заливка в таблицу.
>
>**Сравнение:**
>
>| Вариант | 1 млн записей | Память | Hooks работают | Обработка ошибок |
>|---------|---------------|--------|---------------|------------------|
>| Наивный save | OOM / ∞ | ∞ | Да | Плохая |
>| batch + flush/clear | ~1-2 мин | ~200 МБ | Да | Сложная |
>| StatelessSession | ~30-60 сек | ~100 МБ | **Нет** | Хорошая (по чанкам) |
>| PG COPY | ~5-10 сек | минимум | **Нет** | По факту |
>
>**Правило:** поднимайтесь по лестнице только когда предыдущий уровень упёрся. Большинству задач хватает Варианта 2. StatelessSession — когда важна скорость и нет зависимости от hooks. COPY — когда нужна реально экстремальная производительность и можно обойти приложенческую логику.

---

## Кэширование

### Задача 13: Настройте L2-кэш для справочника и проверьте hit rate

Есть сущность `Country` — справочник стран (~250 записей, меняется раз в год). Она используется в каждом запросе пользователя: при загрузке `User` Hibernate подтягивает `user.getCountry()`. На проде видно 250 одинаковых SELECT'ов в секунду к таблице `countries`.

Нужно:
- Настроить L2-кэш так, чтобы повторные обращения не шли в БД
- Выбрать правильную `CacheConcurrencyStrategy`
- Продемонстрировать проверку hit rate через Statistics API
- Объяснить, что произойдёт при обновлении справочника

>[!note]- Решение
>**Шаг 1 — зависимости (Caffeine через JCache):**
>```xml
><dependency>
>    <groupId>org.hibernate.orm</groupId>
>    <artifactId>hibernate-jcache</artifactId>
></dependency>
><dependency>
>    <groupId>com.github.ben-manes.caffeine</groupId>
>    <artifactId>caffeine</artifactId>
></dependency>
><dependency>
>    <groupId>com.github.ben-manes.caffeine</groupId>
>    <artifactId>jcache</artifactId>
></dependency>
>```
>
>**Шаг 2 — конфигурация:**
>```yaml
>spring.jpa.properties.hibernate:
>  cache:
>    use_second_level_cache: true
>    region.factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
>  javax.cache.provider: com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider
>  generate_statistics: true        # Для проверки hit rate
>```
>
>**Шаг 3 — сущность:**
>```java
>@Entity
>@Table(name = "countries")
>@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)  // Справочник: никогда не меняется через приложение
>@Immutable                                           // Hibernate не будет делать dirty check
>@Getter
>public class Country {
>    @Id
>    @Column(length = 2)
>    private String code;           // "RU", "US"
>
>    @Column(nullable = false)
>    private String name;
>
>    @Column(name = "phone_code")
>    private String phoneCode;
>}
>```
>
>**Почему `READ_ONLY` + `@Immutable`:**
>- `READ_ONLY` — самая быстрая стратегия, нет накладных на soft-locks
>- `@Immutable` — Hibernate знает, что dirty check не нужен, снимок (snapshot) не создаётся
>- Попытка `em.merge(country)` бросит исключение — это защита от случайных правок
>
>**Шаг 4 — связь в User:**
>```java
>@Entity
>public class User {
>    @ManyToOne(fetch = FetchType.LAZY)
>    @JoinColumn(name = "country_code")
>    private Country country;       // При обращении → L2 cache hit, не SELECT
>}
>```
>
>**Шаг 5 — проверка hit rate:**
>```java
>@RestController
>@RequiredArgsConstructor
>public class CacheStatsController {
>    private final EntityManagerFactory emf;
>
>    @GetMapping("/admin/cache-stats")
>    public Map<String, Object> stats() {
>        Statistics s = emf.unwrap(SessionFactory.class).getStatistics();
>        long hits = s.getSecondLevelCacheHitCount();
>        long misses = s.getSecondLevelCacheMissCount();
>        long puts = s.getSecondLevelCachePutCount();
>        double hitRate = (hits + misses) == 0 ? 0 : (double) hits / (hits + misses);
>
>        return Map.of(
>            "l2_hits", hits,
>            "l2_misses", misses,
>            "l2_puts", puts,
>            "l2_hit_rate", String.format("%.2f%%", hitRate * 100),
>            "queries_executed", s.getQueryExecutionCount()
>        );
>    }
>}
>```
>
>Ожидание: после прогрева (первые 250 SELECT'ов заполнят кэш) hit rate → ~100%, `queries_executed` для countries → 0.
>
>**Что при обновлении справочника:**
>
>С `READ_ONLY` обновление через Hibernate невозможно (исключение). Если справочник меняется раз в год:
>
>**Вариант A — evict + перезагрузка:**
>```java
>@Service
>@RequiredArgsConstructor
>public class CountryCacheService {
>    private final EntityManagerFactory emf;
>
>    public void refreshCountries() {
>        emf.unwrap(SessionFactory.class)
>           .getCache()
>           .evictEntityData(Country.class);  // Сброс L2 для Country
>        // Следующие обращения промахнутся → SELECT → снова в кэш
>    }
>}
>```
>
>**Вариант B — если правки через приложение нужны регулярно:**
>Сменить стратегию на `NONSTRICT_READ_WRITE`, убрать `@Immutable`. Потеря: чуть больше оверхеда на каждую запись, но правки через `merge()` работают, кэш обновляется eventual-consistent.
>
>**Подводный камень — кластер:**
>Caffeine — in-process кэш. На 3 инстансах обновление на одном не инвалидирует кэш других. Решение: Hazelcast/Infinispan как провайдер L2, или ручная инвалидация через pub/sub (Redis, Kafka).

### Задача 14: Разберитесь, почему Query Cache не помогает

Код:

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    @Query("SELECT o FROM Order o WHERE o.status = :status")
    List<Order> findByStatusCached(@Param("status") OrderStatus status);
}
```

Настройки:
```yaml
spring.jpa.properties.hibernate:
  cache.use_second_level_cache: true
  cache.use_query_cache: true
  cache.region.factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
```

В логах видно, что каждый вызов `findByStatusCached(ACTIVE)` всё равно делает SELECT к БД. Hit rate Query Cache — 0%. Объясните причину и предложите, как исправить или чем заменить.

>[!note]- Решение
>**Причина 1 — нет `@Cache` на сущности Order:**
>
>Query Cache хранит **только ID** сущностей, не сами объекты. При попадании он идёт за данными в L2-кэш. Если `Order` не аннотирован `@Cache` — нет L2-кэша → для каждого ID делается отдельный SELECT → **хуже, чем без query cache** (N запросов по ID вместо одного WHERE status = ?).
>
>```java
>@Entity
>@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)  // Обязательно!
>public class Order { ... }
>```
>
>**Причина 2 — инвалидация при любом INSERT/UPDATE:**
>
>Query Cache инвалидируется при **любом** изменении таблицы `orders`. Если заказы создаются часто (каждую секунду), query cache сбрасывается раньше, чем успеет дать hit.
>
>```
>t=0s    findByStatusCached(ACTIVE) → MISS → SELECT → кэш
>t=0.5s  new Order saved → orders table changed → query cache invalidated
>t=1s    findByStatusCached(ACTIVE) → MISS → SELECT → кэш (снова!)
>```
>
>Это **главная** причина, почему Query Cache почти никогда не окупается на транзакционных таблицах.
>
>**Причина 3 — разные параметры = разные cache-ключи:**
>
>`findByStatusCached(ACTIVE)` и `findByStatusCached(PENDING)` — разные записи в query cache. Если параметры часто меняются, кэш раздувается без пользы.
>
>**Когда Query Cache работает:**
>- Таблица **редко меняется** (справочники, конфигурация)
>- Запрос вызывается **часто** с одними и теми же параметрами
>- Сущность уже в L2-кэш
>
>**Исправление для текущего кейса — Query Cache не подходит. Альтернативы:**
>
>**Вариант 1 — application-level кэш (Spring @Cacheable):**
>```java
>@Service
>@RequiredArgsConstructor
>public class OrderService {
>    private final OrderRepository repo;
>
>    @Cacheable(value = "activeOrders", key = "#status")
>    @Transactional(readOnly = true)
>    public List<OrderDTO> getByStatus(OrderStatus status) {
>        return repo.findByStatus(status).stream()
>            .map(OrderDTO::from)
>            .toList();
>    }
>
>    @CacheEvict(value = "activeOrders", allEntries = true)
>    @Transactional
>    public Order createOrder(CreateOrderRequest req) {
>        // ...
>    }
>}
>```
>
>Плюсы: вы контролируете, когда инвалидировать. Минусы: инвалидация вручную, можно забыть.
>
>**Вариант 2 — кэш на уровне DTO с TTL:**
>```java
>@Bean
>public CacheManager cacheManager() {
>    CaffeineCacheManager cm = new CaffeineCacheManager();
>    cm.setCaffeine(Caffeine.newBuilder()
>        .expireAfterWrite(Duration.ofSeconds(30))    // Данные "стареют" на 30 сек — приемлемо?
>        .maximumSize(100));
>    return cm;
>}
>```
>
>Стоимость — eventual consistency: данные могут быть устаревшими до 30 секунд. Для дашбордов и списков — обычно приемлемо.
>
>**Вариант 3 — убрать Query Cache, оставить только L2:**
>```yaml
>spring.jpa.properties.hibernate.cache.use_query_cache: false
>```
>L2-кэш на `Order` всё ещё помогает: `em.find(Order.class, id)` попадёт в кэш. Но списочные запросы (`findByStatus`) всегда идут в БД — это нормально, БД умеет быстро фильтровать по индексу.
>
>**Правило:** Query Cache — ложный друг. Включайте только для справочных таблиц, которые уже в L2-кэше и меняются раз в сутки или реже. Для всего остального — application-level `@Cacheable`.

---

## HQL, Criteria и Native Query

### Задача 15: Реализуйте динамический фильтр через Specifications

REST-эндпоинт для поиска заказов с опциональными фильтрами:

```
GET /api/orders?status=ACTIVE&dateFrom=2024-01-01&dateTo=2024-12-31&minAmount=1000&userName=Alice
```

Все параметры опциональные. Код, написанный джуниором:

```java
@Transactional(readOnly = true)
public List<OrderDTO> searchOrders(OrderFilter filter) {
    StringBuilder jpql = new StringBuilder("SELECT o FROM Order o JOIN o.user u WHERE 1=1");
    Map<String, Object> params = new HashMap<>();

    if (filter.getStatus() != null) {
        jpql.append(" AND o.status = :status");
        params.put("status", filter.getStatus());
    }
    if (filter.getDateFrom() != null) {
        jpql.append(" AND o.createdAt >= :dateFrom");
        params.put("dateFrom", filter.getDateFrom());
    }
    // ... ещё 10 таких блоков

    TypedQuery<Order> query = em.createQuery(jpql.toString(), Order.class);
    params.forEach(query::setParameter);
    return query.getResultList().stream().map(OrderDTO::from).toList();
}
```

Проблемы: SQL-injection невозможна (параметризация есть), но код нечитаемый, нетестируемый, легко ошибиться в конкатенации. Перепишите на **Spring Data Specifications** и **QueryDSL** (два варианта).

>[!note]- Решение
>**Вариант 1 — Spring Data JPA Specifications:**
>
>Репозиторий:
>```java
>public interface OrderRepository extends JpaRepository<Order, Long>,
>                                         JpaSpecificationExecutor<Order> {
>}
>```
>
>Спецификации:
>```java
>public class OrderSpecs {
>
>    public static Specification<Order> hasStatus(OrderStatus status) {
>        return (root, query, cb) -> cb.equal(root.get("status"), status);
>    }
>
>    public static Specification<Order> createdAfter(LocalDate date) {
>        return (root, query, cb) -> cb.greaterThanOrEqualTo(
>            root.get("createdAt"), date.atStartOfDay());
>    }
>
>    public static Specification<Order> createdBefore(LocalDate date) {
>        return (root, query, cb) -> cb.lessThan(
>            root.get("createdAt"), date.plusDays(1).atStartOfDay());
>    }
>
>    public static Specification<Order> totalAmountGreaterThan(BigDecimal min) {
>        return (root, query, cb) -> cb.greaterThanOrEqualTo(
>            root.get("totalAmount"), min);
>    }
>
>    public static Specification<Order> userNameContains(String name) {
>        return (root, query, cb) -> {
>            Join<Order, User> user = root.join("user", JoinType.INNER);
>            return cb.like(cb.lower(user.get("name")),
>                "%" + name.toLowerCase() + "%");
>        };
>    }
>}
>```
>
>Сервис:
>```java
>@Service
>@RequiredArgsConstructor
>public class OrderSearchService {
>    private final OrderRepository repo;
>
>    @Transactional(readOnly = true)
>    public List<OrderDTO> search(OrderFilter f) {
>        Specification<Order> spec = Specification.where(null);   // "пустой" фильтр
>
>        if (f.getStatus() != null) {
>            spec = spec.and(OrderSpecs.hasStatus(f.getStatus()));
>        }
>        if (f.getDateFrom() != null) {
>            spec = spec.and(OrderSpecs.createdAfter(f.getDateFrom()));
>        }
>        if (f.getDateTo() != null) {
>            spec = spec.and(OrderSpecs.createdBefore(f.getDateTo()));
>        }
>        if (f.getMinAmount() != null) {
>            spec = spec.and(OrderSpecs.totalAmountGreaterThan(f.getMinAmount()));
>        }
>        if (f.getUserName() != null) {
>            spec = spec.and(OrderSpecs.userNameContains(f.getUserName()));
>        }
>
>        return repo.findAll(spec).stream()
>            .map(OrderDTO::from)
>            .toList();
>    }
>}
>```
>
>**Плюсы Specifications:**
>- Каждый фильтр — отдельный переиспользуемый предикат
>- Можно комбинировать через `and()`, `or()`, `not()`
>- Работает с пагинацией: `repo.findAll(spec, PageRequest.of(0, 20))`
>- Проверяется compile-time (с Metamodel — полностью типобезопасно)
>
>**Минусы:**
>- Многословно: `(root, query, cb) ->` на каждый предикат
>- JOIN'ы вручную, легко задублировать (два фильтра по user → два JOIN'а)
>
>**Вариант 2 — QueryDSL:**
>
>Зависимость:
>```xml
><dependency>
>    <groupId>com.querydsl</groupId>
>    <artifactId>querydsl-jpa</artifactId>
>    <classifier>jakarta</classifier>
></dependency>
><dependency>
>    <groupId>com.querydsl</groupId>
>    <artifactId>querydsl-apt</artifactId>
>    <classifier>jakarta</classifier>
>    <scope>provided</scope>
></dependency>
>```
>
>Репозиторий:
>```java
>public interface OrderRepository extends JpaRepository<Order, Long>,
>                                         QuerydslPredicateExecutor<Order> {
>}
>```
>
>Сервис:
>```java
>@Service
>@RequiredArgsConstructor
>public class OrderSearchService {
>    private final OrderRepository repo;
>
>    @Transactional(readOnly = true)
>    public List<OrderDTO> search(OrderFilter f) {
>        QOrder o = QOrder.order;
>        BooleanBuilder where = new BooleanBuilder();
>
>        if (f.getStatus() != null) {
>            where.and(o.status.eq(f.getStatus()));
>        }
>        if (f.getDateFrom() != null) {
>            where.and(o.createdAt.goe(f.getDateFrom().atStartOfDay()));
>        }
>        if (f.getDateTo() != null) {
>            where.and(o.createdAt.lt(f.getDateTo().plusDays(1).atStartOfDay()));
>        }
>        if (f.getMinAmount() != null) {
>            where.and(o.totalAmount.goe(f.getMinAmount()));
>        }
>        if (f.getUserName() != null) {
>            where.and(o.user.name.containsIgnoreCase(f.getUserName()));
>        }
>
>        return StreamSupport.stream(repo.findAll(where).spliterator(), false)
>            .map(OrderDTO::from)
>            .toList();
>    }
>}
>```
>
>**Плюсы QueryDSL:**
>- Читается как обычный код: `o.status.eq(...)`, `o.user.name.containsIgnoreCase(...)`
>- Полная типобезопасность через сгенерированные Q-классы (ошибки — в compile-time)
>- JOIN'ы автоматически: `o.user.name` — Hibernate знает, что нужен JOIN
>
>**Минусы:**
>- Зависимость от code-generation (`apt` / `annotation-processor`)
>- Q-классы нужно перегенерировать при изменении модели
>- Проект QueryDSL развивается медленнее, чем Spring Data
>
>**Сравнение:**
>
>| Аспект | String JPQL | Specifications | QueryDSL |
>|--------|-------------|---------------|----------|
>| Читаемость | Плохая | Средняя | Хорошая |
>| Типобезопасность | Нет | Частичная (с Metamodel) | Полная |
>| Compile-time проверки | Нет | Частично | Да |
>| Внешние зависимости | Нет | Нет | Да (apt) |
>| Сложность JOIN'ов | Ручная | Ручная | Автоматическая |
>| Пагинация | Вручную | Встроена | Встроена |
>
>**Рекомендация:** для проектов с >3 динамическими фильтрами — QueryDSL. Для простых случаев или если не хотите зависимостей — Specifications.

### Задача 16: Напишите отчёт через Native Query с оконными функциями

Нужен отчёт: для каждого заказа показать:
- Данные заказа (`id`, `created_at`, `total_amount`)
- Порядковый номер заказа пользователя (`order_rank`)
- Нарастающий итог пользователя (`running_total`)
- Процент от общей суммы всех заказов пользователя (`pct_of_user_total`)
- Среднюю сумму заказа в скользящем окне 3 последних заказов (`moving_avg_3`)

JPQL/HQL не поддерживает оконные функции. Реализуйте через Native Query и замапьте результат.

>[!note]- Решение
>**Native Query:**
>```java
>public interface OrderReportRepository extends JpaRepository<Order, Long> {
>
>    @Query(value = """
>        SELECT
>            o.id,
>            o.created_at,
>            o.total_amount,
>            u.name AS user_name,
>            ROW_NUMBER() OVER (
>                PARTITION BY o.user_id ORDER BY o.created_at
>            ) AS order_rank,
>            SUM(o.total_amount) OVER (
>                PARTITION BY o.user_id ORDER BY o.created_at
>                ROWS UNBOUNDED PRECEDING
>            ) AS running_total,
>            ROUND(
>                o.total_amount * 100.0 / SUM(o.total_amount) OVER (PARTITION BY o.user_id),
>                2
>            ) AS pct_of_user_total,
>            ROUND(
>                AVG(o.total_amount) OVER (
>                    PARTITION BY o.user_id ORDER BY o.created_at
>                    ROWS BETWEEN 2 PRECEDING AND CURRENT ROW
>                ),
>                2
>            ) AS moving_avg_3
>        FROM orders o
>        JOIN users u ON u.id = o.user_id
>        WHERE o.user_id = :userId
>        ORDER BY o.created_at
>    """, nativeQuery = true)
>    List<OrderReportRow> findOrderReportByUser(@Param("userId") Long userId);
>}
>```
>
>**Маппинг результата — Interface-based Projection (самый простой):**
>```java
>public interface OrderReportRow {
>    Long getId();
>    Instant getCreatedAt();
>    BigDecimal getTotalAmount();
>    String getUserName();
>    Long getOrderRank();
>    BigDecimal getRunningTotal();
>    BigDecimal getPctOfUserTotal();
>    BigDecimal getMovingAvg3();
>}
>```
>
>Spring Data автоматически маппит колонки по имени (snake_case → camelCase).
>
>**Альтернативный маппинг — через @SqlResultSetMapping + DTO:**
>```java
>@SqlResultSetMapping(
>    name = "OrderReportMapping",
>    classes = @ConstructorResult(
>        targetClass = OrderReportDTO.class,
>        columns = {
>            @ColumnResult(name = "id", type = Long.class),
>            @ColumnResult(name = "created_at", type = Instant.class),
>            @ColumnResult(name = "total_amount", type = BigDecimal.class),
>            @ColumnResult(name = "user_name", type = String.class),
>            @ColumnResult(name = "order_rank", type = Long.class),
>            @ColumnResult(name = "running_total", type = BigDecimal.class),
>            @ColumnResult(name = "pct_of_user_total", type = BigDecimal.class),
>            @ColumnResult(name = "moving_avg_3", type = BigDecimal.class)
>        }
>    )
>)
>@Entity
>public class Order { ... }
>
>public record OrderReportDTO(
>    Long id, Instant createdAt, BigDecimal totalAmount,
>    String userName, Long orderRank, BigDecimal runningTotal,
>    BigDecimal pctOfUserTotal, BigDecimal movingAvg3
>) {}
>```
>
>Использование:
>```java
>@PersistenceContext
>private EntityManager em;
>
>public List<OrderReportDTO> getReport(Long userId) {
>    return em.createNativeQuery(SQL, "OrderReportMapping")
>        .setParameter("userId", userId)
>        .getResultList();
>}
>```
>
>**Разбор оконных функций:**
>
>| Функция | Что делает | Окно |
>|---------|-----------|------|
>| `ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at)` | Порядковый номер заказа внутри пользователя | Всё partition |
>| `SUM(...) OVER (... ROWS UNBOUNDED PRECEDING)` | Нарастающий итог от первого заказа до текущего | От начала до текущей строки |
>| `SUM(...) OVER (PARTITION BY user_id)` | Общая сумма всех заказов пользователя (без ORDER BY = всё partition) | Всё partition |
>| `AVG(...) OVER (... ROWS BETWEEN 2 PRECEDING AND CURRENT ROW)` | Среднее по 3 последним заказам (текущий + 2 предыдущих) | Скользящее окно 3 строки |
>
>**Важно:**
>- `ROWS UNBOUNDED PRECEDING` — физические строки. Для дат с возможными дублями используйте `RANGE`
>- `PARTITION BY` без `ORDER BY` — окно = весь partition (для `pct_of_user_total`)
>- `PARTITION BY` с `ORDER BY` — рамка по умолчанию `RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`
>
>**Когда Native SQL оправдан:**
>- Оконные функции (`ROW_NUMBER`, `LAG`, `LEAD`, `NTILE`)
>- CTE (`WITH ... AS`)
>- `RETURNING` (PostgreSQL)
>- Рекурсивные запросы
>- Full-text search (`tsvector`, `ts_rank`)
>- Специфика БД: `LATERAL JOIN`, `FILTER`, `ARRAY_AGG`
>
>**Правило:** Native Query — не антипаттерн, а инструмент. Если SQL делает работу за один проход, а JPQL потребует N запросов или пост-обработку в Java — выбирайте Native.

---

## Производительность и мониторинг

### Задача 17: Диагностируйте проблему производительности через Hibernate Statistics

Эндпоинт `GET /api/dashboard` отвечает за 3.2 секунды. Код:

```java
@Transactional(readOnly = true)
public DashboardDTO getDashboard(Long userId) {
    User user = userRepo.findById(userId).orElseThrow();
    List<Order> orders = orderRepo.findByUserId(userId);
    List<Notification> notifications = notifRepo.findTop10ByUserIdOrderByCreatedAtDesc(userId);

    return new DashboardDTO(
        user.getName(),
        user.getCountry().getName(),           // (1)
        orders.size(),
        orders.stream()
            .map(o -> new OrderSummary(
                o.getId(),
                o.getItems().size(),             // (2)
                o.getItems().stream()
                    .mapToDouble(i -> i.getProduct().getPrice())  // (3)
                    .sum()
            ))
            .toList(),
        notifications.stream()
            .map(n -> n.getChannel().getDisplayName())   // (4)
            .toList()
    );
}
```

Hibernate Statistics показывает:

```
Query execution count: 4
Entity fetch count: 312
Collection fetch count: 48
Second level cache hit count: 0
```

Найдите все проблемы, объясните каждую цифру, и исправьте — цель: <100ms.

>[!note]- Решение
>**Разбор цифр:**
>
>| Метрика | Значение | Что это значит |
>|---------|----------|---------------|
>| `Query execution count: 4` | 4 основных запроса | `findById` + `findByUserId` + `findTop10...` + 1 extra (country?) |
>| `Entity fetch count: 312` | 312 дополнительных SELECT | N+1 по `items`, `product`, `channel` |
>| `Collection fetch count: 48` | 48 инициализаций коллекций | 48 заказов × `getItems()` |
>| `L2 cache hit: 0` | Кэш не настроен | `Country`, `Channel` могут кэшироваться |
>
>**Детальный анализ N+1:**
>
>1. **(1) `user.getCountry().getName()`** — 1 лишний SELECT (LAZY `@ManyToOne`)
>2. **(2) `o.getItems().size()`** — 48 SELECT'ов (по одному на каждый заказ → инициализация коллекции `items`)
>3. **(3) `i.getProduct().getPrice()`** — ~260 SELECT'ов (по одному на каждый `OrderItem` → загрузка `Product`)
>4. **(4) `n.getChannel().getDisplayName()`** — ~10 SELECT'ов (10 нотификаций × загрузка `Channel`)
>
>Итого: 4 + 1 + 48 + 260 + 10 = 323 запроса ≈ 312 entity fetch + 48 collection fetch + 4 query.
>
>**Исправление — шаг за шагом:**
>
>**Шаг 1 — User + Country в одном запросе:**
>```java
>public interface UserRepository extends JpaRepository<User, Long> {
>
>    @Query("SELECT u FROM User u LEFT JOIN FETCH u.country WHERE u.id = :id")
>    Optional<User> findByIdWithCountry(@Param("id") Long id);
>}
>```
>
>**Шаг 2 — Orders + Items + Products в одном запросе:**
>```java
>public interface OrderRepository extends JpaRepository<Order, Long> {
>
>    @Query("""
>        SELECT DISTINCT o FROM Order o
>        LEFT JOIN FETCH o.items i
>        LEFT JOIN FETCH i.product
>        WHERE o.user.id = :userId
>    """)
>    List<Order> findByUserIdWithItemsAndProducts(@Param("userId") Long userId);
>}
>```
>
>**Шаг 3 — Notifications + Channel:**
>```java
>public interface NotificationRepository extends JpaRepository<Notification, Long> {
>
>    @Query("""
>        SELECT n FROM Notification n
>        LEFT JOIN FETCH n.channel
>        WHERE n.user.id = :userId
>        ORDER BY n.createdAt DESC
>        LIMIT 10
>    """)
>    List<Notification> findTop10WithChannel(@Param("userId") Long userId);
>}
>```
>
>**Шаг 4 — L2-кэш для справочников:**
>```java
>@Entity
>@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
>@Immutable
>public class Country { ... }
>
>@Entity
>@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
>@Immutable
>public class NotificationChannel { ... }
>```
>
>**Итоговый код:**
>```java
>@Transactional(readOnly = true)
>public DashboardDTO getDashboard(Long userId) {
>    User user = userRepo.findByIdWithCountry(userId).orElseThrow();
>    List<Order> orders = orderRepo.findByUserIdWithItemsAndProducts(userId);
>    List<Notification> notifications = notifRepo.findTop10WithChannel(userId);
>
>    return new DashboardDTO(
>        user.getName(),
>        user.getCountry().getName(),
>        orders.size(),
>        orders.stream()
>            .map(o -> new OrderSummary(
>                o.getId(),
>                o.getItems().size(),
>                o.getItems().stream()
>                    .mapToDouble(i -> i.getProduct().getPrice())
>                    .sum()
>            ))
>            .toList(),
>        notifications.stream()
>            .map(n -> n.getChannel().getDisplayName())
>            .toList()
>    );
>}
>```
>
>**Результат Statistics после исправления:**
>```
>Query execution count: 3      (было 4)
>Entity fetch count: 0         (было 312)
>Collection fetch count: 0     (было 48)
>Second level cache hit count: 1  (Country из L2)
>```
>
>3 SQL-запроса, каждый с JOIN FETCH, 0 дополнительных SELECT'ов. Время: ~20-50ms.
>
>**Бонус — DTO-проекция (ещё быстрее):**
>
>Если дашборд — read-only и не нужно менять данные, можно вообще не грузить Entity:
>```java
>@Query(value = """
>    SELECT u.name AS userName, c.name AS countryName,
>           COUNT(DISTINCT o.id) AS orderCount,
>           COALESCE(SUM(oi.price), 0) AS totalSpent
>    FROM users u
>    LEFT JOIN countries c ON c.code = u.country_code
>    LEFT JOIN orders o ON o.user_id = u.id
>    LEFT JOIN order_items oi ON oi.order_id = o.id
>    WHERE u.id = :userId
>    GROUP BY u.name, c.name
>""", nativeQuery = true)
>DashboardSummary findDashboardSummary(@Param("userId") Long userId);
>```
>1 запрос, 0 объектов в памяти, нет N+1, нет L1-кэша → самый быстрый вариант.

### Задача 18: Entity vs DTO — покажите разницу на реальном примере

Эндпоинт `GET /api/products?category=ELECTRONICS` возвращает список товаров. На фронте нужно только: `id`, `name`, `price`, `inStock`. Текущий код:

```java
@Transactional(readOnly = true)
public List<ProductResponse> getProducts(String category) {
    return productRepo.findByCategory(category).stream()
        .map(p -> new ProductResponse(
            p.getId(), p.getName(), p.getPrice(), p.getStockQuantity() > 0
        ))
        .toList();
}
```

`Product` — широкая сущность: 25 колонок, включая `@Lob byte[] image`, `@OneToMany List<Review> reviews`, `@ManyToMany Set<Tag> tags`. Категория ELECTRONICS — 10 000 товаров.

Объясните проблемы и покажите 3 способа оптимизации с замерами.

>[!note]- Решение
>**Проблемы текущего кода:**
>
>1. **Загружаются все 25 колонок**, включая `image` (BLOB) — каждый Product = сотни KB
>2. **10 000 Entity в L1-кэше** — snapshots для dirty checking × 10 000 = огромный расход памяти
>3. **Потенциальные N+1** — если `reviews` или `tags` случайно EAGER (или кто-то обратится к ним в маппере)
>4. **`@Transactional(readOnly = true)`** — помогает (skip dirty checking для Hibernate 5.4+), но Entity всё равно в памяти
>
>**Способ 1 — JPQL DTO-проекция (constructor expression):**
>```java
>public record ProductResponse(Long id, String name, BigDecimal price, boolean inStock) {}
>
>@Query("""
>    SELECT new com.example.dto.ProductResponse(
>        p.id, p.name, p.price, p.stockQuantity > 0
>    )
>    FROM Product p
>    WHERE p.category = :category
>""")
>List<ProductResponse> findProductDTOs(@Param("category") String category);
>```
>
>SQL:
>```sql
>SELECT p.id, p.name, p.price, (p.stock_quantity > 0)
>FROM products p WHERE p.category = ?
>```
>
>Загружаются **4 колонки** вместо 25. Никаких Entity в памяти, нет dirty checking, нет L1-кэша.
>
>**Способ 2 — Interface-based Projection (Spring Data magic):**
>```java
>public interface ProductView {
>    Long getId();
>    String getName();
>    BigDecimal getPrice();
>
>    @Value("#{target.stockQuantity > 0}")   // SpEL-выражение
>    boolean isInStock();
>}
>
>public interface ProductRepository extends JpaRepository<Product, Long> {
>    List<ProductView> findByCategory(String category);
>}
>```
>
>Spring Data автоматически генерирует SELECT только для нужных колонок. `@Value` позволяет вычислять поля на уровне Java.
>
>**Внимание:** если Spring Data не может оптимизировать — он загрузит Entity целиком и обернёт в proxy. Проверяйте генерируемый SQL в логах!
>
>**Способ 3 — Tuple / Native Query (полный контроль):**
>```java
>@Query(value = """
>    SELECT p.id, p.name, p.price, (p.stock_quantity > 0) AS in_stock
>    FROM products p
>    WHERE p.category = :category
>""", nativeQuery = true)
>List<Object[]> findProductTuples(@Param("category") String category);
>```
>
>Маппинг вручную:
>```java
>public List<ProductResponse> getProducts(String category) {
>    return productRepo.findProductTuples(category).stream()
>        .map(row -> new ProductResponse(
>            ((Number) row[0]).longValue(),
>            (String) row[1],
>            (BigDecimal) row[2],
>            (Boolean) row[3]
>        ))
>        .toList();
>}
>```
>
>Минусы: нет типобезопасности, хрупкий маппинг по индексу. Плюсы: полный контроль над SQL.
>
>**Замеры (10 000 записей, PostgreSQL, Product с 25 колонками + BLOB):**
>
>| Вариант | Время | Память (heap delta) | SQL-колонок |
>|---------|-------|---------------------|-------------|
>| Entity + stream map | ~450ms | ~120 MB (Entity + snapshot) | 25 + BLOB |
>| JPQL DTO-проекция | ~80ms | ~8 MB (только DTO) | 4 |
>| Interface Projection | ~90ms | ~10 MB (proxy + DTO) | 4 |
>| Native Tuple | ~70ms | ~6 MB (Object[]) | 4 |
>
>**5-6x быстрее, 15-20x меньше памяти.**
>
>**Когда что использовать:**
>
>| Сценарий | Выбор |
>|----------|-------|
>| Списки, таблицы, дашборды (только чтение) | **JPQL DTO** или **Interface Projection** |
>| Отчёты с агрегатами и оконными функциями | **Native Query + record** |
>| Форма редактирования (нужен Entity для dirty check) | **Entity** |
>| Узкая выборка, не хочу писать JPQL | **Interface Projection** |
>
>**Правило:** Entity — для write-пути (создание, изменение, удаление). DTO — для read-пути (списки, отчёты, API-ответы). Если метод `@Transactional(readOnly = true)` и возвращает список — почти наверняка нужна DTO-проекция.
