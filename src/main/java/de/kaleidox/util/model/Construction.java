package de.kaleidox.util.model;

public interface Construction {
    interface Builder<T> {
        /**
         * Builds a new instance of {@code T} with the (to this point) specified fields and values.
         *
         * @return A new instance of {@code T}.
         */
        T build();
    }

    interface Factory<T> {
        /**
         * Creates a new instance of {@code T} and increments the counter.
         *
         * @return A new instance of {@code T}.
         */
        T create();

        /**
         * Returns the internal counter.
         *
         * @return The internal counter.
         */
        int counter();

        interface Advanced<T, P> {
            /**
             * Creates a new instance of {@code T} and increments the counter.
             *
             * @return A new instance of {@code T}.
             */
            T create(P param);

            /**
             * Returns the internal counter.
             *
             * @return The internal counter.
             */
            int counter();
        }
    }
}
