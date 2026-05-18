package com.jspider.spring_boot_simple_crud_with_mysql.dto;

import com.jspider.spring_boot_simple_crud_with_mysql.entity.Product;

/**
 * Response-side Data Transfer Object that conveys product state from the server
 * back to the API client.
 *
 * <p><strong>Security purpose &mdash; Persistence/wire-contract isolation
 * (CWE-915, OWASP A08:2021).</strong> Paired with the companion
 * {@link ProductRequestDto}, this record decouples the outbound JSON wire
 * contract from the JPA-managed {@code Product} entity. The entity is a
 * persistence-context-attached object whose lifecycle is managed by Hibernate;
 * returning it directly from a controller risks (a) lazy-loading side effects
 * during JSON serialization, (b) accidental exposure of internal persistence
 * fields, and (c) "round-trip" mass-assignment attacks where a client uses an
 * entity-shaped response as a template to forge a request. By projecting the
 * entity through this immutable record at the controller boundary, the
 * application emits exactly four fields, in a known order, with a known wire
 * shape, and never leaks the persistence model.
 *
 * <p><strong>Why all four fields including {@code id}.</strong> Unlike the
 * request DTO (which omits {@code id} to defend against mass assignment), the
 * response DTO must carry {@code id} because clients legitimately need the
 * server-assigned primary key to subsequently address the resource through
 * controller paths that actually exist in {@code ProductController}, such as
 * {@code GET /product/getProduct/{id}} and
 * {@code PUT /product/updateProduct/{id}}. The direction of trust is
 * inverted: on the response side the server is the authoritative producer of
 * {@code id}, so emitting it is correct and necessary.
 *
 * <p><strong>Why boxed {@link Integer} and {@link Double} wire types.</strong>
 * Although the {@code Product} entity declares {@code int id} and
 * {@code double price} as primitives, this record uses the boxed reference
 * types so that the wire contract tolerates a JSON {@code null} during any
 * defensive client-side deserialization round-trip. Auto-boxing in the
 * canonical constructor invocation inside {@link #from(Product)} transparently
 * promotes {@code int -&gt; Integer} and {@code double -&gt; Double}; no
 * additional code is required.
 *
 * <p><strong>No validation annotations.</strong> Jakarta Bean Validation
 * constraints belong on the <em>request</em> side, where untrusted input
 * crosses the trust boundary. Response DTOs are server-produced and therefore
 * should not carry constraints &mdash; doing so would cause the application to
 * reject its own outputs if historical data violated current invariants (e.g.
 * a row persisted before {@code @DecimalMax("99999.99")} was introduced).
 *
 * <p><strong>Record semantics &mdash; auto-generated members.</strong> Per
 * JLS &sect;8.10, a record class implicitly provides:
 * <ul>
 *   <li>A canonical constructor accepting all four components in declaration
 *       order.</li>
 *   <li>Public accessor methods named {@link #id()}, {@link #name()},
 *       {@link #color()}, and {@link #price()} &mdash; note the component-name
 *       style; these are <em>not</em> JavaBean {@code getId()} / {@code getName()}
 *       style methods.</li>
 *   <li>{@link Object#equals(Object) equals(Object)} that compares all four
 *       components.</li>
 *   <li>{@link Object#hashCode() hashCode()} derived from all four components.</li>
 *   <li>{@link Object#toString() toString()} that prints the type name and all
 *       four components.</li>
 * </ul>
 * Jackson's Spring Boot auto-configuration serializes records through the
 * canonical constructor and the component accessors, producing the JSON shape
 * {@code {"id":...,"name":...,"color":...,"price":...}} for clients.
 *
 * <p><strong>Mapping convention.</strong> The static factory
 * {@link #from(Product)} is the single authoritative place that knows how to
 * convert an entity into this DTO. Centralizing the mapping here means future
 * controller refactoring can read {@code ProductResponseDto.from(product)}
 * without duplicating field-by-field construction at every call site, and any
 * future change to the entity-to-DTO mapping (e.g. truncating a name, masking
 * a sensitive field) is a one-file edit.
 *
 * @param id    server-assigned primary key from the {@code product} table;
 *              boxed {@link Integer} on the wire although the entity field is
 *              primitive {@code int}
 * @param name  product display name as persisted on the {@code Product} entity
 * @param color product color as persisted on the {@code Product} entity
 * @param price product price as persisted on the {@code Product} entity;
 *              boxed {@link Double} on the wire although the entity field is
 *              primitive {@code double}
 *
 * @see ProductRequestDto
 * @see Product
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/Mass_Assignment_Cheat_Sheet.html">OWASP Mass Assignment Cheat Sheet</a>
 * @see <a href="https://cwe.mitre.org/data/definitions/915.html">CWE-915</a>
 */
public record ProductResponseDto(Integer id, String name, String color, Double price) {

    /**
     * Static factory that projects a JPA {@link Product} entity onto an
     * immutable {@link ProductResponseDto} suitable for emission as part of an
     * HTTP response body.
     *
     * <p>The factory reads the Lombok-{@code @Data}-generated accessors
     * {@link Product#getId()}, {@link Product#getName()},
     * {@link Product#getColor()}, and {@link Product#getPrice()} on the entity
     * and forwards their values into this record's canonical constructor in
     * declaration order ({@code id}, {@code name}, {@code color},
     * {@code price}). Auto-boxing promotes the entity's primitive {@code int}
     * and {@code double} fields to the record's boxed {@link Integer} and
     * {@link Double} components transparently.
     *
     * <p><strong>Null-safety contract.</strong> This factory is the
     * controller-layer adapter for entities that the persistence layer has
     * already loaded; in practice {@code p} is non-null at every documented
     * call site (the controller is the producer, the DAO returns
     * fully-hydrated entities, and the {@code findById} path raises an
     * exception before reaching this factory if the row does not exist).
     * Callers therefore pass a fully-initialized {@link Product}; the factory
     * does not defensively null-check the entity reference because doing so
     * would silently mask programming errors with a {@link NullPointerException}
     * thrown at a less informative stack frame.
     *
     * @param p the source {@link Product} entity; must be non-null and fully
     *          hydrated (all Lombok-generated getters must return defined
     *          values for the resulting DTO to be meaningful)
     * @return  a new immutable {@link ProductResponseDto} carrying the four
     *          values projected from {@code p}
     */
    public static ProductResponseDto from(Product p) {
        return new ProductResponseDto(p.getId(), p.getName(), p.getColor(), p.getPrice());
    }
}
