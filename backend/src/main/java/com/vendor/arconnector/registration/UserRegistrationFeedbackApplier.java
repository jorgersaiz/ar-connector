package com.vendor.arconnector.registration;

import com.vendor.arconnector.domain.Person;
import com.vendor.arconnector.domain.RegistrationStatus;
import com.vendor.arconnector.registration.UserRegistrationFeedbackParser.Row;

import java.time.LocalDate;
import java.util.*;

/**
 * Aplica el feedback del registro a las personas enviadas ese día:
 *  - sin reason -> REGISTERED; su primer día imputable en AR es el siguiente al
 *    envío, así que si la fecha de alta de la persona era anterior se adelanta,
 *  - con reason -> REJECTED con el código APV_UR_xx.
 * Las filas se casan por "uid" (hash, que es lo que devuelve el fichero real)
 * o por "uids" (en claro) si viniera. Lógica pura, sin Spring.
 */
public final class UserRegistrationFeedbackApplier {

    public record Result(int matched, int unmatched, int registered, int rejected, List<Row> unmatchedRows) {
    }

    private UserRegistrationFeedbackApplier() {
    }

    public static Result apply(List<Row> rows, Collection<Person> sentPersons, LocalDate submissionDate) {
        Map<String, Person> byUid = new HashMap<>();
        Map<String, Person> byHash = new HashMap<>();
        for (Person p : sentPersons) {
            byUid.put(p.getUid(), p);
            byHash.put(p.personId().toUpperCase(Locale.ROOT), p);   // BBVA devuelve el hash en mayúsculas
        }
        LocalDate firstDayInAr = submissionDate.plusDays(1);

        int matched = 0;
        int registered = 0;
        int rejected = 0;
        List<Row> unmatched = new ArrayList<>();
        for (Row row : rows) {
            Person p = row.uids().isEmpty() ? null : byUid.get(row.uids());
            if (p == null && !row.uid().isEmpty()) {
                p = byHash.get(row.uid().toUpperCase(Locale.ROOT));
            }
            if (p == null) {
                unmatched.add(row);
                continue;
            }
            matched++;
            if (row.isRejected()) {
                p.setRegistrationStatus(RegistrationStatus.REJECTED);
                p.setRegistrationReason(row.reason());
                rejected++;
            } else {
                p.setRegistrationStatus(RegistrationStatus.REGISTERED);
                p.setRegistrationReason(null);
                if (p.getStartDate() == null || p.getStartDate().isBefore(firstDayInAr)) {
                    p.setStartDate(firstDayInAr);
                }
                registered++;
            }
        }
        return new Result(matched, unmatched.size(), registered, rejected, unmatched);
    }
}
