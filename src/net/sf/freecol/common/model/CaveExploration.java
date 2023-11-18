/**
 *  Copyright (C) 2002-2022   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.i18n.NameCache;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;
import static net.sf.freecol.common.model.Constants.*;
import net.sf.freecol.common.model.Map.Layer;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.common.util.RandomChoice;
import static net.sf.freecol.common.util.RandomUtils.*;
import static net.sf.freecol.common.util.StringUtils.*;


/**
 * Represents a cave to be explored.
 */
public class CaveExploration extends TileItem {

    private static final Logger logger = Logger.getLogger(CaveExploration.class.getName());

    public static final String TAG = "CaveExplorationExploration";

    // The bogus end of the world year.
    private static final int MAYAN_PROPHESY_YEAR = 2012;

    /** Constants describing types of Lost City Rumours. */
    public static enum FindType {
        NOTHING_MORE_TO_EXPLORE,
        TRAP,
        NOTHING,
        LEARN,
        COLONIST,
        TREASURE;

        /**
         * Get the stem key for this LCR type.
         *
         * @return The stem key.
         */
        private String getKey() {
            return "caveExploration." + getEnumKey(this);
        }

        public String getDescriptionKey() {
            return Messages.descriptionKey("model." + getKey());
        }
    }


    /**
     * The type of the rumour.  A FindType, or null if the type has
     * not yet been determined.
     */
    private FindType type = null;

    /**
     * The name of this rumour, or null, if it has none.  Rumours such
     * as the Seven Cities of Gold and Fountains of Youth may have
     * individual names.
     */
    private String name = null;


    /**
     * Creates a new {@code LostCityRumour} instance.
     *
     * @param game The enclosing {@code Game}.
     * @param tile The {@code Tile} where the LCR is.
     */
    public CaveExploration(Game game, Tile tile) {
        super(game, tile);
    }

    /**
     * Creates a new {@code LostCityRumour} instance.
     *
     * @param game The enclosing {@code Game}.
     * @param tile The {@code Tile} where the LCR is.
     * @param type The type of rumour.
     * @param name The name of the rumour.
     */
    public CaveExploration(Game game, Tile tile, FindType type, String name) {
        super(game, tile);

        this.type = type;
        this.name = name;
    }

    /**
     * Creates a new {@code LostCityRumour} instance.
     *
     * @param game The enclosing {@code Game}.
     * @param id The object identifier.
     */
    public CaveExploration(Game game, String id) {
        super(game, id);
    }


    /**
     * Get the type of rumour.
     *
     * @return The {@code FindType}.
     */
    public final FindType getType() {
        return type;
    }

    /**
     * Set the type of rumour.
     *
     * @param newType The new rumour type.
     */
    public final void setType(final FindType newType) {
        this.type = newType;
    }

    /**
     * Get the name of this rumour.
     *
     * @return The name.
     */
    public final String getName() {
        return name;
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
            if (allowFoY) { // Tolerating potential 2% weight error
                c.add(new RandomChoice<>(FindType.FOUNTAIN_OF_YOUTH,
                        2 * percentGood));
            }
            if (allowLearn) {
                c.add(new RandomChoice<>(FindType.LEARN,
                        30 * percentGood));
                c.add(new RandomChoice<>(FindType.TRIBAL_CHIEF,
                        30 * percentGood));
                c.add(new RandomChoice<>(FindType.COLONIST,
                        20 * percentGood));
            } else {
                c.add(new RandomChoice<>(FindType.TRIBAL_CHIEF,
                        50 * percentGood));
                c.add(new RandomChoice<>(FindType.COLONIST,
                        30 * percentGood));
            }
            c.add(new RandomChoice<>(FindType.MOUNDS,
                    8 * percentGood));
            c.add(new RandomChoice<>(FindType.RUINS,
                    6 * percentGood));
            c.add(new RandomChoice<>(FindType.CIBOLA,
                    4 * percentGood));
        }

