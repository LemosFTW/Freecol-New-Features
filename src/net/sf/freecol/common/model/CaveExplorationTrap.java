package net.sf.freecol.common.model;

import java.util.Random;

public class CaveExplorationTrap extends AbstractCaveExploration{

    public CaveExplorationTrap(Game game, Tile tile) {
        super(game, tile);
    }

    public CaveExplorationTrap(Game game, Tile tile, FindType type, String name) {
        super(game, tile, type, name);
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
