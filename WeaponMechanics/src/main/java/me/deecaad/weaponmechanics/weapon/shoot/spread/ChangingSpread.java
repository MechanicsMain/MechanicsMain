package me.deecaad.weaponmechanics.weapon.shoot.spread;

import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.Serializer;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.utils.NumberUtil;
import me.deecaad.weaponmechanics.wrappers.EntityWrapper;
import me.deecaad.weaponmechanics.wrappers.HandData;
import org.jetbrains.annotations.NotNull;

public class ChangingSpread implements Serializer<ChangingSpread> {

    private double startingAmount;
    private int resetTime;
    private ModifySpreadWhen increaseChangeWhen;
    private Bounds bounds;

    /**
     * Default constructor for serializer
     */
    public ChangingSpread() {
    }

    public ChangingSpread(double startingAmount, int resetTime, ModifySpreadWhen increaseChangeWhen, Bounds bounds) {
        this.startingAmount = startingAmount;
        this.resetTime = resetTime;
        this.increaseChangeWhen = increaseChangeWhen;
        this.bounds = bounds;
    }

    /**
     * Applies all changes based on this changing spread. After changes are applied, also entity
     * wrapper's {@link HandData#getSpreadChange()} is modified based on circumstances. This basically
     * means that changes are always made for NEXT shot, not current.
     *
     * @param entityWrapper the entity wrapper used to check circumstances
     * @param tempSpread the spread
     * @param mainHand whether main hand was used
     * @param updateSpreadChange whether to allow updating current spread change
     * @return the modifier holder with updated horizontal and vertical values
     */
    public double applyChanges(EntityWrapper entityWrapper, double tempSpread, boolean mainHand, boolean updateSpreadChange) {
        HandData handData = mainHand ? entityWrapper.getMainHandData() : entityWrapper.getOffHandData();

        // Reset if required
        if (NumberUtil.hasMillisPassed(handData.getLastShotTime(), resetTime))
            handData.setSpreadChange(startingAmount);

        // Check bounds of spread change
        boolean didReset = false;
        if (bounds != null)
            didReset = bounds.checkBounds(handData, startingAmount);

        // Add the current spread before doing modifications to it
        tempSpread += handData.getSpreadChange();

        // Modify current changing spread only if its allowed
        // AND
        // If bounds didn't reset it
        if (updateSpreadChange && !didReset) {
            handData.setSpreadChange(increaseChangeWhen.applyChanges(entityWrapper, handData.getSpreadChange()));
        }

        return tempSpread;
    }

    @Override
    public String getKeyword() {
        return "Changing_Spread";
    }

    @Override
    public @NotNull ChangingSpread serialize(@NotNull SerializeData data) throws SerializerException {
        ModifySpreadWhen increaseChangeWhen = (ModifySpreadWhen) data.of("Increase_Change_When").assertExists().serialize(ModifySpreadWhen.class).get();
        double startingAmount = data.of("Starting_Amount").getDouble().orElse(0.0) * 0.01;
        int resetTime = data.of("Reset_Time").getInt().orElse(20) * 50; // Convert to millis

        Bounds bounds = data.of("Bounds").serialize(Bounds.class).orElse(null);

        return new ChangingSpread(startingAmount, resetTime, increaseChangeWhen, bounds);
    }

    public static class Bounds implements Serializer<Bounds> {

        private boolean resetAfterReachingBound;
        private double min;
        private double max;

        /**
         * Default constructor for serializer
         */
        public Bounds() {
        }

        public Bounds(boolean resetAfterReachingBound, double min, double max) {
            this.resetAfterReachingBound = resetAfterReachingBound;
            this.min = min;
            this.max = max;
        }

        /**
         * Checks bounds of spread
         *
         * @return whether the spread change was reset
         */
        public boolean checkBounds(HandData handData, double startingAmount) {
            double currentSpreadChange = handData.getSpreadChange();
            if (min != 0.0 && currentSpreadChange <= min) {
                if (resetAfterReachingBound) {
                    handData.setSpreadChange(startingAmount);
                    return true;
                }
                handData.setSpreadChange(min);
                return false;
            } else if (max != 0.0 && currentSpreadChange > max) {
                if (resetAfterReachingBound) {
                    handData.setSpreadChange(startingAmount);
                    return true;
                }
                handData.setSpreadChange(max);
                return false;
            }
            return false;
        }

        @Override
        public @NotNull Bounds serialize(@NotNull SerializeData data) throws SerializerException {
            double min = data.of("Minimum").assertRange(0, null).getDouble().orElse(0.0);
            double max = data.of("Maximum").assertRange(min, null).getDouble().orElse(15.0);

            boolean resetAfterReachingBound = data.of("Reset_After_Reaching_Bound").getBool().orElse(false);
            return new Bounds(resetAfterReachingBound, min * 0.01, max * 0.01);
        }
    }
}