        if (percentBad > 0) { // The BAD
            List<RandomChoice<FindType>> cbad = new ArrayList<>();
            if (allowBurial) {
                cbad.add(new RandomChoice<>(FindType.BURIAL_GROUND,
                        25 * percentBad));
            }
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

    /**
     * Get the message for a "nothing" rumour.
     *
     * @param player The {@code Player} to generate the message for.
     * @param mounds Is this rumour a result of exploring "strange mounds"?
     * @param random A pseudo-random number source.
     * @return A suitable {@code ModelMessage}.
     */
    public ModelMessage getNothingMessage(Player player, boolean mounds,
                                          Random random) {
        final Game game = getGame();
        return (mounds)
                ? new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                FindType.NOTHING.getAlternateDescriptionKey("mounds"),
                player)
                : (game.getTurn().getYear() % 100 == 12
                && randomInt(logger, "Mayans?", random, 4) == 0)
                ? new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                FindType.NOTHING.getAlternateDescriptionKey("mayans"),
                player)
                .addAmount("%years%",
                        MAYAN_PROPHESY_YEAR - game.getTurn().getYear())
                : new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                NameCache.getRumourNothingKey(random),
                player);
    }


    // Interface Named

    /**
     * {@inheritDoc}
     */
    @Override
    public String getNameKey() {
        return Messages.nameKey("model.lostCityRumour");
    }


    // Interface TileItem

    /**
     * {@inheritDoc}
     */
    @Override
    public final int getZIndex() {
        return Tile.RUMOUR_ZINDEX;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTileTypeAllowed(TileType tileType) {
        return !tileType.isWater();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int applyBonus(GoodsType goodsType, UnitType unitType, int potential) {
        // Just return the given potential, since lost cities do not
        // provide any production bonuses.  FIXME: maybe we should
        // return zero, since lost cities actually prevent production?
        return potential;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canProduce(GoodsType goodsType, UnitType unitType) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Stream<Modifier> getProductionModifiers(GoodsType goodsType,
                                                   UnitType unitType) {
        return Stream.<Modifier>empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isNatural() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isComplete() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Layer getLayer() {
        return Layer.RUMOURS;
    }


    // Override FreeColGameObject

    /**
     * {@inheritDoc}
     */
    @Override
    public IntegrityType checkIntegrity(boolean fix, LogBuilder lb) {
        IntegrityType result = super.checkIntegrity(fix, lb);
        if (type == FindType.NOTHING_MORE_TO_EXPLORE) {
            lb.add("\n  Rumour with null type: ", getId());
            result = result.fail();
        }
        return result;
    }


    // Override FreeColObject

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends FreeColObject> boolean copyIn(T other) {
        CaveExploration o = copyInCast(other, CaveExploration.class);
        if (o == null || !super.copyIn(o)) return false;
        this.type = o.getType();
        this.name = o.getName();
        return true;
    }


    // Serialization

    private static final String NAME_TAG = "name";
    private static final String TILE_TAG = "tile";
    private static final String TYPE_TAG = "type";


    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(FreeColXMLWriter xw) throws XMLStreamException {
        super.writeAttributes(xw);

        xw.writeAttribute(TILE_TAG, getTile());

        if (type != null) {
            xw.writeAttribute(TYPE_TAG, getType());
        }

        if (name != null) {
            xw.writeAttribute(NAME_TAG, name);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(FreeColXMLReader xr) throws XMLStreamException {
        super.readAttributes(xr);

        tile = xr.findFreeColGameObject(getGame(), TILE_TAG,
                Tile.class, (Tile)null, true);

        type = xr.getAttribute(TYPE_TAG, FindType.class, (FindType)null);

        name = xr.getAttribute(NAME_TAG, (String)null);
    }

    /**
     * {@inheritDoc}
     */
    public String getXMLTagName() { return TAG; }
}

