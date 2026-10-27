package com.aplicafacil.identity.application.port.out;

import java.util.UUID;

/** Eventos de cuenta para los demás servicios. Se registran en la misma transacción (outbox). */
public interface AccountEvents {

    /** {@code user.deleted} (contracts/schemas/user-deleted-event.schema.json). */
    void userDeleted(UUID userId);
}
