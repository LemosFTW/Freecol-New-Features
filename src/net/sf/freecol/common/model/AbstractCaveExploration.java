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
public abstract class AbstractCaveExploration extends TileItem implements  CaveExploration{

    public static final String TAG = "CaveExplorationExploration";

    // The bogus end of the world year.
    private static final int MAYAN_PROPHESY_YEAR = 2012;

    /** Probably not needed we now use different classes */
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
    public AbstractCaveExploration(Game game, Tile tile) {
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
    public AbstractCaveExploration(Game game, Tile tile, FindType type, String name) {
        super(game, tile);

        this.type = type;
        this.name = name;
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
        AbstractCaveExploration o = copyInCast(other, AbstractCaveExploration.class);
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

