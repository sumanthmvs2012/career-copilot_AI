package com.careercopilot.ai;

import java.util.ArrayList;
import java.util.List;

/** Returns a fixed reply (or throws a fixed error) and records the prompts it was given. */
public class FakeLlmClient implements LlmClient {

    private final Object reply;
    private final RuntimeException error;
    public final List<String> systems = new ArrayList<>();
    public final List<String> users = new ArrayList<>();

    private FakeLlmClient(Object reply, RuntimeException error) {
        this.reply = reply;
        this.error = error;
    }

    public static FakeLlmClient replying(Object reply) {
        return new FakeLlmClient(reply, null);
    }

    public static FakeLlmClient failing(RuntimeException error) {
        return new FakeLlmClient(null, error);
    }

    public int calls() {
        return users.size();
    }

    @Override
    public <T> T completeStructured(String system, String user, Class<T> type, int maxTokens) {
        systems.add(system);
        users.add(user);
        if (error != null) {
            throw error;
        }
        return type.cast(reply);
    }
}
