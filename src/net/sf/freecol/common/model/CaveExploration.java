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

import static net.sf.freecol.common.util.StringUtils.*;


/**
 * Represents a cave to be explored.
 */
public  class CaveExploration extends TileItem {
    private static final Logger logger = Logger.getLogger(CaveExploration.class.getName());

    public static final String TAG = "CaveExploration";

    // The bogus end of the world year.
    private static final int MAYAN_PROPHESY_YEAR = 2012;

    public static enum CaveType {
        NOTHING_MORE_TO_EXPLORE,
        TRAP,
        LETHAL_TRAP,
        NOTHING,
        LEARN,
        COLONIST,
        RESOURCES,
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
     * The type of the rumour.  A CaveType, or null if the type has
     * not yet been determined.
     */
    private CaveType type = null;

    /**
     * The name of this rumour, or null, if it has none.  Rumours such
     * as the Seven Cities of Gold and Fountains of Youth may have
     * individual names.
     */
    private String name = null;

    private int nFloors;

    private int currentFloor;

    /**
     * Creates a new {@code LostCityRumour} instance.
     * TODO: get random number of floors
     * @param game The enclosing {@code Game}.
     * @param tile The {@code Tile} where the LCR is.
     */
    public CaveExploration(Game game, Tile tile) {
        super(game, tile);
        nFloors = 5;
        currentFloor = 0;
    }

    /**
     * Creates a new {@code LostCityRumour} instance.
     *
     * @param game The enclosing {@code Game}.
     * @param tile The {@code Tile} where the LCR is.
     * @param type The type of rumour.
     * @param name The name of the rumour.
     */
    public CaveExploration(Game game, Tile tile, CaveType type, String name) {
        super(game, tile);

        this.type = type;
        this.name = name;
    }

    /**
     * Creates a new {@code CaveExploration} instance.
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
     * @return The {@code CaveType}.
     */
    public final CaveType getType() {
        return type;
    }

    /**
     * Get the name of this rumour.
     *
     * @return The name.
     */
    public final String getName() {
        return name;
    }

    // Interface Named

    /**
     * {@inheritDoc}
     */
    @Override
    public String getNameKey() {
        return Messages.nameKey("model.caveExploration");
    }

    /**
     * Chooses a type of Lost City Rumour.  The type of rumour depends
     * on the exploring unit, as well as player settings.
     *
     * The scouting outcome is based on three factors: good/bad percent
     * rumour difficulty option, expert scout or not, DeSoto or not.
     *
     * FIXME: Make CaveType a FreeColSpecObjectType and move all the
     * TODO: Fix probabilities
     * magic numbers in here to the specification.
     *
     * @param unit The {@code Unit} exploring (optional).
     * @param random A random number source.
     * @return The type of rumour.
     */
    public CaveType chooseType(Unit unit, Random random) {
        if(currentFloor >= nFloors) {
            return CaveType.NOTHING_MORE_TO_EXPLORE;
        } else {
            final Specification spec = getSpecification();
            final Tile tile = getTile();
            // Booleans for various rumour types that are conditionally available.
            //
            // Only colonial players get FoYs as immigration ends at independence
            final boolean allowFoY = unit != null
                    && unit.getOwner().getPlayerType() == Player.PlayerType.COLONIAL;
            // Certain units can learn a skill at LCRs
            //TODO: connect this
            final boolean allowLearn = unit != null
                    && !spec.getUnitChanges(UnitChangeType.CAVE_EXPLORATION,
                    unit.getType()).isEmpty();
            // Expert units never vanish
            final boolean isExpert = unit != null
                    && unit.hasAbility(Ability.EXPERT_SCOUT);

            // Work out the bad and good chances.  The base values are
            // difficulty options.  Neutral results take up any remainder.
            //TODO: CONNECT THIS
            int percentBad = spec.getInteger(GameOptions.BAD_CAVE);
            int percentGood = spec.getInteger(GameOptions.GOOD_CAVE);
            if (unit != null) {
                if (unit.getOwner().hasAbility(Ability.CAVE_ALWAYS_POSITIVE)) {
                    // DeSoto forces all good results.
                    percentBad = 0;
                    percentGood = 100;
                } else {
                    // Otherwise apply any unit exploration bonus
                    float mod = unit.apply(1.0f, getGame().getTurn(),
                            Modifier.EXPLORE_CAVE);
                    percentBad = Math.round(percentBad / mod);
                    percentGood = Math.round(percentGood * mod);
                }
            }
            int percentNeutral = Math.max(0, 100 - percentBad - percentGood);

            // Add all possible events to a RandomChoice List
            List<RandomChoice<CaveType>> c = new ArrayList<>();

            if (percentGood > 0) { // The GOOD
                if (allowLearn) {
                    c.add(new RandomChoice<>(CaveType.LEARN,
                            30 * percentGood));
                    c.add(new RandomChoice<>(CaveType.COLONIST,
                            20 * percentGood));
                } else {
                    c.add(new RandomChoice<>(CaveType.COLONIST,
                            30 * percentGood));
                }
                c.add(new RandomChoice<>(CaveType.TREASURE,
                        20 * percentGood));
                c.add(new RandomChoice<>(CaveType.RESOURCES,
                        30 * percentGood));
            }

            if (percentBad > 0) { // The BAD
                List<RandomChoice<CaveType>> cbad = new ArrayList<>();
                if (isExpert) {
                    cbad.add(new RandomChoice<>(CaveType.TRAP,
                            25 * percentBad));
                    cbad.add(new RandomChoice<>(CaveType.LETHAL_TRAP,
                            5 * percentBad));
                } else {
                    cbad.add(new RandomChoice<>(CaveType.TRAP,
                            50 * percentBad));
                    cbad.add(new RandomChoice<>(CaveType.LETHAL_TRAP,
                            20 * percentBad));
                }
                RandomChoice.normalize(cbad, 100);
                c.addAll(cbad);
            }

            if (percentNeutral > 0) { // The NEUTRAL
                c.add(new RandomChoice<>(CaveType.NOTHING,
                        100 * percentNeutral));
            }
            currentFloor++;
            return RandomChoice.getWeightedRandom(logger, "Choose cave", c, random);
        }
    }

    public ModelMessage getNothingMessage(Player player, Random random) {
        return new ModelMessage(ModelMessage.MessageType.CAVE_EXPLORATION,
                NameCache.getCaveNothingKey(random),
                player);
    }

    public boolean isEmpty(){
        return currentFloor >= nFloors;
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
        return Layer.CAVES;
    }


    // Override FreeColGameObject

    /**
     * {@inheritDoc}
     */
    @Override
    public IntegrityType checkIntegrity(boolean fix, LogBuilder lb) {
        IntegrityType result = super.checkIntegrity(fix, lb);
        if (type == CaveType.NOTHING_MORE_TO_EXPLORE) {
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

        type = xr.getAttribute(TYPE_TAG, CaveType.class, (CaveType)null);

        name = xr.getAttribute(NAME_TAG, (String)null);
    }

    /**
     * {@inheritDoc}
     */
    public String getXMLTagName() { return TAG; }
}