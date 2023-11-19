package net.sf.freecol.common.model;

import java.util.Random;

public class CaveExplorationTreasure extends AbstractCaveExploration{
    public CaveExplorationTreasure(Game game, Tile tile) {
        super(game, tile);
    }

    public CaveExplorationTreasure(Game game, Tile tile, FindType type, String name) {
        super(game, tile, type, name);
    }

    public CaveExplorationTreasure(Game game, String id) {
        super(game, id);
    }

    @Override
    public String getDescriptionKey() {
        return null;
    }

    @Override
    public ModelMessage getNothingMessage(Player player, boolean mounds, Random random) {
        return null;
    }
}
