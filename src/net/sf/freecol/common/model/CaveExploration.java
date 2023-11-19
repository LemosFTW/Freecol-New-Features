package net.sf.freecol.common.model;

import java.util.Random;

public interface CaveExploration {

    public String getDescriptionKey();

    public AbstractCaveExploration.FindType getType();

    public void setType(final AbstractCaveExploration.FindType newType);

    public String getName();

    public AbstractCaveExploration.FindType chooseType(Unit unit, Random random);

    public ModelMessage getNothingMessage(Player player, boolean mounds, Random random);

    public String getXMLTagName();
}
