package me.deecaad.core.mechanics.conditions;

import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.mechanics.CastData;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalDouble;

public class RangeCondition extends Condition {

    private OptionalDouble minSquared;
    private OptionalDouble maxSquared;

    /**
     * Default constructor for serializer.
     */
    public RangeCondition() {
    }

    public RangeCondition(OptionalDouble minSquared, OptionalDouble maxSquared) {
        this.minSquared = minSquared;
        this.maxSquared = maxSquared;
    }

    @Override
    public boolean isAllowed0(CastData cast) {
        // Bias towards using the target
        Location targetLocation;
        if (cast.getTarget() != null)
            targetLocation = cast.getTarget().getEyeLocation();
        else
            targetLocation = cast.getTargetLocation();

        double distanceSquared = targetLocation.distanceSquared(cast.getSourceLocation());
        if (minSquared.isPresent() && distanceSquared < minSquared.getAsDouble())
            return false;
        if (maxSquared.isPresent() && distanceSquared >= maxSquared.getAsDouble())
            return false;

        return true;
    }

    @Override
    public String getKeyword() {
        return "Range";
    }

    @Override
    public @Nullable String getWikiLink() {
        return "https://cjcrafter.gitbook.io/mechanics/conditions/range";
    }

    @NotNull @Override
    public Condition serialize(@NotNull SerializeData data) throws SerializerException {
        double minNum = data.of("Min").assertPositive().getDouble(-1.0);
        double maxNum = data.of("Max").assertPositive().getDouble(-1.0);

        OptionalDouble min = data.has("Min") ? OptionalDouble.of(minNum * minNum) : OptionalDouble.empty();
        OptionalDouble max = data.has("Max") ? OptionalDouble.of(maxNum * maxNum) : OptionalDouble.empty();
        return applyParentArgs(data, new RangeCondition(min, max));
    }
}
