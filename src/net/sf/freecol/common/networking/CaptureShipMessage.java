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

package net.sf.freecol.common.networking;

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * The message sent when looting cargo.
 */
public class CaptureShipMessage extends ObjectMessage {

    public static final String TAG = "lootCargo";
    private static final String LOSER_TAG = "loser";
    private static final String WINNER_TAG = "winner";


    /**
     *
     * @param winner
     * @param loserId
     */
    public CaptureShipMessage(Unit winner, String loserId) {
        super(TAG, WINNER_TAG, winner.getId(), LOSER_TAG, loserId);
    }

    /**
     * Create a new {@code LootCargoMessage} from a stream.
     *
     * @param game The {@code Game} this message belongs to.
     * @param xr The {@code FreeColXMLReader} to read from.
     * @exception XMLStreamException if there is a problem reading the stream.
     */
    public CaptureShipMessage(Game game, FreeColXMLReader xr)
            throws XMLStreamException {
        super(TAG, xr, WINNER_TAG, LOSER_TAG);
    }


    /**
     * Accessor for the winning unit.
     *
     * @param game The {@code Game} to look for the unit in.
     * @return The winner unit.
     */
    private Unit getWinner(Game game) {
        return game.getFreeColGameObject(getStringAttribute(WINNER_TAG),
                Unit.class);
    }

    /**
     * Accessor for the losing unit identifier.
     *
     * @return The loser unit object Identifier.
     */
    private String getLoserId() {
        return getStringAttribute(LOSER_TAG);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessagePriority getPriority() {
        return Message.MessagePriority.LATE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void aiHandler(FreeColServer freeColServer, AIPlayer aiPlayer) {
        final Game game = freeColServer.getGame();
        final Unit winner = getWinner(game);
        final String loserId = getLoserId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clientHandler(FreeColClient freeColClient) {
        final Game game = freeColClient.getGame();
        final Unit unit = getWinner(game);
        final String loserId = getLoserId();

        if (unit == null) return;

        igc(freeColClient).captureShipHandler(unit, loserId);
        clientGeneric(freeColClient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChangeSet serverHandler(FreeColServer freeColServer,
                                   ServerPlayer serverPlayer) {
        final Game game = freeColServer.getGame();

        Unit winner;
        try {
            winner = getWinner(game);
        } catch (Exception e) {
            return serverPlayer.clientError(e.getMessage());
        }
        // Do not check the defender identifier, as it might have
        // sunk.  It is enough that the attacker knows it.  Similarly
        // the server is better placed to check the goods validity.

        // Try to loot.
        return igc(freeColServer)
                .captureShip(serverPlayer, winner, getLoserId());
    }
}
