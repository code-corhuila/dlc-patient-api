package co.edu.corhuila.dlc.patients.application.usecase;

import co.edu.corhuila.dlc.patients.application.port.in.RegisterPatientUseCase.Command;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class RegistrationFingerprint {
    private RegistrationFingerprint() {
    }

    static String hash(Command command) {
        var profile = command.profile();
        var canonical = new StringBuilder("patient-registration-v1:");
        for (var value : new String[] {command.document().type(), command.document().number(),
                profile.name(), profile.birthDate().toString(), profile.phone(), profile.email(),
                profile.address(), Integer.toString(profile.emergencyContacts().size())}) {
            append(canonical, value);
        }
        for (var contact : profile.emergencyContacts()) {
            append(canonical, contact.name());
            append(canonical, contact.relationship());
            append(canonical, contact.phone());
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void append(StringBuilder target, String value) {
        // Length prefixes distinguish field boundaries, nulls and literal separators.
        target.append(value == null ? -1 : value.length()).append(':');
        if (value != null) {
            target.append(value);
        }
    }
}
