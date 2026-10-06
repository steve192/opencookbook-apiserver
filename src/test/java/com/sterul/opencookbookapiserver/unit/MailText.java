package com.sterul.opencookbookapiserver.unit;

import jakarta.mail.Multipart;
import jakarta.mail.Part;

/** A sent mail's text decoded, as a mail client shows it: German text travels quoted-printable. */
public final class MailText {

    private MailText() {
    }

    public static String of(Part mail) throws Exception {
        return textOf(mail.getContent());
    }

    private static String textOf(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        var all = new StringBuilder();
        if (content instanceof Multipart multipart) {
            for (var part = 0; part < multipart.getCount(); part++) {
                all.append(textOf(multipart.getBodyPart(part).getContent()));
            }
        }
        return all.toString();
    }
}
