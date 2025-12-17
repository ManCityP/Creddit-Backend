package com.crdt;

import com.crdt.users.User;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;

public final class ReportableForcedAdapter implements TypeAdapterFactory {

    private static final RuntimeTypeAdapterFactory<Reportable> DELEGATE =
            RuntimeTypeAdapterFactory.of(Reportable.class, "type")
                    .registerSubtype(User.class, "user")
                    .registerSubtype(Post.class, "post")
                    .registerSubtype(Comment.class, "comment");

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        return DELEGATE.create(gson, type);
    }
}
