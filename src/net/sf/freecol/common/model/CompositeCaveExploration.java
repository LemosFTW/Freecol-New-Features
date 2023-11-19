package net.sf.freecol.common.model;

import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.common.util.RandomChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

public class CompositeCaveExploration extends AbstractCaveExploration{

    private static final Logger logger = Logger.getLogger(AbstractCaveExploration.class.getName());

    private List<CaveExploration> floors;

    public CompositeCaveExploration(Game game, Tile tile) {
        super(game, tile);
    }

    public CompositeCaveExploration(Game game, Tile tile, FindType type, String name) {
        super(game, tile, type, name);
    }

    public CompositeCaveExploration(Game game, String id) {
        super(game, id);
    }

    @Override
    public String getDescriptionKey() {
        return null;
    }

    /**
     * Chooses a type of Lost City Rumour.  The type of rumour depends
     * on the exploring unit, as well as player settings.
     *
     * The scouting outcome is based on three factors: good/bad percent
     * rumour difficulty option, expert scout or not, DeSoto or not.
     *
     * FIXME: Make FindType a FreeColSpecObjectType and move all the
     * magic numbers in here to the specification.
     *
     * @param unit The {@code Unit} exploring (optional).
     * @param random A random number source.
     * @return The type of rumour.
     */
    public FindType chooseType(Unit unit, Random random) {
        final Specification spec = getSpecification();
        final Tile tile = getTile();
        // Booleans for various rumour types that are conditionally available.
        //
        // If the tile is native-owned, allow burial grounds rumour.
        final boolean allowBurial = tile.getOwner() != null
                && tile.getOwner().isIndian();
        // Only colonial players get FoYs as immigration ends at independence
        final boolean allowFoY = unit != null
                && unit.getOwner().getPlayerType() == Player.PlayerType.COLONIAL;
        // Certain units can learn a skill at LCRs
        final boolean allowLearn = unit != null
                && !spec.getUnitChanges(UnitChangeType.LOST_CITY,
                unit.getType()).isEmpty();
        // Expert units never vanish
        final boolean allowVanish = !(unit != null
                && unit.hasAbility(Ability.EXPERT_SCOUT));

        // Work out the bad and good chances.  The base values are
        // difficulty options.  Neutral results take up any remainder.
        int percentBad = spec.getInteger(GameOptions.BAD_RUMOUR);
        int percentGood = spec.getInteger(GameOptions.GOOD_RUMOUR);
        if (!allowBurial && !allowVanish) {
            // Degenerate case where no bad rumours are possible
            percentBad = 0;
        } else if (unit != null) {
            if (unit.getOwner().hasAbility(Ability.RUMOURS_ALWAYS_POSITIVE)) {
                // DeSoto forces all good results.
                percentBad = 0;
                percentGood = 100;
            } else {
                // Otherwise apply any unit exploration bonus
                float mod = unit.apply(1.0f, getGame().getTurn(),
                        Modifier.EXPLORE_LOST_CITY_RUMOUR);
                percentBad = Math.round(percentBad / mod);
                percentGood = Math.round(percentGood * mod);
            }
        }
        int percentNeutral = Math.max(0, 100 - percentBad - percentGood);

        // Add all possible events to a RandomChoice List
        List<RandomChoice<FindType>> c = new ArrayList<>();

        if (percentGood > 0) { // The GOOD
            if (allowLearn) {
                c.add(new RandomChoice<>(FindType.LEARN,
                        30 * percentGood));
                c.add(new RandomChoice<>(FindType.COLONIST,
                        20 * percentGood));
            } else {
                c.add(new RandomChoice<>(FindType.COLONIST,
                        30 * percentGood));
            }
            c.add(new RandomChoice<>(FindType.TREASURE,
                    20 * percentGood));
        }

        if (percentBad > 0) { // The BAD
            List<RandomChoice<FindType>> cbad = new ArrayList<>();
            if (allowVanish) {
                cbad.add(new RandomChoice<>(FindType.TRAP,
                        75 * percentBad));
            }
            RandomChoice.normalize(cbad, 100);
            c.addAll(cbad);
        }

        if (percentNeutral > 0) { // The NEUTRAL
            c.add(new RandomChoice<>(FindType.NOTHING,
                    100 * percentNeutral));
        }

        return RandomChoice.getWeightedRandom(logger, "Choose rumour", c,
                random);
    }

    @Override
    public ModelMessage getNothingMessage(Player player, boolean mounds, Random random) {
        return null;
    }
}
