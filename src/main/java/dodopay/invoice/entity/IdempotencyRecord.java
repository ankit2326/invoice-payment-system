package dodopay.invoice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
@IdClass(IdempotencyRecordId.class)
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Id
    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "request_path", nullable = false)
    private String requestPath;

    @Column(name = "request_body_hash", nullable = false, length = 128)
    private String requestBodyHash;

    @Column(name = "response_status_code", nullable = false)
    private int responseStatusCode;

    @Column(name = "response_body", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public IdempotencyRecord() {}

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public String getRequestPath() { return requestPath; }
    public void setRequestPath(String requestPath) { this.requestPath = requestPath; }
    public String getRequestBodyHash() { return requestBodyHash; }
    public void setRequestBodyHash(String requestBodyHash) { this.requestBodyHash = requestBodyHash; }
    public int getResponseStatusCode() { return responseStatusCode; }
    public void setResponseStatusCode(int responseStatusCode) { this.responseStatusCode = responseStatusCode; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public Instant getCreatedAt() { return createdAt; }
}
