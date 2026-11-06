package com.englishhero.identity.email;

public interface EmailDelivery {
    boolean available();

    void deliver(String email, String code, EmailPurpose purpose);
}
