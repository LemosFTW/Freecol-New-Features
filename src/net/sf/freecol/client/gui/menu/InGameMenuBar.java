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

package net.sf.freecol.client.gui.menu;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.event.MouseMotionListener;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.util.logging.Logger;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.FontLibrary;
import net.sf.freecol.client.gui.action.*;
import net.sf.freecol.client.gui.action.DisplayTileTextAction.DisplayText;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.TileImprovementType;


/**
 * This is the menu bar used in-game.
 *
 * That is, the menu bar that is displayed on the top left corner of the
 * {@code Canvas}.
 *
 * @see MapEditorMenuBar
 */
public class InGameMenuBar extends FreeColMenuBar {

    @SuppressWarnings("unused")
    private static final Logger logger = Logger.getLogger(InGameMenuBar.class.getName());

    /**
     * Creates a new {@code FreeColMenuBar}. This menu bar will include
     * all of the submenus and items.
     *
     * @param freeColClient The main controller.
     * @param listener An optional mouse motion listener.
     */
    public InGameMenuBar(FreeColClient freeColClient,
                         MouseMotionListener listener) {
        super(freeColClient, listener);
        
        reset();
    }


    /**
     * Resets this menu bar.
     */
    @Override
    public final void reset() {
        removeAll();

        buildGameMenu();
        buildViewMenu();
        buildOrdersMenu();
        buildReportMenu();
        buildColopediaMenu();
        buildGamePrefMenu();

        if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.MENUS)) {
            add(new DebugMenu(this.freeColClient));
        }

        update();
    }

    private void buildGamePrefMenu(){
        JMenu menu = Utility.localizedMenu("menuBar.preferences");
        menu.setOpaque(false);
        //menu.setMnemonic(KeyEvent.VK_P);

        menu.add(getMenuItem(ShowGameOptionsAction.id));
        menu.add(getCheckBoxMenuItem(AutoGetShip.id));
        //menu.add(getCheckBoxMenuItem(MapControlsAction.id));

        add(menu);

    }

    private void buildGameMenu() {
        // --> Game
        JMenu menu = Utility.localizedMenu("menuBar.game");
        menu.setOpaque(false);
        menu.setMnemonic(KeyEvent.VK_G);

        menu.add(getMenuItem(NewAction.id));
        menu.add(getMenuItem(OpenAction.id));
        menu.add(getMenuItem(SaveAction.id));

        menu.addSeparator();

        menu.add(getMenuItem(PreferencesAction.id));
        menu.add(getMenuItem(ReconnectAction.id));

        menu.addSeparator();

        menu.add(getMenuItem(ChatAction.id));
        menu.add(getMenuItem(DeclareIndependenceAction.id));
        menu.add(getMenuItem(EndTurnAction.id));

        menu.addSeparator();

        menu.add(getMenuItem(ShowMainAction.id));
        menu.add(getMenuItem(ReportHighScoresAction.id));
        menu.add(getMenuItem(RetireAction.id));
        menu.add(getMenuItem(SaveAndQuitAction.id));
        menu.add(getMenuItem(QuitAction.id));
        
        add(menu);
    }

    private void buildViewMenu() {
        // --> View
        JMenu menu = Utility.localizedMenu("menuBar.view");
        menu.setOpaque(false);
        menu.setMnemonic(KeyEvent.VK_V);

        menu.add(getCheckBoxMenuItem(MapControlsAction.id));
        menu.add(getCheckBoxMenuItem(DisplayGridAction.id));
        menu.add(getCheckBoxMenuItem(DisplayBordersAction.id));
        menu.add(getCheckBoxMenuItem(DisplayFogOfWarAction.id));
        menu.add(getMenuItem(ToggleViewModeAction.id));
        menu.add(getCheckBoxMenuItem(ChangeWindowedModeAction.id));

        menu.addSeparator();
        ButtonGroup group = new ButtonGroup();
        for (DisplayText type : DisplayText.values()) {
            menu.add(getRadioButtonMenuItem(DisplayTileTextAction.id
                    + type.getKey(), group));
        }

        menu.addSeparator();
        menu.add(getMenuItem(ZoomInAction.id));
        menu.add(getMenuItem(ZoomOutAction.id));
        menu.add(getMenuItem(CenterAction.id));
        menu.add(getMenuItem(TilePopupAction.id));

        menu.addSeparator();

        menu.add(getMenuItem(EuropeAction.id));
        menu.add(getMenuItem(TradeRouteAction.id));
        menu.add(getMenuItem(FindSettlementAction.id));

        add(menu);
    }

    private void buildOrdersMenu() {
        final Specification spec = this.freeColClient.getGame()
            .getSpecification();
        // --> Orders
        JMenu menu = Utility.localizedMenu("menuBar.orders");
        menu.setOpaque(false);
        menu.setMnemonic(KeyEvent.VK_O);

        if (spec.hasAbility(Ability.HITPOINTS_COMBAT_MODEL)) {
            menu.add(getMenuItem(AttackRangedAction.id));
        }
        
        menu.add(getMenuItem(SentryAction.id));
        menu.add(getMenuItem(FortifyAction.id));

        menu.addSeparator();

        menu.add(getMenuItem(GotoAction.id));
        menu.add(getMenuItem(GotoTileAction.id));
        menu.add(getMenuItem(ExecuteGotoOrdersAction.id));
        menu.add(getMenuItem(AssignTradeRouteAction.id));

        menu.addSeparator();

        menu.add(getMenuItem(BuildColonyAction.id));
        // Insert all Improvements here:
        for (TileImprovementType type : spec.getTileImprovementTypeList()) {
            if (!type.isNatural()) {
                menu.add(getMenuItem(type.getSuffix() + "Action"));
            }
        }
        menu.addSeparator();

        menu.add(getMenuItem(LoadAction.id));
        menu.add(getMenuItem(UnloadAction.id));

        menu.addSeparator();

        menu.add(getMenuItem(WaitAction.id));
        menu.add(getMenuItem(SkipUnitAction.id));
        menu.add(getMenuItem(ChangeAction.id));

        menu.addSeparator();

        menu.add(getMenuItem(ClearOrdersAction.id));
        menu.add(getMenuItem(RenameAction.id));
        menu.add(getMenuItem(DisbandUnitAction.id));

        add(menu);
    }

    private void buildReportMenu() {
        // --> Report

        JMenu menu = Utility.localizedMenu("menuBar.report");
        menu.setOpaque(false);
        menu.setMnemonic(KeyEvent.VK_R);

        menu.add(getMenuItem(ReportReligionAction.id));
        menu.add(getMenuItem(ReportLabourAction.id));
        menu.add(getMenuItem(ReportColonyAction.id));
        menu.add(getMenuItem(ReportForeignAction.id));
        menu.add(getMenuItem(ReportIndianAction.id));
        menu.add(getMenuItem(ReportContinentalCongressAction.id));
        menu.add(getMenuItem(ReportMilitaryAction.id));
        menu.add(getMenuItem(ReportNavalAction.id));
        menu.add(getMenuItem(ReportTradeAction.id));
        menu.add(getMenuItem(ReportTurnAction.id));
        menu.add(getMenuItem(ReportRequirementsAction.id));
        menu.add(getMenuItem(ReportCargoAction.id));
        menu.add(getMenuItem(ReportExplorationAction.id));
        menu.add(getMenuItem(ReportHistoryAction.id));
        menu.add(getMenuItem(ReportProductionAction.id));
        menu.add(getMenuItem(ReportEducationAction.id));
        menu.add(getMenuItem(ShowDifficultyAction.id));
        menu.add(getMenuItem(ShowGameOptionsAction.id));
        menu.add(getMenuItem(ShowMapGeneratorOptionsAction.id));

        add(menu);
    }


    // Override JComponent

    /**
     * {@inheritDoc}
     */
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (this.freeColClient == null) return; // Fail fast
        final Player player = this.freeColClient.getMyPlayer();
        if (player == null) return;

        final String text = Messages.message(StringTemplate
            .template("menuBar.statusLine")
            .addAmount("%gold%", player.getGold())
            .addAmount("%tax%", player.getTax())
            .addAmount("%score%", player.getScore())
            .addStringTemplate("%year%", this.freeColClient.getGame()
            .getTurn().getLabel())).replace("|", "✧");
        
        Graphics2D g2d = (Graphics2D)g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                             RenderingHints.VALUE_RENDER_QUALITY);

        final Font font = FontLibrary.getMainFont();
        g2d.setFont(font);
        
        final FontMetrics fm = g2d.getFontMetrics();
        final Rectangle2D d  = fm.getStringBounds(text, g2d);
        final int textWidth = (int) d.getWidth();
        final int textHeight = (int) d.getHeight();;
        
        final int rightSidePaddingInPx = 10;
        final int centerHeight = getHeight() - getInsets().bottom;
        final int x = getWidth() - rightSidePaddingInPx - textWidth - getInsets().right;
        final int y = (centerHeight - textHeight) / 2 + fm.getAscent();
        
        Utility.drawGoldenText(text, g2d, font, x, y);
    }
}
