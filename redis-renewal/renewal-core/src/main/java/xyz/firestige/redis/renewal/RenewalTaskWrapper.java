package xyz.firestige.redis.renewal;

/**
 * 运行时任务包装器。
 */
class RenewalTaskWrapper {
    private final String id;
    private final RenewalTask task;
    private final RenewalContext context;
    private volatile boolean paused = false;

    RenewalTaskWrapper(String id, RenewalTask task, RenewalContext context) {
        this.id = id;
        this.task = task;
        this.context = context;
    }

    String id() {
        return id;
    }

    RenewalTask task() {
        return task;
    }

    RenewalContext context() {
        return context;
    }

    boolean paused() {
        return paused;
    }

    void pause() {
        this.paused = true;
    }

    void resume() {
        this.paused = false;
    }
}

