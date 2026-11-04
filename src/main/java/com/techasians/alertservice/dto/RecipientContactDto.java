package com.techasians.alertservice.dto;

import java.util.List;

public class RecipientContactDto {
    private List<String> emails;
    private List<String> phones;

    public RecipientContactDto(List<String> emails, List<String> phones) {
        this.emails = emails;
        this.phones = phones;
    }

    public List<String> getEmails() {
        return emails;
    }

    public void setEmails(List<String> emails) {
        this.emails = emails;
    }

    public List<String> getPhones() {
        return phones;
    }

    public void setPhones(List<String> phones) {
        this.phones = phones;
    }
}
