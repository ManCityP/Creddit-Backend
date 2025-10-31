package com.crdt.users;

public enum Gender {
    MALE("Male"),
    FEMALE("Female");

    private final String gender;

    Gender(String g) {
        this.gender = g;
    }

    public static Gender toGender(String s) {
        if(s.equalsIgnoreCase("Male"))
            return MALE;
        else if(s.equalsIgnoreCase("Female"))
            return FEMALE;
        else
            return null;
    }
    public String getGender() {
        return this.gender;
    }
}
