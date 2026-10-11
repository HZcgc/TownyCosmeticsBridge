package litebans.api;

public abstract class Events {
    private static final TestEvents INSTANCE = new TestEvents();

    public static Events get() {
        return INSTANCE;
    }

    public abstract void register(Listener listener);

    public abstract void unregister(Listener listener);

    public static Listener registeredListenerForTests() {
        return INSTANCE.listener;
    }

    public static void resetForTests() {
        INSTANCE.listener = null;
    }

    public static abstract class Listener {
        public void entryAdded(Entry entry) {
        }

        public void entryRemoved(Entry entry) {
        }

        public void broadcastSent(String message, String type) {
        }
    }

    private static final class TestEvents extends Events {
        private Listener listener;

        @Override
        public void register(Listener listener) {
            this.listener = listener;
        }

        @Override
        public void unregister(Listener listener) {
            if (this.listener == listener) this.listener = null;
        }
    }
}
