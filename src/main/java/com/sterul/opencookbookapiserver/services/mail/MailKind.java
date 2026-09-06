package com.sterul.opencookbookapiserver.services.mail;

/**
 * The mails this instance knows how to send.
 *
 * One name drives everything: the templates are mailtemplates/html/&lt;name&gt;.vm and
 * mailtemplates/text/&lt;name&gt;.vm, the texts are mail.&lt;name&gt;.* in the i18n bundles. So a
 * new mail is a value here plus four files, and there is no table of names to keep in step.
 */
public enum MailKind {

    ACTIVATION("activation"),
    PASSWORD_RESET("passwordReset"),
    ACCOUNT_DELETED("accountDeleted");

    private final String name;

    MailKind(String name) {
        this.name = name;
    }

    public String templateName() {
        return name;
    }

    public String subjectKey() {
        return "mail." + name + ".subject";
    }

    /** The line the inbox shows next to the subject. */
    public String preheaderKey() {
        return "mail." + name + ".preheader";
    }
}
