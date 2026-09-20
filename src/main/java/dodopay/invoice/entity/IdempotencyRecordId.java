package dodopay.invoice.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class IdempotencyRecordId implements Serializable {
    private String idempotencyKey;
    private UUID businessId;

    public IdempotencyRecordId() {}

    public IdempotencyRecordId(String idempotencyKey, UUID businessId) {
        this.idempotencyKey = idempotencyKey;
        this.businessId = businessId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyRecordId that)) return false;
        return Objects.equals(idempotencyKey, that.idempotencyKey) &&
               Objects.equals(businessId, that.businessId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotencyKey, businessId);
    }
}
