package com.crdt.users;

public enum Gender {
    MALE("Male"),
    FEMALE("Female");

    private final String gender;

    Gender(String g) {
        this.gender = g;
    }

    public static Gender from(String s) {
        if(s.equalsIgnoreCase("Male"))
            return MALE;
        if(s.equalsIgnoreCase("Female"))
            return FEMALE;
        return null;
    }
    public String getGender() {
        return this.gender;
    }
}
