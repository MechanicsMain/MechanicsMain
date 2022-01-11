package me.deecaad.core.file;

import org.jetbrains.annotations.NotNull;

public class SerializerOptionsException extends SerializerException {

    public SerializerOptionsException(@NotNull Serializer<?> serializer, String type, Iterable<String> options,
                                      String actual, @NotNull String location) {

        super(serializer, getMessages(type, options, actual), location);
    }

    private static String[] getMessages(String type, Iterable<String> options, String actual) {
        return new String[] {
                "Could not match config to any " + type,
                forValue(actual),
                didYouMean(actual, options),
                possibleValues(options, 4)
        };
    }
}