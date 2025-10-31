package com.crdt.users;

public enum Gender {
    MALE("Male"),
    FEMALE("Female");

    private final String gender;

    Gender(String g) {
        this.gender = g;
    }
}
