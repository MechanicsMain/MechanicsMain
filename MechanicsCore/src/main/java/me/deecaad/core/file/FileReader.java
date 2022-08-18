package me.deecaad.core.file;

import me.deecaad.core.utils.Debugger;
import me.deecaad.core.utils.LogLevel;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class FileReader {

    private final Debugger debug;
    private final List<PathToSerializer> pathToSerializers;
    private final Map<String, Serializer<?>> serializers;
    private final List<ValidatorData> validatorDatas;
    private final Map<String, IValidator> validators;

    public FileReader(@Nonnull Debugger debug, @Nullable List<Serializer<?>> serializers, @Nullable List<IValidator> validators) {
        this.debug = debug;
        this.serializers = new HashMap<>();
        this.validators = new HashMap<>();
        this.pathToSerializers = new ArrayList<>();
        this.validatorDatas = new ArrayList<>();
        addSerializers(serializers);
        addValidators(validators);
    }

    /**
     * Adds new serializers for this file reader.
     * Serializers should be added before using fillAllFiles() or fillOneFile() methods.
     *
     * @param serializers the new list serializers for this file reader
     */
    public void addSerializers(List<Serializer<?>> serializers) {
        if (serializers != null && serializers.size() > 0) {
            for (Serializer<?> serializer : serializers) {
                addSerializer(serializer);
            }
        }
    }

    /**
     * Adds new serializer for this file reader.
     * Serializers should be added before using fillAllFiles() or fillOneFile() methods.
     *
     * @param serializer the new serializer for this file reader
     */
    public void addSerializer(Serializer<?> serializer) {
        String serializerLowerCase = serializer.getKeyword().toLowerCase();
        if (this.serializers.containsKey(serializerLowerCase)) {
            Serializer<?> alreadyAdded = this.serializers.get(serializerLowerCase);

            // Check if already added serializer isn't assignable with the new one
            if (!alreadyAdded.getClass().isAssignableFrom(serializer.getClass())) {
                debug.log(LogLevel.ERROR,
                        "Can't add serializer with keyword of " + serializer.getKeyword() + " because other serializer already has same keyword.",
                        "Already added serializer is located at " + alreadyAdded.getClass().getName() + " and this new one was located at " + serializer.getClass().getName() + ".",
                        "To override existing serializer either make new serializer extend existing serializer or implement Serializer<same data type as the existing one>.");
                return;
            }

            // If code reaches this point, it was assignable from new serializer
            debug.log(LogLevel.DEBUG,
                    "New serializer " + serializer.getClass().getName() + " will now override already added serializer " + alreadyAdded.getClass().getName());
        }
        this.serializers.put(serializerLowerCase, serializer);
    }

    /**
     * Adds new validators for this file reader.
     * Validators should be added before using fillAllFiles() or fillOneFile() methods.
     *
     * @param validators the new list validators for this file reader
     */
    public void addValidators(List<IValidator> validators) {
        if (validators != null && validators.size() > 0) {
            for (IValidator validator : validators) {
                addValidator(validator);
            }
        }
    }

    /**
     * Adds new validator for this file reader.
     * Validators should be added before using fillAllFiles() or fillOneFile() methods.
     *
     * @param validator the new validator for this file reader
     */
    public void addValidator(IValidator validator) {
        String validatorLowerCase = validator.getKeyword().toLowerCase();
        if (this.validators.containsKey(validatorLowerCase)) {
            IValidator alreadyAdded = this.validators.get(validatorLowerCase);

            // Check if already added validator isn't assignable with the new one
            if (!alreadyAdded.getClass().isAssignableFrom(validator.getClass())) {
                debug.log(LogLevel.ERROR,
                        "Can't add validator with keyword of " + validator.getKeyword() + " because other validator already has same keyword.",
                        "Already added validators is located at " + alreadyAdded.getClass().getName() + " and this new one was located at " + validator.getClass().getName() + ".");
                return;
            }

            // If code reaches this point, it was assignable from new validator
            debug.log(LogLevel.DEBUG,
                    "New validator " + validator.getClass().getName() + " will now override already added validator " + alreadyAdded.getClass().getName());
        }
        this.validators.put(validatorLowerCase, validator);
    }

    /**
     * Iterates through all .yml files inside every directory starting from the given directory.
     * It is recommended to give this method plugin's data folder directory.
     * This also takes in account given serializers.
     *
     * @param directory the directory
     * @param ignoreFiles ignored files which name starts with any given string
     * @return the map with all configurations
     */
    public Configuration fillAllFiles(File directory, @Nullable String... ignoreFiles) {
        if (directory == null || directory.listFiles() == null) {
            throw new IllegalArgumentException("The given file MUST be a directory!");
        }
        Configuration filledMap = fillAllFilesLoop(directory, ignoreFiles);

        // Only run this once
        // That's why fillAllFilesLoop method is required
        usePathToSerializersAndValidators(filledMap);

        // Filter out anything with a null value... Sometimes validators will
        // set to null so the code looks cleaner, but this will be confusing
        // for containsKey(), and it adds overhead from hash clashing. Just
        // remove null values for simplicity.
        LinkedConfig linked = (LinkedConfig) filledMap;
        linked.values().removeAll(Collections.singleton(null));

        return filledMap;
    }

    private Configuration fillAllFilesLoop(File directory, @Nullable String... ignoreFiles) {
        // A set to determine if a file should be ignored
        Set<String> fileBlacklist = ignoreFiles == null ? new HashSet<>() : Arrays.stream(ignoreFiles).collect(Collectors.toSet());

        // Dummy map to fill
        Configuration filledMap = new LinkedConfig();

        for (File directoryFile : directory.listFiles()) {

            String name = directoryFile.getName();
            if (fileBlacklist.contains(name)) continue;

            try {
                if (name.endsWith(".yml")) {
                    Configuration newFilledMap = fillOneFile(directoryFile);

                    // This occurs when the yml file is empty
                    if (newFilledMap == null) {
                        continue;
                    }

                    filledMap.add(newFilledMap);
                } else if (directoryFile.isDirectory()) {
                    filledMap.add(fillAllFilesLoop(directoryFile));
                }
            } catch (DuplicateKeyException ex) {
                debug.log(LogLevel.ERROR, "Found duplicate keys in configuration!",
                        "This occurs when you have 2 lines in configuration with the same name",
                        "This is a huge error and WILL 100% cause issues in your guns.",
                        "Duplicates Found: " + Arrays.toString(ex.getKeys()),
                        "Found in file: " + name);

                debug.log(LogLevel.DEBUG, "Duplicate Key Exception: ", ex);
            }
        }
        return filledMap;
    }

    /**
     * Fills one file into map and returns its configuration.
     * This also takes in account given serializers.
     *
     * @param file the file to read
     * @return the map with file's configurations
     */
    public Configuration fillOneFile(File file) {
        Configuration filledMap = new LinkedConfig();

        // If a serializer is found, it's path is saved here. Any
        // NON SERIALIZER variable within a serializer is then "skipped"
        // Meaning booleans, numbers, etc. are skipped
        String startsWithDeny = null;

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        for (String key : configuration.getKeys(true)) {

            // Remove the starsWithDeny if the key does no longer start with it
            // Booleans, numbers, etc. can then be saved again
            if (startsWithDeny != null && !key.startsWith(startsWithDeny)) {
                startsWithDeny = null;
            }

            String[] keySplit = key.split("\\.");
            if (keySplit.length > 0) {

                // Get the last "key name" of the key
                String lastKey = keySplit[keySplit.length - 1].toLowerCase();

                // Only allow using validators when they aren't under already serialized object
                if (startsWithDeny == null) {
                    IValidator validator = this.validators.get(lastKey);
                    if (validator != null) {
                        // Get the first "key name" of the key
                        String firstKey = keySplit[0].toLowerCase();

                        String keyWithoutFirstKey = keySplit.length == 1 ? null : key.substring(firstKey.length());
                        if (keyWithoutFirstKey == null || validator.getAllowedPaths() == null || validator.getAllowedPaths().stream().anyMatch(keyWithoutFirstKey::equalsIgnoreCase)) {

                            validatorDatas.add(new ValidatorData(validator, file, configuration, key));

                            if (validator.denyKeys())
                                startsWithDeny = key;
                        }
                    }
                }

                // Check if this key is a serializer, and that it isn't the header and handle pathTo
                Serializer<?> serializer = this.serializers.get(lastKey);
                if (serializer != null) {

                    // If the serializer doesn't have parent keywords used, or it doesn't match the current path
                    // -> Don't try to serialize this serializer under serializer
                    String keyWithoutLastKey = keySplit.length == 1 ? null : key.substring(0, key.length() - lastKey.length() - 1);
                    if (startsWithDeny != null && (serializer.getParentKeywords() == null || keyWithoutLastKey == null || serializer.getParentKeywords().stream().noneMatch(keyWithoutLastKey::endsWith))) {
                        continue;
                    }

                    if (!serializer.shouldSerialize(new SerializeData(serializer, file, key, configuration))) {
                        debug.debug("Skipping " + key + " due to skip");
                        continue;
                    }

                    String pathTo = serializer.useLater(configuration, key);
                    if (pathTo != null) {
                        pathToSerializers.add(new PathToSerializer(serializer, key, pathTo));
                    } else {
                        try {

                            // SerializerException can be thrown whenever the
                            // user input an invalid value. We should log the
                            // exception.
                            Object valid = serializer.serialize(new SerializeData(serializer, file, key, configuration));
                            filledMap.set(key, valid);

                            // Only update the startsWithDeny if this is the "main serializer"
                            // If this serialization happened within serializer (meaning this is child serializer), startsWithDeny is not null
                            if (startsWithDeny == null) startsWithDeny = key;

                        } catch (SerializerException ex) {
                            ex.log(debug);
                            if (startsWithDeny == null) startsWithDeny = key;
                        } catch (Exception ex) {

                            // Any Exception other than SerializerException
                            // should be fixed by the dev of the serializer.
                            throw new InternalError("Unhandled caught exception from serializer " + serializer + "!", ex);
                        }
                    }
                    continue;
                }
            }

            if (startsWithDeny != null && key.startsWith(startsWithDeny)) {
                continue;
            }

            // We don't want to store these
            if (configuration.isConfigurationSection(key)) continue;

            Object object = configuration.get(key);
            filledMap.set(key, object);
        }
        if (filledMap.getKeys().isEmpty()) {
            return null;
        }

        return filledMap;
    }

    /**
     * Uses all path to serializers and validators.
     * This should be used AFTER normal serialization.
     *
     * @param filledMap the filled mappings
     * @return the map with used path to serializers and validators
     */
    public Configuration usePathToSerializersAndValidators(Configuration filledMap) {
        if (!pathToSerializers.isEmpty()) {
            for (PathToSerializer pathToSerializer : pathToSerializers) {
                pathToSerializer.getSerializer().tryPathTo(filledMap, pathToSerializer.getPathWhereToStore(), pathToSerializer.getPathTo());
            }
        }
        if (!validatorDatas.isEmpty()) {
            for (ValidatorData validatorData : validatorDatas) {

                if (!validatorData.validator.shouldValidate(new SerializeData(validatorData.validator.getKeyword(), validatorData.file, validatorData.path, validatorData.configurationSection))) {
                    debug.debug("Skipping " + validatorData.path + " due to skip");
                    continue;
                }

                try {
                    validatorData.getValidator().validate(filledMap, new SerializeData(validatorData.validator.getKeyword(), validatorData.file, validatorData.path, validatorData.configurationSection));
                } catch (SerializerException ex) {
                    ex.log(debug);
                } catch (Exception ex) {
                    throw new InternalError("Unhandled caught exception from validator " + validatorData.validator + "!", ex);
                }
            }
        }
        return filledMap;
    }

    /**
     * Class to hold path to serializer data for later on use
     */
    public static class PathToSerializer {

        private final Serializer<?> serializer;
        private final String pathWhereToStore;
        private final String pathTo;

        public PathToSerializer(Serializer<?> serializer, String pathWhereToStore, String pathTo) {
            this.serializer = serializer;
            this.pathWhereToStore = pathWhereToStore;
            this.pathTo = pathTo;
        }

        public Serializer<?> getSerializer() {
            return serializer;
        }

        public String getPathWhereToStore() {
            return pathWhereToStore;
        }

        public String getPathTo() {
            return pathTo;
        }
    }

    /**
     * Class to help validators
     */
    public static class ValidatorData {

        private final IValidator validator;
        private final File file;
        private final ConfigurationSection configurationSection;
        private final String path;

        public ValidatorData(IValidator validator, File file, ConfigurationSection configurationSection, String path) {
            this.validator = validator;
            this.file = file;
            this.configurationSection = configurationSection;
            this.path = path;
        }

        public IValidator getValidator() {
            return validator;
        }

        public File getFile() {
            return file;
        }

        public ConfigurationSection getConfigurationSection() {
            return configurationSection;
        }

        public String getPath() {
            return path;
        }
    }
}