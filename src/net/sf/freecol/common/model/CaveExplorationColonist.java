package net.sf.freecol.common.model;

import java.util.Random;

public class CaveExplorationColonist extends AbstractCaveExploration{
    public CaveExplorationColonist(Game game, Tile tile) {
        super(game, tile);
    }

    public CaveExplorationColonist(Game game, Tile tile, FindType type, String name) {
        super(game, tile, type, name);
    }

    public CaveExplorationColonist(Game game, String id) {
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
